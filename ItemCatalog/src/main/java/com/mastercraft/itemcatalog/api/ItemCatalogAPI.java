package com.mastercraft.itemcatalog.api;

import com.mastercraft.itemcatalog.ItemCatalogPlugin;
import com.mastercraft.itemcatalog.model.CatalogItem;
import com.mastercraft.itemcatalog.model.ProviderType;

import java.util.List;
import java.util.Optional;

/**
 * Small, stable facade other plugins can call into (requirement #16).
 * <p>
 * Usage from another plugin:
 * <pre>
 *   if (Bukkit.getPluginManager().isPluginEnabled("ItemCatalog")) {
 *       List&lt;CatalogItem&gt; swords = ItemCatalogAPI.getByCategory("weapons.swords");
 *   }
 * </pre>
 * Add ItemCatalog as a (soft)depend in your plugin.yml and shade/compile
 * against this module (or just call it via reflection) to use it.
 */
public final class ItemCatalogAPI {

    private ItemCatalogAPI() {}

    private static ItemCatalogPlugin plugin() {
        return ItemCatalogPlugin.getInstance();
    }

    public static List<CatalogItem> getAllItems() {
        return plugin().getItemRegistry().getAll();
    }

    public static Optional<CatalogItem> getItem(String catalogId) {
        return plugin().getItemRegistry().get(catalogId);
    }

    public static List<CatalogItem> getByCategory(String categoryOrFullId) {
        return plugin().getItemRegistry().getByCategory(categoryOrFullId);
    }

    public static List<CatalogItem> getByProvider(ProviderType provider) {
        return plugin().getItemRegistry().getByProvider(provider);
    }

    public static List<CatalogItem> search(String query) {
        return plugin().getItemRegistry().search(query);
    }

    /** Forces a full reload of configs + a fresh pull from every provider. Returns the new item count. */
    public static int reload() {
        return plugin().reloadAll();
    }

    public static int totalItemCount() {
        return plugin().getItemRegistry().size();
    }
}
