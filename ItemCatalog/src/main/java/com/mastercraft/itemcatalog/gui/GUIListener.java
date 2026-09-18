package com.mastercraft.itemcatalog.gui;

import com.mastercraft.itemcatalog.ItemCatalogPlugin;
import com.mastercraft.itemcatalog.model.CatalogItem;
import com.mastercraft.itemcatalog.util.ColorUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Optional;

public class GUIListener implements Listener {

    private final ItemCatalogPlugin plugin;

    public GUIListener(ItemCatalogPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CatalogInventoryHolder holder)) return;
        event.setCancelled(true); // catalog is strictly view-only; never let items move

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        CatalogInventoryHolder.SlotAction action = holder.getAction(slot);
        if (action == CatalogInventoryHolder.SlotAction.NONE) return;

        CatalogGUIManager gui = plugin.getGuiManager();
        PlayerSession session = gui.getSession(player);

        switch (action) {
            case OPEN_CATEGORY -> gui.openSubCategories(player, holder.getActionData(slot));

            case OPEN_SUBCATEGORY -> {
                String data = holder.getActionData(slot);
                String categoryId = session.getCategoryId();
                if ("__all__".equals(data)) {
                    gui.openItems(player, categoryId, null, 0);
                } else {
                    gui.openItems(player, categoryId, data, 0);
                }
            }

            case VIEW_ITEM -> {
                String catalogId = holder.getActionData(slot);
                Optional<CatalogItem> item = plugin.getItemRegistry().get(catalogId);
                item.ifPresent(i -> player.sendMessage(ColorUtil.color(
                        "&8[&6ItemCatalog&8] &f" + i.getDisplayName() + " &7(" + i.getCatalogId() + ")")));
            }

            case PREVIOUS_PAGE -> reopenAtPage(player, session, session.getPage() - 1);

            case NEXT_PAGE -> reopenAtPage(player, session, session.getPage() + 1);

            case BACK -> handleBack(player, session);

            case SEARCH -> {
                if (!plugin.getConfigManager().isSearchEnabled()) {
                    player.sendMessage(ColorUtil.color("&cSearch is currently disabled."));
                    return;
                }
                if (!player.hasPermission("itemcatalog.search")) {
                    player.sendMessage(ColorUtil.color("&cYou don't have permission to search the catalog."));
                    return;
                }
                if (!plugin.getConfigManager().isSearchAllowChatInput()) {
                    player.sendMessage(ColorUtil.color("&7Use &f/catalog search <name>&7 to search."));
                    return;
                }
                player.closeInventory();
                int timeout = plugin.getConfigManager().getSearchChatTimeoutSeconds();
                session.startAwaitingChatSearch(timeout);
                player.sendMessage(ColorUtil.color("&8[&6ItemCatalog&8] &7Type the name to search for in chat (or 'cancel')."));
            }

            case FILTER -> {
                gui.cycleProviderFilter(player, session);
                reopenCurrentView(player, session);
            }

            default -> {}
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof CatalogInventoryHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getGuiManager().clearSession(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PlayerSession session = plugin.getGuiManager().getSession(player);
        if (!session.isAwaitingChatSearch()) return;

        event.setCancelled(true);
        session.stopAwaitingChatSearch();
        String message = event.getMessage().trim();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (message.equalsIgnoreCase("cancel")) {
                    player.sendMessage(ColorUtil.color("&8[&6ItemCatalog&8] &7Search cancelled."));
                    plugin.getGuiManager().openMain(player);
                    return;
                }
                int minLength = plugin.getConfigManager().getSearchMinLength();
                if (message.length() < minLength) {
                    player.sendMessage(ColorUtil.color("&cSearch query must be at least " + minLength + " characters."));
                    return;
                }
                plugin.getGuiManager().openSearch(player, message, 0);
            }
        }.runTask(plugin);
    }

    private void reopenAtPage(Player player, PlayerSession session, int page) {
        session.setPage(Math.max(0, page));
        reopenCurrentView(player, session);
    }

    private void reopenCurrentView(Player player, PlayerSession session) {
        CatalogGUIManager gui = plugin.getGuiManager();
        switch (session.getView()) {
            case MAIN -> gui.openMain(player, session.getPage());
            case SUBCATEGORIES -> gui.openSubCategories(player, session.getCategoryId(), session.getPage());
            case ITEMS -> gui.openItems(player, session.getCategoryId(), session.getSubCategoryId(), session.getPage());
            case SEARCH_RESULTS -> gui.openSearch(player, session.getSearchQuery(), session.getPage());
        }
    }

    private void handleBack(Player player, PlayerSession session) {
        CatalogGUIManager gui = plugin.getGuiManager();
        switch (session.getView()) {
            case ITEMS -> {
                var category = plugin.getCategoryManager().getCategory(session.getCategoryId());
                if (category != null && category.hasChildren()) {
                    gui.openSubCategories(player, session.getCategoryId());
                } else {
                    gui.openMain(player);
                }
            }
            case SUBCATEGORIES, SEARCH_RESULTS -> gui.openMain(player);
            default -> gui.openMain(player);
        }
    }
}
