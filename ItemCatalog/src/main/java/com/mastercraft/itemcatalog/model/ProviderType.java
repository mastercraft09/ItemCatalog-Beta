package com.mastercraft.itemcatalog.model;

/**
 * The set of item sources ItemCatalog knows how to read from.
 * The config key (used in config.yml / items.yml / categories.yml) is
 * always the lowercase name.
 */
public enum ProviderType {

    MMOITEMS("MMOItems"),
    ORAXEN("Oraxen"),
    ITEMSADDER("ItemsAdder"),
    EXECUTABLEITEMS("ExecutableItems");

    private final String pluginName;

    ProviderType(String pluginName) {
        this.pluginName = pluginName;
    }

    /** Exact plugin.yml "name" of the external plugin, used for Bukkit.getPluginManager().getPlugin(...). */
    public String pluginName() {
        return pluginName;
    }

    /** Lowercase config key, e.g. "mmoitems". */
    public String configKey() {
        return name().toLowerCase();
    }
}
