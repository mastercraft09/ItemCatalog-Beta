package com.mastercraft.itemcatalog.gui;

import com.mastercraft.itemcatalog.ItemCatalogPlugin;
import com.mastercraft.itemcatalog.category.Category;
import com.mastercraft.itemcatalog.model.CatalogItem;
import com.mastercraft.itemcatalog.model.ProviderType;
import com.mastercraft.itemcatalog.util.ColorUtil;
import com.mastercraft.itemcatalog.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds every inventory the catalog shows and keeps a {@link PlayerSession}
 * per online player so pagination/filters/back-navigation all work.
 */
public class CatalogGUIManager {

    private final ItemCatalogPlugin plugin;
    private final Map<UUID, PlayerSession> sessions = new HashMap<>();

    public CatalogGUIManager(ItemCatalogPlugin plugin) {
        this.plugin = plugin;
    }

    public PlayerSession getSession(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), id -> new PlayerSession());
    }

    public void clearSession(Player player) {
        sessions.remove(player.getUniqueId());
    }

    // ================================================================
    //  MAIN MENU — top-level categories
    // ================================================================

    public void openMain(Player player) {
        openMain(player, 0);
    }

    public void openMain(Player player, int page) {
        PlayerSession session = getSession(player);
        session.setView(PlayerSession.View.MAIN);
        session.setCategoryId(null);
        session.setSubCategoryId(null);
        session.setPage(page);

        int rows = plugin.getConfigManager().getGuiRows();
        String title = ColorUtil.color(plugin.getConfigManager().getGuiString("gui.main-title", "&8&lItem Catalog"));

        List<Category> categories = visibleTopCategories(player);
        List<Integer> contentSlots = plugin.getConfigManager().getCategorySlots();

        int perPage = contentSlots.isEmpty() ? 0 : contentSlots.size();
        int totalPages = perPage == 0 ? 1 : Math.max(1, (int) Math.ceil(categories.size() / (double) perPage));
        int actualPage = Math.max(0, Math.min(session.getPage(), totalPages - 1));
        session.setPage(actualPage);

        CatalogInventoryHolder holder = new CatalogInventoryHolder();
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.setInventory(inv);

        int from = actualPage * perPage;
        int to = Math.min(categories.size(), from + Math.max(perPage, 0));
        List<Category> pageCategories = from < to ? categories.subList(from, to) : List.of();

        if (actualPage == 0) {
            placeCategoriesWithSlots(inv, holder, pageCategories, contentSlots, CatalogInventoryHolder.SlotAction.OPEN_CATEGORY);
        } else {
            int idx = 0;
            for (Category category : pageCategories) {
                if (idx >= contentSlots.size()) break;
                int slot = contentSlots.get(idx);
                inv.setItem(slot, categoryIcon(category));
                holder.bind(slot, CatalogInventoryHolder.SlotAction.OPEN_CATEGORY, category.getId());
                idx++;
            }
        }

        fillEmpty(inv, holder, 0, (rows - 1) * 9);
        boolean prev = actualPage > 0;
        boolean next = actualPage < totalPages - 1;
        addNavRow(inv, holder, rows, prev, next, true, false, session);

        player.openInventory(inv);
    }

    // ================================================================
    //  SUBCATEGORY MENU
    // ================================================================

    public void openSubCategories(Player player, String categoryId) {
        openSubCategories(player, categoryId, 0);
    }

    public void openSubCategories(Player player, String categoryId, int page) {
        Category category = plugin.getCategoryManager().getCategory(categoryId);
        if (category == null || !category.hasChildren()) {
            openItems(player, categoryId, null, 0);
            return;
        }

        PlayerSession session = getSession(player);
        session.setView(PlayerSession.View.SUBCATEGORIES);
        session.setCategoryId(categoryId);
        session.setSubCategoryId(null);
        session.setPage(page);

        int rows = plugin.getConfigManager().getGuiRows();
        String title = ColorUtil.color(plugin.getConfigManager().getGuiString("gui.category-title", "&8&l%category%")
                .replace("%category%", ColorUtil.strip(category.getDisplayName())));

        List<Integer> contentSlots = plugin.getConfigManager().getCategorySlots();
        boolean allButtonEnabled = plugin.getConfigManager().getConfig().getBoolean("gui.all-category-button.enabled", true);

        List<Category> subs = category.getChildren().stream()
                .filter(Category::isEnabled)
                .filter(sub -> hasCategoryPermission(player, sub))
                .collect(Collectors.toList());

        // The "All <category>" shortcut (when enabled) always occupies the
        // first configured content slot and only ever appears on page 0, so
        // page 0 has one fewer slot available for actual subcategories than
        // later pages.
        int firstPageCapacity = Math.max(0, contentSlots.size() - (allButtonEnabled ? 1 : 0));
        int laterPageCapacity = contentSlots.size();
        int totalPages;
        if (subs.size() <= firstPageCapacity) {
            totalPages = 1;
        } else {
            totalPages = 1 + (laterPageCapacity == 0 ? 0
                    : (int) Math.ceil((subs.size() - firstPageCapacity) / (double) laterPageCapacity));
        }
        int actualPage = Math.max(0, Math.min(page, totalPages - 1));
        session.setPage(actualPage);

        CatalogInventoryHolder holder = new CatalogInventoryHolder();
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.setInventory(inv);
        int invSize = inv.getSize();

        if (actualPage == 0) {
            boolean[] occupied = new boolean[invSize];

            if (allButtonEnabled) {
                int allSlot = contentSlots.isEmpty() ? 0 : contentSlots.get(0);
                Material allMaterial = safeMaterial(plugin.getConfigManager().getAllCategoryButtonMaterial());
                String allName = plugin.getConfigManager().getAllCategoryButtonName()
                        .replace("%category%", ColorUtil.strip(category.getDisplayName()));
                List<String> allLore = plugin.getConfigManager().getAllCategoryButtonLore();
                ItemBuilder allBuilder = new ItemBuilder(allMaterial).name(allName);
                if (!allLore.isEmpty()) allBuilder.lore(allLore);
                inv.setItem(allSlot, allBuilder.build());
                holder.bind(allSlot, CatalogInventoryHolder.SlotAction.OPEN_SUBCATEGORY, "__all__");
                occupied[allSlot] = true;
            }

            List<Category> firstPageSubs = subs.size() <= firstPageCapacity ? subs : subs.subList(0, firstPageCapacity);
            List<Category> pinned = firstPageSubs.stream().filter(Category::hasExplicitSlot).collect(Collectors.toList());
            List<Category> unpinned = firstPageSubs.stream().filter(sub -> !sub.hasExplicitSlot()).collect(Collectors.toList());

            for (Category sub : pinned) {
                int slot = sub.getSlot();
                if (slot >= 0 && slot < invSize && !occupied[slot]) {
                    inv.setItem(slot, categoryIcon(sub));
                    holder.bind(slot, CatalogInventoryHolder.SlotAction.OPEN_SUBCATEGORY, sub.getId());
                    occupied[slot] = true;
                } else {
                    unpinned.add(sub);
                }
            }

            int idx = 0;
            for (Category sub : unpinned) {
                while (idx < contentSlots.size() && occupied[contentSlots.get(idx)]) idx++;
                if (idx >= contentSlots.size()) break;
                int slot = contentSlots.get(idx);
                inv.setItem(slot, categoryIcon(sub));
                holder.bind(slot, CatalogInventoryHolder.SlotAction.OPEN_SUBCATEGORY, sub.getId());
                occupied[slot] = true;
                idx++;
            }
        } else {
            int from = firstPageCapacity + (actualPage - 1) * laterPageCapacity;
            int to = Math.min(subs.size(), from + laterPageCapacity);
            List<Category> pageSubs = from < to ? subs.subList(from, to) : List.of();

            int idx = 0;
            for (Category sub : pageSubs) {
                if (idx >= contentSlots.size()) break;
                int slot = contentSlots.get(idx);
                inv.setItem(slot, categoryIcon(sub));
                holder.bind(slot, CatalogInventoryHolder.SlotAction.OPEN_SUBCATEGORY, sub.getId());
                idx++;
            }
        }

        fillEmpty(inv, holder, 0, (rows - 1) * 9);
        boolean prev = actualPage > 0;
        boolean next = actualPage < totalPages - 1;
        addNavRow(inv, holder, rows, prev, next, true, true, session);

        player.openInventory(inv);
    }

    // ================================================================
    //  ITEM LIST (category/subcategory browsing, paginated)
    // ================================================================

    public void openItems(Player player, String categoryId, String subCategoryId, int page) {
        PlayerSession session = getSession(player);
        session.setView(PlayerSession.View.ITEMS);
        session.setCategoryId(categoryId);
        session.setSubCategoryId(subCategoryId);
        session.setPage(page);
        session.setSearchQuery(null);

        String key = subCategoryId == null ? categoryId : categoryId + "." + subCategoryId;
        List<CatalogItem> items = applyFilters(plugin.getItemRegistry().getByCategory(key), session, player);

        Category category = plugin.getCategoryManager().getCategory(key);
        String categoryName = category != null ? category.getDisplayName() : key;

        String title = ColorUtil.color(plugin.getConfigManager().getGuiString("gui.category-title", "&8&l%category%")
                .replace("%category%", ColorUtil.strip(categoryName)));

        // If this category is a dynamic MMOItems category (its id is the
        // slugged MMOItems Type id), check mmoitems.yml for a per-category
        // slot/fill-direction override before falling back to the global
        // gui.item-slots settings.
        List<Integer> contentSlots = plugin.getConfigManager().getItemSlots();
        if (category != null && category.isDynamic()) {
            var override = plugin.getConfigManager().getMmoItemsCategorySlotOverride(categoryId);
            if (override.isPresent()) {
                contentSlots = override.get().resolveSlots(plugin.getConfigManager().getGuiRows() * 9);
            }
        }

        renderItemGrid(player, session, items, title, true, contentSlots);
    }

    // ================================================================
    //  SEARCH RESULTS
    // ================================================================

    public void openSearch(Player player, String query, int page) {
        PlayerSession session = getSession(player);
        session.setView(PlayerSession.View.SEARCH_RESULTS);
        session.setSearchQuery(query);
        session.setPage(page);

        List<CatalogItem> results = applyFilters(searchByNameOrCategory(query), session, player);

        String title = ColorUtil.color(plugin.getConfigManager().getGuiString("gui.search-title", "&8&lSearch results: &f%query%")
                .replace("%query%", query));

        renderItemGrid(player, session, results, title, false, plugin.getConfigManager().getSearchItemSlots());
    }

    /**
     * Combines the normal item-name text search with a category lookup: if
     * the query matches a category's id, full id, or display name (an
     * internal catalog category OR e.g. an MMOItems Type's own id/name like
     * "DAGGER"), OR matches one of its configured search.yml "search-alias"
     * words (e.g. "armatura" -> "armor"), every item in that category is
     * included too, alongside any regular name matches. Deduplicated by
     * catalog id, category matches first. Categories that are disabled/hidden
     * are never matched this way, so a hidden category's items can't leak
     * into search results through its name/alias either.
     */
    private List<CatalogItem> searchByNameOrCategory(String query) {
        Map<String, CatalogItem> merged = new LinkedHashMap<>();

        Category matched = plugin.getCategoryManager().findByNameOrId(query);
        if (matched == null) {
            String aliasCategoryId = plugin.getConfigManager().resolveSearchAlias(query);
            if (aliasCategoryId != null) {
                matched = plugin.getCategoryManager().getCategory(aliasCategoryId);
            }
        }
        if (matched != null && isCategoryFullyEnabled(matched)) {
            for (CatalogItem item : plugin.getItemRegistry().getByCategory(matched.getFullId())) {
                merged.put(item.getCatalogId(), item);
            }
        }

        boolean caseInsensitive = plugin.getConfigManager().isSearchCaseInsensitive();
        for (CatalogItem item : plugin.getItemRegistry().search(query, caseInsensitive)) {
            merged.putIfAbsent(item.getCatalogId(), item);
        }

        return new ArrayList<>(merged.values());
    }

    /** True unless this category, or (for a subcategory) its parent, is disabled. */
    private boolean isCategoryFullyEnabled(Category category) {
        if (category == null) return false;
        if (!category.isEnabled()) return false;
        Category parent = category.getParent();
        return parent == null || parent.isEnabled();
    }

    // ================================================================
    //  shared item-grid renderer
    // ================================================================

    private void renderItemGrid(Player player, PlayerSession session, List<CatalogItem> items, String title,
                                 boolean showBackToSubcats, List<Integer> contentSlots) {
        int rows = plugin.getConfigManager().getGuiRows();
        int perPage = contentSlots.isEmpty() ? 0 : Math.min(plugin.getConfigManager().getItemsPerPage(), contentSlots.size());
        int page = session.getPage();
        int totalPages = perPage == 0 ? 1 : Math.max(1, (int) Math.ceil(items.size() / (double) perPage));
        page = Math.max(0, Math.min(page, totalPages - 1));
        session.setPage(page);

        CatalogInventoryHolder holder = new CatalogInventoryHolder();
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.setInventory(inv);

        int from = page * perPage;
        int to = Math.min(items.size(), from + perPage);
        List<CatalogItem> pageItems = from < to ? items.subList(from, to) : List.of();

        if (page == 0) {
            placeItemsWithPins(inv, holder, pageItems, contentSlots);
        } else {
            int idx = 0;
            for (CatalogItem item : pageItems) {
                if (idx >= contentSlots.size()) break;
                int slot = contentSlots.get(idx);
                inv.setItem(slot, buildDisplayItem(item));
                holder.bind(slot, CatalogInventoryHolder.SlotAction.VIEW_ITEM, item.getCatalogId());
                idx++;
            }
        }

        fillEmpty(inv, holder, 0, (rows - 1) * 9);
        boolean prev = page > 0;
        boolean next = page < totalPages - 1;
        addNavRow(inv, holder, rows, prev, next, true, showBackToSubcats, session);

        player.openInventory(inv);
    }

    /**
     * Places items into a single page's content slots, honouring per-item
     * slot pins from items.yml (`slot: <n>`) first, then filling the
     * configured gui.item-slots pattern in order with the rest of the
     * (already ordered) list.
     */
    private void placeItemsWithPins(Inventory inv, CatalogInventoryHolder holder, List<CatalogItem> pageItems, List<Integer> availableSlots) {
        int invSize = inv.getSize();
        boolean[] occupied = new boolean[invSize];
        List<CatalogItem> unpinned = new ArrayList<>();

        for (CatalogItem item : pageItems) {
            int pin = plugin.getConfigManager().getItemPinnedSlot(item.getCatalogId());
            if (pin >= 0 && pin < invSize && !occupied[pin]) {
                inv.setItem(pin, buildDisplayItem(item));
                holder.bind(pin, CatalogInventoryHolder.SlotAction.VIEW_ITEM, item.getCatalogId());
                occupied[pin] = true;
            } else {
                unpinned.add(item);
            }
        }

        int idx = 0;
        for (CatalogItem item : unpinned) {
            while (idx < availableSlots.size() && occupied[availableSlots.get(idx)]) idx++;
            if (idx >= availableSlots.size()) break;
            int slot = availableSlots.get(idx);
            inv.setItem(slot, buildDisplayItem(item));
            holder.bind(slot, CatalogInventoryHolder.SlotAction.VIEW_ITEM, item.getCatalogId());
            occupied[slot] = true;
            idx++;
        }
    }

    /**
     * Places a list of categories into the GUI, honouring each category's
     * explicit `slot` (from categories.yml) first, then filling the
     * configured gui.category-slots pattern in order (by the category's
     * `order` field) with everything left over.
     */
    private void placeCategoriesWithSlots(Inventory inv, CatalogInventoryHolder holder, List<Category> categories,
                                           List<Integer> availableSlots, CatalogInventoryHolder.SlotAction action) {
        int invSize = inv.getSize();
        boolean[] occupied = new boolean[invSize];
        List<Category> unpinned = new ArrayList<>();

        for (Category category : categories) {
            if (category.hasExplicitSlot() && category.getSlot() < invSize && !occupied[category.getSlot()]) {
                int slot = category.getSlot();
                inv.setItem(slot, categoryIcon(category));
                holder.bind(slot, action, category.getId());
                occupied[slot] = true;
            } else {
                unpinned.add(category);
            }
        }

        int idx = 0;
        for (Category category : unpinned) {
            while (idx < availableSlots.size() && occupied[availableSlots.get(idx)]) idx++;
            if (idx >= availableSlots.size()) break;
            int slot = availableSlots.get(idx);
            inv.setItem(slot, categoryIcon(category));
            holder.bind(slot, action, category.getId());
            occupied[slot] = true;
            idx++;
        }
    }

    // ================================================================
    //  filters
    // ================================================================

    private List<CatalogItem> applyFilters(List<CatalogItem> source, PlayerSession session, Player player) {
        boolean hideNotObtainable = plugin.getConfigManager().hideNotObtainableFromPlayers() && !player.hasPermission("itemcatalog.admin");

        java.util.stream.Stream<CatalogItem> stream = source.stream()
                .filter(item -> !hideNotObtainable || item.isObtainable())
                .filter(item -> session.getProviderFilter() == null || item.getProvider() == session.getProviderFilter())
                .filter(item -> session.getRarityFilter() == null || session.getRarityFilter().equalsIgnoreCase(item.getRarity()))
                .filter(item -> hasCategoryPermission(player, item.getResolvedCategory(), item.getResolvedSubCategory()))
                .filter(item -> isCategoryEnabled(item.getResolvedCategory(), item.getResolvedSubCategory()));

        // gui.item-order: "provider" (default) keeps the exact order items were
        // returned by their source plugin (e.g. MMOItems' own template order,
        // which is what makes the catalog make sense for MMOItems); "alphabetical"
        // sorts by display name instead.
        String order = plugin.getConfigManager().getConfig().getString("gui.item-order", "provider");
        if ("alphabetical".equalsIgnoreCase(order)) {
            stream = stream.sorted(Comparator.comparing(CatalogItem::getPlainName));
        }

        return stream.collect(Collectors.toList());
    }

    /** Cycles the provider filter: All -> MMOItems -> Oraxen -> ItemsAdder -> ExecutableItems -> All. */
    public void cycleProviderFilter(Player player, PlayerSession session) {
        ProviderType current = session.getProviderFilter();
        ProviderType[] values = ProviderType.values();
        if (current == null) {
            session.setProviderFilter(values[0]);
        } else {
            int idx = current.ordinal() + 1;
            session.setProviderFilter(idx >= values.length ? null : values[idx]);
        }
        player.sendMessage(ColorUtil.color("&8[&6ItemCatalog&8] &7Provider filter: &f"
                + (session.getProviderFilter() == null ? "All" : session.getProviderFilter().pluginName())));
    }

    // ================================================================
    //  navigation / permissions / rendering helpers
    // ================================================================

    private List<Category> visibleTopCategories(Player player) {
        return plugin.getCategoryManager().getTopLevelCategories().stream()
                .filter(c -> hasCategoryPermission(player, c))
                .collect(Collectors.toList());
    }

    private boolean hasCategoryPermission(Player player, Category category) {
        if (category == null || !category.requiresPermission()) return true;
        return player.hasPermission("itemcatalog.category.*") || player.hasPermission("itemcatalog.category." + category.getFullId());
    }

    private boolean hasCategoryPermission(Player player, String categoryId, String subCategoryId) {
        String fullId = subCategoryId == null ? categoryId : categoryId + "." + subCategoryId;
        Category category = plugin.getCategoryManager().getCategory(fullId);
        return hasCategoryPermission(player, category);
    }

    /**
     * True unless the item's resolved category (or, for a subcategory, its
     * parent) has been disabled ("enabled: false" in categories.yml, or its
     * dynamic MMOItems equivalent). This is what keeps a hidden category's
     * items out of BOTH normal browsing and /catalog search — previously
     * only the menu button was hidden, but the items themselves could still
     * turn up in search results.
     */
    private boolean isCategoryEnabled(String categoryId, String subCategoryId) {
        if (categoryId == null) return true;
        Category top = plugin.getCategoryManager().getCategory(categoryId);
        if (top == null) return true; // unresolved/unknown category id — don't silently hide it
        if (!top.isEnabled()) return false;
        if (subCategoryId != null) {
            Category sub = plugin.getCategoryManager().getCategory(categoryId + "." + subCategoryId);
            if (sub != null && !sub.isEnabled()) return false;
        }
        return true;
    }

    private ItemStack categoryIcon(Category category) {
        List<String> lore = new ArrayList<>();
        if (category.hasChildren()) {
            lore.add("&7" + category.getChildren().size() + " subcategories");
        } else {
            String key = category.getFullId();
            lore.add("&7" + plugin.getItemRegistry().getByCategory(key).size() + " items");
        }
        lore.add("");
        lore.add("&eClick to browse");
        return new ItemBuilder(category.getIcon())
                .name(category.getDisplayName())
                .lore(lore)
                .build();
    }

    /**
     * By default (gui.item-preview-mode: true) this returns the item exactly
     * as its provider built it — same display name, same full lore/stats
     * (e.g. every MMOItems stat line) — with nothing added or removed, so
     * hovering over it in the catalog is a true 1:1 preview of the real item.
     * Set gui.item-preview-mode: false in config.yml to instead show the
     * generic provider/category/rarity/obtainable lore block.
     */
    private ItemStack buildDisplayItem(CatalogItem item) {
        boolean previewMode = plugin.getConfigManager().getConfig().getBoolean("gui.item-preview-mode", true);
        if (previewMode) {
            return item.getItemStack();
        }

        boolean loreEnabled = plugin.getConfigManager().getConfig().getBoolean("gui.item-lore.enabled", true);
        if (!loreEnabled) {
            return new ItemBuilder(item.getItemStack()).name(item.getDisplayName()).build();
        }

        List<String> loreLines = plugin.getConfigManager().getConfig().getStringList("gui.item-lore.lines");
        String obtainableLine = item.isObtainable()
                ? plugin.getConfigManager().getConfig().getString("gui.obtainable-line", "&aObtainable")
                : plugin.getConfigManager().getConfig().getString("gui.not-obtainable-line", "&cNot obtainable");

        List<String> lore = new ArrayList<>();
        for (String line : loreLines) {
            String resolved = line
                    .replace("%provider%", item.getProvider().pluginName())
                    .replace("%category%", nullToDash(item.getResolvedCategory()))
                    .replace("%subcategory%", nullToDash(item.getResolvedSubCategory()))
                    .replace("%rarity%", nullToDash(item.getRarity()))
                    .replace("%obtainable_line%", obtainableLine);
            lore.add(resolved);
        }

        return new ItemBuilder(item.getItemStack())
                .name(item.getDisplayName())
                .lore(lore)
                .build();
    }

    private String nullToDash(String s) {
        return s == null ? "-" : s;
    }

    private void fillEmpty(Inventory inv, CatalogInventoryHolder holder, int fromSlot, int toSlotExclusive) {
        if (!plugin.getConfigManager().getConfig().getBoolean("gui.fill-empty-slots", true)) return;
        Material fill = safeMaterial(plugin.getConfigManager().getConfig().getString("gui.fill-material", "GRAY_STAINED_GLASS_PANE"));
        ItemStack filler = new ItemBuilder(fill).name(" ").build();
        for (int i = fromSlot; i < toSlotExclusive; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }
    }

    /**
     * Draws the bottom nav row (as filler) and places whichever nav buttons
     * apply to the current view. Every button's slot/material/name/lore
     * comes from config.yml (gui.nav.*) so admins can fully re-skin them.
     * <p>
     * showPrev/showNext are the "arrows only appear when needed" switches:
     * callers only pass true when there genuinely is a previous/next page,
     * so a single-page listing shows neither arrow, and the last page never
     * shows "next", etc.
     */
    private void addNavRow(Inventory inv, CatalogInventoryHolder holder, int rows,
                            boolean showPrev, boolean showNext, boolean showSearch, boolean showBack,
                            PlayerSession session) {
        int rowStart = (rows - 1) * 9;
        Material fill = safeMaterial(plugin.getConfigManager().getConfig().getString("gui.fill-material", "GRAY_STAINED_GLASS_PANE"));
        ItemStack filler = new ItemBuilder(fill).name(" ").build();
        for (int i = rowStart; i < rowStart + 9; i++) {
            inv.setItem(i, filler);
        }

        if (showPrev) {
            placeNavButton(inv, holder, "previous-page", 18, CatalogInventoryHolder.SlotAction.PREVIOUS_PAGE, null);
        }
        if (showNext) {
            placeNavButton(inv, holder, "next-page", 26, CatalogInventoryHolder.SlotAction.NEXT_PAGE, null);
        }
        if (showBack) {
            placeNavButton(inv, holder, "back", 49, CatalogInventoryHolder.SlotAction.BACK, null);
        }
        if (showSearch && plugin.getConfigManager().isSearchEnabled()) {
            placeNavButton(inv, holder, "search", 47, CatalogInventoryHolder.SlotAction.SEARCH, null);
        }
        if (plugin.getConfigManager().filtersEnabled() && plugin.getConfigManager().getConfig().getBoolean("filters.by-provider", true)) {
            String current = session.getProviderFilter() == null ? "All" : session.getProviderFilter().pluginName();
            placeNavButton(inv, holder, "filter", 51, CatalogInventoryHolder.SlotAction.FILTER, current);
        }
    }

    /**
     * Places a single fully-customizable nav button. Reads
     * gui.nav.<key>-enabled / -slot / -icon / -name / -lore from config.yml,
     * falling back to the given default slot if unset. If -enabled is false,
     * the button is skipped entirely (its slot stays empty filler). %filter%
     * in the name/lore is replaced with filterValue when provided (used by
     * the filter button).
     */
    private void placeNavButton(Inventory inv, CatalogInventoryHolder holder, String key, int defaultSlot,
                                 CatalogInventoryHolder.SlotAction action, String filterValue) {
        if (!plugin.getConfigManager().getConfig().getBoolean("gui.nav." + key + "-enabled", true)) return;

        int slot = plugin.getConfigManager().getConfig().getInt("gui.nav." + key + "-slot", defaultSlot);
        if (slot < 0 || slot >= inv.getSize()) return;

        Material material = safeMaterial(plugin.getConfigManager().getConfig().getString("gui.nav." + key + "-icon", "STONE"));
        String name = plugin.getConfigManager().getNavName(key, "&f" + key);
        List<String> lore = plugin.getConfigManager().getNavLore(key);

        if (filterValue != null) {
            name = name.replace("%filter%", filterValue);
            List<String> resolvedLore = new ArrayList<>();
            for (String line : lore) resolvedLore.add(line.replace("%filter%", filterValue));
            lore = resolvedLore;
        }

        ItemBuilder builder = new ItemBuilder(material).name(name);
        if (!lore.isEmpty()) builder.lore(lore);

        inv.setItem(slot, builder.build());
        holder.bind(slot, action);
    }

    private Material safeMaterial(String name) {
        try {
            return Material.valueOf(name.trim().toUpperCase());
        } catch (Exception e) {
            return Material.STONE;
        }
    }
}
