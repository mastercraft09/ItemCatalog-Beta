package com.mastercraft.itemcatalog.util;

import com.mastercraft.itemcatalog.ItemCatalogPlugin;

import java.util.logging.Level;

/**
 * Thin wrapper around the plugin logger that only prints when
 * config.yml's "debug: true" is set, plus always-on warn/error helpers
 * used by the error-handling requirements (providers must never crash
 * the plugin, but failures should still be visible).
 */
public final class DebugLogger {

    private DebugLogger() {}

    public static void debug(String message) {
        ItemCatalogPlugin plugin = ItemCatalogPlugin.getInstance();
        if (plugin != null && plugin.getConfigManager() != null && plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[debug] " + message);
        }
    }

    public static void warn(String message) {
        ItemCatalogPlugin plugin = ItemCatalogPlugin.getInstance();
        if (plugin != null) {
            plugin.getLogger().warning(message);
        }
    }

    public static void error(String message, Throwable t) {
        ItemCatalogPlugin plugin = ItemCatalogPlugin.getInstance();
        if (plugin != null) {
            plugin.getLogger().log(Level.SEVERE, message, t);
        }
    }

    public static void error(String message) {
        ItemCatalogPlugin plugin = ItemCatalogPlugin.getInstance();
        if (plugin != null) {
            plugin.getLogger().severe(message);
        }
    }
}
