package com.mastercraft.itemcatalog.category;

import com.mastercraft.itemcatalog.ItemCatalogPlugin;
import com.mastercraft.itemcatalog.model.CatalogItem;
import com.mastercraft.itemcatalog.util.ColorUtil;
import com.mastercraft.itemcatalog.util.DebugLogger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/**
 * Builds and holds the category tree read from categories.yml, plus the
 * native-category-mapping table used by {@link CategoryResolver} priority 2,
 * plus any *dynamic* top-level categories auto-created directly from a
 * provider's own native categories (e.g. MMOItems Types) when the admin
 * hasn't explicitly mapped or defined them — this is what makes MMOItems'
 * own category list ("Swords", "Axes", "Bows"...) show up in the catalog
 * out of the box, with zero configuration required.
 */
public class CategoryManager {

    private final ItemCatalogPlugin plugin;

    private final Map<String, Category> topLevel = new LinkedHashMap<>();     // id -> Category
    private final Map<String, Category> byFullId = new HashMap<>();          // "weapons" or "weapons.swords" -> Category

    // provider key -> native raw category (as returned by the provider, case-insensitive) -> [category, subcategory]
    private final Map<String, Map<String, String[]>> nativeMappings = new HashMap<>();

    // dynamic-category order counter so auto-created categories keep a stable,
    // first-seen order relative to each other (and always sort after the
    // explicitly configured ones, which default to order 1-99)
    private int nextDynamicOrder = 1000;

    public CategoryManager(ItemCatalogPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        topLevel.clear();
        byFullId.clear();
        nativeMappings.clear();
        nextDynamicOrder = 1000;

        boolean categoriesFileEnabled = plugin.getConfigManager().getCategoriesConfig().getBoolean("categories-file-enabled", true);
        String position = plugin.getConfigManager().getCategoriesConfig().getString("categories-file-position", "last");
        boolean manualCategoriesLast = !"first".equalsIgnoreCase(position);
        // Order offset applied to every manually-defined category so they sort
        // after every dynamic (provider-native) category when position=="last".
        // Dynamic categories start at order 1000 and count up, so 100000 gives
        // plenty of headroom for any realistic number of them.
        int manualOrderOffset = manualCategoriesLast ? 100000 : 0;

        if (!categoriesFileEnabled) {
            DebugLogger.debug("categories-file-enabled is false — every manually-defined category in categories.yml is hidden.");
        } else {
            ConfigurationSection categories = plugin.getConfigManager().getCategoriesConfig().getConfigurationSection("categories");
            if (categories == null) {
                DebugLogger.warn("categories.yml has no 'categories' section — the catalog will be empty until providers add their own categories!");
            } else {
                List<Category> built = new ArrayList<>();
                for (String id : categories.getKeys(false)) {
                    ConfigurationSection sec = categories.getConfigurationSection(id);
                    if (sec == null) continue;
                    built.add(buildCategory(id, sec, null, manualOrderOffset));
                }
                built.sort(Comparator.comparingInt(Category::getOrder));
                for (Category c : built) {
                    topLevel.put(c.getId(), c);
                    byFullId.put(c.getFullId(), c);
                    for (Category child : c.getChildren()) {
                        byFullId.put(child.getFullId(), child);
                    }
                }
            }
        }

        loadNativeMappings();

        DebugLogger.debug("Loaded " + byFullId.size() + " configured categories/subcategories.");
    }

    private Category buildCategory(String id, ConfigurationSection sec, Category parent, int orderOffset) {
        String displayName = ColorUtil.color(sec.getString("display-name", id));
        Material icon = parseMaterial(sec.getString("icon", "CHEST"));
        int order = sec.getInt("order", 50) + orderOffset;
        boolean enabled = sec.getBoolean("enabled", true);
        int slot = sec.getInt("slot", -1);
        boolean requirePermission = sec.getBoolean("require-permission", false);

        Category category = new Category(id, displayName, icon, order, enabled, parent, slot, false, requirePermission);

        ConfigurationSection subSec = sec.getConfigurationSection("subcategories");
        if (subSec != null) {
            List<Category> children = new ArrayList<>();
            for (String subId : subSec.getKeys(false)) {
                ConfigurationSection childSec = subSec.getConfigurationSection(subId);
                if (childSec == null) continue;
                // subcategory order offsets don't matter (they never mix with
                // dynamic top-level categories), so pass 0 for those
                children.add(buildCategory(subId, childSec, category, 0));
            }
            children.sort(Comparator.comparingInt(Category::getOrder));
            category.getChildren().addAll(children);
        }

        return category;
    }

    private Material parseMaterial(String name) {
        try {
            return Material.valueOf(name.trim().toUpperCase());
        } catch (Exception e) {
            DebugLogger.warn("Unknown material '" + name + "' in categories.yml, defaulting to CHEST.");
            return Material.CHEST;
        }
    }

    private void loadNativeMappings() {
        ConfigurationSection root = plugin.getConfigManager().getCategoriesConfig().getConfigurationSection("native-category-mappings");
        if (root == null) return;

        for (String providerKey : root.getKeys(false)) {
            ConfigurationSection providerSec = root.getConfigurationSection(providerKey);
            if (providerSec == null) continue;

            Map<String, String[]> map = new HashMap<>();
            for (String rawCategory : providerSec.getKeys(false)) {
                ConfigurationSection entry = providerSec.getConfigurationSection(rawCategory);
                if (entry == null) continue;
                String category = entry.getString("category");
                String subCategory = entry.getString("sub-category", null);
                if (category != null) {
                    map.put(rawCategory.toLowerCase(), new String[]{category, subCategory});
                }
            }
            nativeMappings.put(providerKey.toLowerCase(), map);
        }
    }

    /** Returns {category, subCategory (nullable)} or null if no explicit mapping exists for this provider/rawCategory pair. */
    public String[] resolveNativeMapping(String providerKey, String rawCategory) {
        if (rawCategory == null) return null;
        Map<String, String[]> map = nativeMappings.get(providerKey.toLowerCase());
        if (map == null) return null;
        return map.get(rawCategory.toLowerCase());
    }

    // ----------------------------------------------------------------
    //  Dynamic categories — auto-created straight from provider data
    // ----------------------------------------------------------------

    /**
     * Auto-creates top-level catalog categories straight from provider data:
     * MMOItems Types, and any other provider's native category that isn't
     * explicitly mapped or already defined manually. Must run AFTER
     * {@link #load()} and provider collection, BEFORE
     * {@link CategoryResolver#resolve(CatalogItem)}.
     * <p>
     * MMOItems gets special handling ({@link #registerMmoItemsDynamicCategories})
     * so its categories always come out in MMOItems' own Type order (from
     * item-types.yml) — including Types with zero items — instead of "first
     * item seen" order, which used to push empty Types to the very end.
     */
    public void registerDynamicCategoriesFromItems(Collection<CatalogItem> items) {
        registerMmoItemsDynamicCategories(items);

        for (CatalogItem item : items) {
            if (item.getProvider() == com.mastercraft.itemcatalog.model.ProviderType.MMOITEMS) continue; // handled above

            String rawNative = item.getNativeCategory();
            if (rawNative == null || rawNative.isBlank()) continue;
            if (resolveNativeMapping(item.getProvider().configKey(), rawNative) != null) continue;

            String id = slug(rawNative);
            if (id.isEmpty() || topLevel.containsKey(id)) continue;

            Material icon = item.getItemStack().getType();
            if (icon == Material.AIR) icon = Material.CHEST;
            String displayName = prettify(rawNative);

            Category dynamicCategory = new Category(id, displayName, icon, nextDynamicOrder++, true, null, -1, true);
            topLevel.put(id, dynamicCategory);
            byFullId.put(id, dynamicCategory);

            DebugLogger.debug("Auto-created dynamic category '" + id + "' ('" + displayName + "') from "
                    + item.getProvider().pluginName() + " native category '" + rawNative + "'.");
        }
    }

    /**
     * Creates one dynamic category per MMOItems Type, walked in MMOItems'
     * own Type order (item-types.yml — same order MMOItems' own menus use),
     * INCLUDING Types that currently have zero items, so they show up
     * immediately in their correct position instead of not appearing at all
     * (or, previously, appearing bunched at the very end once added).
     * Excluded/hidden Types and Types explicitly mapped via
     * native-category-mappings are skipped, same as any other category.
     */
    private void registerMmoItemsDynamicCategories(Collection<CatalogItem> items) {
        if (!plugin.getConfigManager().isProviderEnabled("mmoitems")) return;
        var mmoProvider = findMmoItemsProvider().orElse(null);
        if (mmoProvider == null) return;

        boolean matchMmoItemsIcons = plugin.getConfigManager().getMmoItemsConfig().getBoolean("match-native-icons", true);

        // Representative icon per Type, taken from the first MMOItems item
        // seen for it (items are already pre-sorted per Type/template order),
        // used as a fallback when match-native-icons can't resolve one.
        Map<String, Material> firstItemIconByType = new HashMap<>();
        for (CatalogItem item : items) {
            if (item.getProvider() != com.mastercraft.itemcatalog.model.ProviderType.MMOITEMS) continue;
            String rawNative = item.getNativeCategory();
            if (rawNative != null) {
                firstItemIconByType.putIfAbsent(rawNative.toUpperCase(), item.getItemStack().getType());
            }
        }

        Set<String> excluded = new HashSet<>();
        for (String s : plugin.getConfigManager().getExcludedCategories("mmoitems")) {
            excluded.add(s.toLowerCase());
        }

        List<String> canonicalOrder = mmoProvider.getAllTypeIds();
        if (canonicalOrder.isEmpty()) {
            // Safety net: if the canonical Type list couldn't be read for some
            // reason, fall back to first-appearance order so nothing is lost.
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            for (CatalogItem item : items) {
                if (item.getProvider() == com.mastercraft.itemcatalog.model.ProviderType.MMOITEMS && item.getNativeCategory() != null) {
                    seen.add(item.getNativeCategory());
                }
            }
            canonicalOrder = new ArrayList<>(seen);
        }

        for (String typeId : canonicalOrder) {
            if (typeId == null || typeId.isBlank()) continue;
            if (excluded.contains(typeId.toLowerCase())) continue;
            if (resolveNativeMapping("mmoitems", typeId) != null) continue;

            String id = slug(typeId);
            if (id.isEmpty() || topLevel.containsKey(id)) continue;

            Material icon = matchMmoItemsIcons ? mmoProvider.getTypeIcon(typeId) : null;
            if (icon == null) icon = firstItemIconByType.get(typeId.toUpperCase());
            if (icon == null || icon == Material.AIR) icon = Material.CHEST;

            String displayName = prettify(typeId);
            Category dynamicCategory = new Category(id, displayName, icon, nextDynamicOrder++, true, null, -1, true);
            topLevel.put(id, dynamicCategory);
            byFullId.put(id, dynamicCategory);

            DebugLogger.debug("Auto-created MMOItems dynamic category '" + id + "' ('" + displayName + "') for type '" + typeId + "'.");
        }
    }

    private Optional<com.mastercraft.itemcatalog.provider.impl.MMOItemsProvider> findMmoItemsProvider() {
        for (var provider : plugin.getProviderManager().getProviders()) {
            if (provider instanceof com.mastercraft.itemcatalog.provider.impl.MMOItemsProvider mmoProvider) {
                return Optional.of(mmoProvider);
            }
        }
        return Optional.empty();
    }

    /** "TWO_HANDED_SWORD" / "two-handed sword" -> "two_handed_sword". Used as the internal category id. */
    public static String slug(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase().replaceAll("[^a-z0-9]+", "_");
        s = s.replaceAll("^_+|_+$", "");
        return s;
    }

    /** "TWO_HANDED_SWORD" -> "Two Handed Sword". Used as the default display name for dynamic categories. */
    public static String prettify(String raw) {
        if (raw == null || raw.isBlank()) return "Unknown";
        String[] parts = raw.trim().toLowerCase().split("[^a-z0-9]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.length() == 0 ? raw : sb.toString();
    }

    public Collection<Category> getTopLevelCategories() {
        List<Category> list = new ArrayList<>(topLevel.values());
        list.removeIf(c -> !c.isEnabled());
        list.sort(Comparator.comparingInt(Category::getOrder).thenComparing(Category::getDisplayName));
        return list;
    }

    /** Looks up by simple id (top level) or dotted full id ("weapons.swords"). */
    public Category getCategory(String idOrFullId) {
        if (idOrFullId == null) return null;
        Category direct = byFullId.get(idOrFullId);
        if (direct != null) return direct;
        return topLevel.get(idOrFullId);
    }

    public boolean categoryExists(String id) {
        return topLevel.containsKey(id) || byFullId.containsKey(id);
    }

    public Collection<Category> getAllCategoriesAndSubcategories() {
        return byFullId.values();
    }

    /**
     * Looks up a category by id, full dotted id, or display name (colors
     * stripped), all case-insensitively — used so typing a category's name
     * or id into /catalog search (e.g. an internal category id, or an
     * MMOItems Type's own id/name like "DAGGER") pulls up its items. Returns
     * null if nothing matches.
     */
    public Category findByNameOrId(String query) {
        if (query == null || query.isBlank()) return null;
        String q = query.trim();

        for (Category c : byFullId.values()) {
            if (c.getId().equalsIgnoreCase(q) || c.getFullId().equalsIgnoreCase(q)
                    || ColorUtil.strip(c.getDisplayName()).equalsIgnoreCase(q)) {
                return c;
            }
        }
        for (Category c : topLevel.values()) {
            if (c.getId().equalsIgnoreCase(q) || ColorUtil.strip(c.getDisplayName()).equalsIgnoreCase(q)) {
                return c;
            }
        }
        return null;
    }
}
