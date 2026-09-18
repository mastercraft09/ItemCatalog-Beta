package com.mastercraft.itemcatalog.provider;

import com.mastercraft.itemcatalog.ItemCatalogPlugin;
import com.mastercraft.itemcatalog.model.CatalogItem;
import com.mastercraft.itemcatalog.model.ProviderType;
import com.mastercraft.itemcatalog.util.DebugLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Shared plumbing for every provider: availability checks against
 * config.yml + Bukkit's plugin manager, and a small helper that turns
 * "loop over N raw entries, convert each one" into something where a
 * single bad entry is logged and skipped instead of killing the whole
 * provider (requirement #17 — a provider failure must never crash the plugin).
 */
public abstract class AbstractProvider implements ItemProvider {

    protected final ItemCatalogPlugin plugin;

    protected AbstractProvider(ItemCatalogPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isAvailable() {
        ProviderType type = getType();
        boolean pluginPresent = plugin.getServer().getPluginManager().isPluginEnabled(type.pluginName());
        boolean configEnabled = plugin.getConfigManager().isProviderEnabled(type.configKey());
        return pluginPresent && configEnabled;
    }

    /** Final list of items, with excluded categories/items (config.yml) already filtered out. */
    protected List<CatalogItem> filterExcluded(List<CatalogItem> items) {
        Set<String> excludedCategories = Set.copyOf(plugin.getConfigManager().getExcludedCategories(getType().configKey()))
                .stream().map(String::toLowerCase).collect(Collectors.toUnmodifiableSet());
        Set<String> excludedItems = Set.copyOf(plugin.getConfigManager().getExcludedItems(getType().configKey()))
                .stream().map(String::toLowerCase).collect(Collectors.toUnmodifiableSet());

        if (excludedCategories.isEmpty() && excludedItems.isEmpty()) return items;

        List<CatalogItem> result = new ArrayList<>(items.size());
        for (CatalogItem item : items) {
            if (excludedItems.contains(item.getRawId().toLowerCase())) continue;
            if (item.getNativeCategory() != null && excludedCategories.contains(item.getNativeCategory().toLowerCase())) continue;
            result.add(item);
        }
        return result;
    }

    /**
     * Runs {@code converter} for a single raw provider entry and returns
     * its CatalogItem, or null (logged) if anything went wrong. Used so
     * one malformed item never aborts the whole collectItems() loop.
     */
    protected CatalogItem safeConvert(String label, Supplier<CatalogItem> converter) {
        try {
            return converter.get();
        } catch (Throwable t) {
            plugin.getLogger().warning("[" + getType().pluginName() + "] Failed to load item '" + label + "': " + t.getMessage());
            plugin.getLogger().warning("[" + getType().pluginName() + "] Skipping item...");
            DebugLogger.debug("Stack trace for '" + label + "': " + t);
            return null;
        }
    }
}
