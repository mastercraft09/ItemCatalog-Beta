package com.mastercraft.itemcatalog.provider;

import com.mastercraft.itemcatalog.ItemCatalogPlugin;
import com.mastercraft.itemcatalog.model.CatalogItem;
import com.mastercraft.itemcatalog.provider.impl.ExecutableItemsProvider;
import com.mastercraft.itemcatalog.provider.impl.ItemsAdderProvider;
import com.mastercraft.itemcatalog.provider.impl.MMOItemsProvider;
import com.mastercraft.itemcatalog.provider.impl.OraxenProvider;
import com.mastercraft.itemcatalog.util.DebugLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects which of the four supported plugins are installed & enabled,
 * builds only the corresponding providers, and pulls a fresh full item
 * list from all of them on demand. A provider throwing (or simply being
 * unavailable) never stops the others from running (requirement #1 / #17).
 */
public class ProviderManager {

    private final ItemCatalogPlugin plugin;
    private final List<ItemProvider> providers = new ArrayList<>();

    public ProviderManager(ItemCatalogPlugin plugin) {
        this.plugin = plugin;
    }

    /** (Re)detects which providers are usable right now. Call on startup and on reload. */
    public void detectProviders() {
        providers.clear();
        providers.add(new MMOItemsProvider(plugin));
        providers.add(new OraxenProvider(plugin));
        providers.add(new ItemsAdderProvider(plugin));
        providers.add(new ExecutableItemsProvider(plugin));

        for (ItemProvider provider : providers) {
            boolean available = safeIsAvailable(provider);
            plugin.getLogger().info("[ItemCatalog] " + provider.getType().pluginName() + " provider: "
                    + (available ? "ENABLED" : "disabled (plugin not found or disabled in config)"));
        }
    }

    private boolean safeIsAvailable(ItemProvider provider) {
        try {
            return provider.isAvailable();
        } catch (Throwable t) {
            plugin.getLogger().warning("[ItemCatalog] " + provider.getType().pluginName() + "Provider failed availability check: " + t.getMessage());
            return false;
        }
    }

    /** Pulls a fresh list of every item from every currently-available provider. */
    public List<CatalogItem> collectAll() {
        List<CatalogItem> all = new ArrayList<>();

        for (ItemProvider provider : providers) {
            if (!safeIsAvailable(provider)) continue;

            try {
                long start = System.currentTimeMillis();
                List<CatalogItem> items = provider.collectItems();
                if (items == null) items = List.of();
                all.addAll(items);
                DebugLogger.debug(provider.getType().pluginName() + " provided " + items.size()
                        + " items in " + (System.currentTimeMillis() - start) + "ms");
            } catch (Throwable t) {
                // Extra safety net: even if a provider implementation forgot to
                // catch internally, the plugin as a whole must keep going.
                plugin.getLogger().warning("[ItemCatalog] " + provider.getType().pluginName() + "Provider failed");
                DebugLogger.debug(String.valueOf(t));
            }
        }

        return all;
    }

    public List<ItemProvider> getProviders() {
        return providers;
    }
}
