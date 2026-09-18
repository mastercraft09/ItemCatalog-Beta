package com.mastercraft.itemcatalog.config;

import com.mastercraft.itemcatalog.ItemCatalogPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Loads and gives typed access to config.yml, categories.yml and items.yml.
 * Call {@link #reload()} to re-read everything from disk (used by
 * /itemcatalog reload).
 */
public class ConfigManager {

    private final ItemCatalogPlugin plugin;

    private FileConfiguration config;
    private FileConfiguration categoriesConfig;
    private FileConfiguration itemsConfig;
    private FileConfiguration mmoItemsConfig;
    private FileConfiguration providersConfig;
    private FileConfiguration searchConfig;

    private File configFile;
    private File categoriesFile;
    private File itemsFile;
    private File mmoItemsFile;
    private File providersFile;
    private File searchFile;

    public ConfigManager(ItemCatalogPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        saveResourceIfMissing("categories.yml");
        saveResourceIfMissing("items.yml");
        saveResourceIfMissing("mmoitems.yml");
        saveResourceIfMissing("providers.yml");
        saveResourceIfMissing("search.yml");

        configFile = new File(plugin.getDataFolder(), "config.yml");
        categoriesFile = new File(plugin.getDataFolder(), "categories.yml");
        itemsFile = new File(plugin.getDataFolder(), "items.yml");
        mmoItemsFile = new File(plugin.getDataFolder(), "mmoitems.yml");
        providersFile = new File(plugin.getDataFolder(), "providers.yml");
        searchFile = new File(plugin.getDataFolder(), "search.yml");

        reload();
    }

    private void saveResourceIfMissing(String name) {
        File f = new File(plugin.getDataFolder(), name);
        if (!f.exists()) {
            plugin.saveResource(name, false);
        }
    }

    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        categoriesConfig = YamlConfiguration.loadConfiguration(categoriesFile);
        itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);
        mmoItemsConfig = YamlConfiguration.loadConfiguration(mmoItemsFile);
        providersConfig = YamlConfiguration.loadConfiguration(providersFile);
        searchConfig = YamlConfiguration.loadConfiguration(searchFile);
    }

    public void saveItemsConfig() {
        try {
            itemsConfig.save(itemsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save items.yml: " + e.getMessage());
        }
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getCategoriesConfig() {
        return categoriesConfig;
    }

    public FileConfiguration getItemsConfig() {
        return itemsConfig;
    }

    /** mmoitems.yml — everything specific to the MMOItems integration. */
    public FileConfiguration getMmoItemsConfig() {
        return mmoItemsConfig;
    }

    /** providers.yml — settings for Oraxen / ItemsAdder / ExecutableItems. */
    public FileConfiguration getProvidersConfig() {
        return providersConfig;
    }

    /** search.yml — everything related to /catalog search and the search GUI button. */
    public FileConfiguration getSearchConfig() {
        return searchConfig;
    }

    // ---------------- convenience accessors ----------------

    public boolean isDebug() {
        return config.getBoolean("debug", false);
    }

    /**
     * Global "who is the catalog for" switch (config.yml catalog-scope):
     * "all" (default), "mmoitems", or "other" (= everything except MMOItems).
     */
    public CatalogScope getCatalogScope() {
        String raw = config.getString("catalog-scope", "all");
        try {
            return CatalogScope.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            return CatalogScope.ALL;
        }
    }

    public enum CatalogScope { ALL, MMOITEMS, OTHER }

    public boolean isProviderEnabled(String providerKey) {
        if ("mmoitems".equalsIgnoreCase(providerKey)) {
            return mmoItemsConfig.getBoolean("enabled", true);
        }
        return providersConfig.getBoolean("providers." + providerKey + ".enabled", true);
    }

    /**
     * Categories (MMOItems Types) whose items must be fully skipped, as if
     * they didn't exist. For MMOItems this is the union of the explicit
     * "excluded-categories" list AND any Type marked "visible: false" under
     * mmoitems.yml's "categories:" section — hiding a category that way has
     * exactly the same effect as listing it in excluded-categories: no
     * dynamic catalog category is created for it, it's not browsable, and
     * it can never turn up in /catalog search either.
     */
    public List<String> getExcludedCategories(String providerKey) {
        if ("mmoitems".equalsIgnoreCase(providerKey)) {
            List<String> excluded = new ArrayList<>(mmoItemsConfig.getStringList("excluded-categories"));
            ConfigurationSection categories = mmoItemsConfig.getConfigurationSection("categories");
            if (categories != null) {
                for (String typeId : categories.getKeys(false)) {
                    ConfigurationSection sec = categories.getConfigurationSection(typeId);
                    if (sec != null && !sec.getBoolean("visible", true)) {
                        excluded.add(typeId);
                    }
                }
            }
            return excluded;
        }
        return providersConfig.getStringList("providers." + providerKey + ".excluded-categories");
    }

    /**
     * Materials to hide MMOItems items for, e.g. placeholder items whose icon
     * is a plain glass pane. Reads mmoitems.yml's per-category
     * "categories.&lt;typeId&gt;.excluded-materials" override if that key is
     * explicitly present for this Type (even as an empty list — which means
     * "show every material for this category, ignore the general list"),
     * otherwise falls back to the general top-level "excluded-materials" list.
     * typeId may be null (general list only).
     */
    public Set<Material> getMmoItemsExcludedMaterials(String typeId) {
        if (typeId != null) {
            ConfigurationSection categories = mmoItemsConfig.getConfigurationSection("categories");
            if (categories != null) {
                for (String key : categories.getKeys(false)) {
                    if (key.equalsIgnoreCase(typeId)) {
                        ConfigurationSection sec = categories.getConfigurationSection(key);
                        if (sec != null && sec.isSet("excluded-materials")) {
                            return parseMaterials(sec.getStringList("excluded-materials"));
                        }
                        break;
                    }
                }
            }
        }
        // If mmoitems.yml on disk predates this setting (an old file that was
        // never regenerated/updated), fall back to the same built-in default
        // this plugin ships with, instead of silently excluding nothing.
        if (!mmoItemsConfig.isSet("excluded-materials")) {
            return parseMaterials(List.of("BLACK_STAINED_GLASS_PANE"));
        }
        return parseMaterials(mmoItemsConfig.getStringList("excluded-materials"));
    }

    private Set<Material> parseMaterials(List<String> names) {
        if (names == null || names.isEmpty()) return Set.of();
        Set<Material> result = new HashSet<>();
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            try {
                result.add(Material.valueOf(name.trim().toUpperCase()));
            } catch (Exception e) {
                com.mastercraft.itemcatalog.util.DebugLogger.warn("Unknown material '" + name + "' in mmoitems.yml excluded-materials, ignoring.");
            }
        }
        return result;
    }

    public List<String> getExcludedItems(String providerKey) {
        if ("mmoitems".equalsIgnoreCase(providerKey)) {
            return mmoItemsConfig.getStringList("excluded-items");
        }
        return providersConfig.getStringList("providers." + providerKey + ".excluded-items");
    }

    public boolean hideNotObtainableFromPlayers() {
        return config.getBoolean("hide-not-obtainable-from-players", false);
    }

    public boolean filtersEnabled() {
        return config.getBoolean("filters.enabled", true);
    }

    public int getGuiRows() {
        return Math.max(3, Math.min(6, config.getInt("gui.rows", 6)));
    }

    /** The exact slots (0-based) where top-level/subcategory icons are auto-placed, e.g. from "10-16,19-25,28-34". */
    public List<Integer> getCategorySlots() {
        String pattern = config.getString("gui.category-slots", "10-16,19-25,28-34");
        boolean vertical = config.getBoolean("gui.category-slots-vertical-fill", true);
        return com.mastercraft.itemcatalog.util.SlotPattern.parse(pattern, getGuiRows() * 9, vertical);
    }

    /** The exact slots (0-based) where catalog items are auto-placed, e.g. from "10-16,19-25,28-34,37-43". */
    public List<Integer> getItemSlots() {
        String pattern = config.getString("gui.item-slots", "10-16,19-25,28-34,37-43");
        boolean vertical = config.getBoolean("gui.item-slots-vertical-fill", true);
        return com.mastercraft.itemcatalog.util.SlotPattern.parse(pattern, getGuiRows() * 9, vertical);
    }

    public int getItemsPerPage() {
        return config.getInt("gui.items-per-page", 45);
    }

    /** Slot layout used ONLY for /catalog search results (search.yml item-slots). */
    public List<Integer> getSearchItemSlots() {
        String pattern = searchConfig.getString("item-slots", "10-16,19-25,28-34,37-43");
        boolean vertical = searchConfig.getBoolean("item-slots-vertical-fill", true);
        return com.mastercraft.itemcatalog.util.SlotPattern.parse(pattern, getGuiRows() * 9, vertical);
    }

    // ---------------- search.yml ----------------

    public boolean isSearchEnabled() {
        return searchConfig.getBoolean("enabled", true);
    }

    public boolean isSearchCaseInsensitive() {
        return searchConfig.getBoolean("case-insensitive", true);
    }

    public int getSearchMinLength() {
        return searchConfig.getInt("min-length", 2);
    }

    public boolean isSearchAllowChatInput() {
        return searchConfig.getBoolean("allow-chat-input", true);
    }

    public int getSearchChatTimeoutSeconds() {
        return searchConfig.getInt("chat-timeout-seconds", 30);
    }

    /**
     * Resolves a raw search query against search.yml's "search-alias" table
     * (case-insensitively). Returns the target category id (e.g. "armor")
     * if the query exactly matches one of its configured alias words, or
     * null if it isn't an alias for anything.
     */
    public String resolveSearchAlias(String query) {
        if (query == null || query.isBlank()) return null;
        String q = query.trim();
        ConfigurationSection sec = searchConfig.getConfigurationSection("search-alias");
        if (sec == null) return null;
        for (String categoryId : sec.getKeys(false)) {
            for (String alias : sec.getStringList(categoryId)) {
                if (alias != null && alias.trim().equalsIgnoreCase(q)) {
                    return categoryId;
                }
            }
        }
        return null;
    }

    /**
     * Per-MMOItems-Type item-slot layout override, read from mmoitems.yml's
     * "categories:" section. Returns empty unless it actually applies:
     * either the feature's master switch (custom-item-slots-enabled) is on
     * and this type has a "slots" entry, or this specific type has its own
     * "custom-slots: true" exception overriding the master switch. typeId is
     * matched case-insensitively against the MMOItems Type id.
     * <p>
     * NOTE: this is purely about the item GRID LAYOUT. Whether the category
     * shows up in the catalog at all is a separate, independent setting —
     * see "visible" under the same "categories:" section (wired through
     * {@link #getExcludedCategories(String)}), not this method.
     */
    public Optional<CategorySlotOverride> getMmoItemsCategorySlotOverride(String typeId) {
        if (typeId == null) return Optional.empty();
        boolean masterEnabled = mmoItemsConfig.getBoolean("custom-item-slots-enabled", false);
        ConfigurationSection categories = mmoItemsConfig.getConfigurationSection("categories");
        if (categories == null) return Optional.empty();

        ConfigurationSection catSec = null;
        for (String key : categories.getKeys(false)) {
            if (key.equalsIgnoreCase(typeId)) {
                catSec = categories.getConfigurationSection(key);
                break;
            }
        }
        if (catSec == null) return Optional.empty();

        boolean exceptionEnabled = catSec.getBoolean("custom-slots", false);
        if (!masterEnabled && !exceptionEnabled) return Optional.empty();

        String slots = catSec.getString("slots", null);
        if (slots == null || slots.isBlank()) return Optional.empty();
        boolean vertical = catSec.getBoolean("vertical-fill", true);
        return Optional.of(new CategorySlotOverride(slots, vertical));
    }

    /** Simple holder for a resolved per-category slot pattern + fill direction. */
    public record CategorySlotOverride(String slotPattern, boolean verticalFill) {
        public List<Integer> resolveSlots(int invSize) {
            return com.mastercraft.itemcatalog.util.SlotPattern.parse(slotPattern, invSize, verticalFill);
        }
    }

    public String getGuiString(String path, String def) {
        return config.getString(path, def);
    }

    // ---------------- customizable GUI buttons (nav + all-category) ----------------

    /** Display name for a nav button, e.g. key="previous-page" -> gui.nav.previous-page-name. */
    public String getNavName(String key, String def) {
        return config.getString("gui.nav." + key + "-name", def);
    }

    /** Lore for a nav button, e.g. key="previous-page" -> gui.nav.previous-page-lore. Empty list = no lore. */
    public List<String> getNavLore(String key) {
        return config.getStringList("gui.nav." + key + "-lore");
    }

    public String getAllCategoryButtonMaterial() {
        return config.getString("gui.all-category-button.material", "CHEST");
    }

    public String getAllCategoryButtonName() {
        return config.getString("gui.all-category-button.name", "&f&lAll %category%");
    }

    public List<String> getAllCategoryButtonLore() {
        return config.getStringList("gui.all-category-button.lore");
    }

    /**
     * Optional per-item GUI slot pin, set via items.yml (`slot: <n>`),
     * e.g.:
     * <pre>
     * items:
     *   mmoitems:sword.excalibur:
     *     slot: 4
     * </pre>
     * Only honoured on the first page of a category/search view. Returns
     * -1 if no pin is configured for this catalog id.
     */
    public int getItemPinnedSlot(String catalogId) {
        org.bukkit.configuration.ConfigurationSection itemsSec = itemsConfig.getConfigurationSection("items");
        if (itemsSec == null) return -1;
        for (String key : itemsSec.getKeys(false)) {
            if (key.equalsIgnoreCase(catalogId)) {
                org.bukkit.configuration.ConfigurationSection entry = itemsSec.getConfigurationSection(key);
                return entry != null ? entry.getInt("slot", -1) : -1;
            }
        }
        return -1;
    }
}
