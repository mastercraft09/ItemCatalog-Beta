package com.mastercraft.itemcatalog.provider.impl;

import com.mastercraft.itemcatalog.ItemCatalogPlugin;
import com.mastercraft.itemcatalog.model.CatalogItem;
import com.mastercraft.itemcatalog.model.ProviderType;
import com.mastercraft.itemcatalog.provider.AbstractProvider;
import com.mastercraft.itemcatalog.util.DebugLogger;
import com.mastercraft.itemcatalog.util.ReflectionUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads items from MMOItems via reflection.
 * <p>
 * Verified against the MMOItems 6.9.x public API shape:
 * <pre>
 *   MMOItems.plugin                                    (static field, the JavaPlugin instance)
 *     .getTypes().getAll()                              -> Collection&lt;Type&gt;
 *     .getTemplates().getTemplates(Type)                -> Collection&lt;MMOItemTemplate&gt;
 *   Type#getId()                                        -> String   (e.g. "SWORD")
 *   MMOItemTemplate#getId()                              -> String   (e.g. "EXCALIBUR")
 *   MMOItemTemplate#newBuilder().build()                 -> MMOItem
 *   MMOItem#newBuilder().build()                         -> ItemStack
 * </pre>
 * If your installed MMOItems build renamed any of these, adjust the
 * method-name candidates below — everything else in the plugin is
 * unaffected since nothing else touches MMOItems classes directly.
 */
public class MMOItemsProvider extends AbstractProvider {

    private static final String MMOITEMS_CLASS = "net.Indyuce.mmoitems.MMOItems";

    public MMOItemsProvider(ItemCatalogPlugin plugin) {
        super(plugin);
    }

    @Override
    public ProviderType getType() {
        return ProviderType.MMOITEMS;
    }

    @Override
    public boolean isAvailable() {
        return super.isAvailable() && ReflectionUtil.classExists(MMOITEMS_CLASS);
    }

    @Override
    public List<CatalogItem> collectItems() {
        List<CatalogItem> results = new ArrayList<>();
        templateOrderCache.clear(); // re-read item/<TYPE>.yml files fresh on every reload
        typeIconCache.clear();
        try {
            Object mmoItemsPlugin = ReflectionUtil.getStatic(MMOITEMS_CLASS, "plugin");

            Object typeManager = ReflectionUtil.call(mmoItemsPlugin, "getTypes");
            Object templateManager = ReflectionUtil.call(mmoItemsPlugin, "getTemplates");

            @SuppressWarnings("unchecked")
            Collection<Object> rawTypes = (Collection<Object>) ReflectionUtil.call(typeManager, "getAll");

            // Same issue as templates below: MMOItems' Type collection isn't
            // guaranteed to iterate in a stable, meaningful order. MMOItems
            // itself defines/orders its types via plugins/MMOItems/item-types.yml
            // (that file's top-level key order is what MMOItems' own menus use),
            // so read that file directly rather than guessing (alphabetical
            // would NOT match MMOItems here). Falls back to alphabetical only
            // if the file can't be read for some reason.
            List<Object> types = new ArrayList<>(rawTypes);
            List<String> typeOrder = loadTypeOrder();
            if (!typeOrder.isEmpty()) {
                List<String> upperOrder = new ArrayList<>();
                for (String s : typeOrder) upperOrder.add(s.toUpperCase());
                types.sort(Comparator.comparingInt(t -> {
                    String id = String.valueOf(ReflectionUtil.call(t, "getId")).toUpperCase();
                    int idx = upperOrder.indexOf(id);
                    return idx < 0 ? Integer.MAX_VALUE : idx;
                }));
            } else {
                types.sort(Comparator.comparing(t -> String.valueOf(ReflectionUtil.call(t, "getId"))));
            }

            for (Object type : types) {
                String typeId = String.valueOf(ReflectionUtil.call(type, "getId"));

                Collection<Object> templates;
                try {
                    //noinspection unchecked
                    templates = (Collection<Object>) ReflectionUtil.call(
                            templateManager, "getTemplates", new Class<?>[]{type.getClass().isInterface() ? type.getClass() : type.getClass()}, type);
                } catch (Throwable t) {
                    // fall back to the declared interface type if the exact runtime class lookup failed
                    templates = tryTemplatesFallback(templateManager, type);
                }
                if (templates == null) continue;

                // MMOItems' own API can return templates in an arbitrary (e.g. hash-based)
                // order that has nothing to do with the order the admin actually defined
                // them in. To make the catalog match what players see in MMOItems itself,
                // re-sort this type's templates using the natural top-to-bottom order of
                // its item/<TYPE>.yml config file (SnakeYAML/Bukkit preserve key order,
                // so that file's key order IS the "real" definition order).
                List<Object> orderedTemplates = new ArrayList<>(templates);
                List<String> fileOrder = loadTemplateOrderForType(typeId);
                if (!fileOrder.isEmpty()) {
                    orderedTemplates.sort(Comparator.comparingInt(t -> {
                        String id = String.valueOf(ReflectionUtil.call(t, "getId"));
                        int idx = fileOrder.indexOf(id);
                        return idx < 0 ? Integer.MAX_VALUE : idx;
                    }));
                }

                for (Object template : orderedTemplates) {
                    String templateId = String.valueOf(ReflectionUtil.call(template, "getId"));
                    String label = typeId + "." + templateId;

                    CatalogItem item = safeConvert(label, () -> convert(typeId, templateId, template));
                    if (item != null) {
                        results.add(item);
                    }
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[MMOItems] Provider failed: " + t.getMessage());
            DebugLogger.debug("MMOItemsProvider stack trace: " + t);
        }

        return filterExcludedMaterials(filterExcluded(results));
    }

    /**
     * Drops any item whose actual icon Material is on the mmoitems.yml
     * material-exclusion list for its Type (general "excluded-materials",
     * or that Type's own override under "categories.&lt;TYPE&gt;.excluded-materials"
     * — see ConfigManager#getMmoItemsExcludedMaterials).
     */
    private List<CatalogItem> filterExcludedMaterials(List<CatalogItem> items) {
        List<CatalogItem> result = new ArrayList<>(items.size());
        for (CatalogItem item : items) {
            var excludedMaterials = plugin.getConfigManager().getMmoItemsExcludedMaterials(item.getNativeCategory());
            if (excludedMaterials.isEmpty()) {
                result.add(item);
                continue;
            }
            org.bukkit.inventory.ItemStack stack = item.getItemStack();
            if (stack != null && excludedMaterials.contains(stack.getType())) {
                DebugLogger.debug("Hid '" + item.getCatalogId() + "' — material " + stack.getType()
                        + " is on the excluded-materials list for type '" + item.getNativeCategory() + "'.");
                continue; // filtered out
            }
            result.add(item);
        }
        return result;
    }

    /**
     * All MMOItems Type ids that currently exist, whether or not they have
     * any items yet — used so a brand-new/empty Type still gets a catalog
     * category instead of silently not appearing until its first item is
     * added. Prefers item-types.yml (same source as loadTypeOrder()); falls
     * back to a live reflection call if that file can't be read.
     */
    public List<String> getAllTypeIds() {
        List<String> fromFile = loadTypeOrder();
        if (!fromFile.isEmpty()) return fromFile;

        try {
            Object mmoItemsPlugin = ReflectionUtil.getStatic(MMOITEMS_CLASS, "plugin");
            Object typeManager = ReflectionUtil.call(mmoItemsPlugin, "getTypes");
            @SuppressWarnings("unchecked")
            Collection<Object> rawTypes = (Collection<Object>) ReflectionUtil.call(typeManager, "getAll");
            List<String> ids = new ArrayList<>();
            for (Object type : rawTypes) {
                ids.add(String.valueOf(ReflectionUtil.call(type, "getId")));
            }
            return ids;
        } catch (Throwable t) {
            DebugLogger.debug("Could not list MMOItems type ids: " + t);
            return List.of();
        }
    }

    /**
     * Reads the natural order MMOItems' own Types are defined in, straight
     * from plugins/MMOItems/item-types.yml (that file's top-level key order
     * — NOT alphabetical — is what MMOItems' own menus follow). Returns an
     * empty list if MMOItems isn't a normal plugin, the file is missing, or
     * anything goes wrong reading it.
     */
    private List<String> loadTypeOrder() {
        try {
            Plugin mmoItems = Bukkit.getPluginManager().getPlugin("MMOItems");
            if (mmoItems == null) return List.of();
            File file = new File(mmoItems.getDataFolder(), "item-types.yml");
            if (!file.exists()) return List.of();
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            return new ArrayList<>(yml.getKeys(false));
        } catch (Throwable t) {
            DebugLogger.debug("Could not read MMOItems type order from item-types.yml: " + t);
            return List.of();
        }
    }

    // Small per-reload cache so we don't re-read the same YAML file once per
    // item when a type has many templates.
    private final Map<String, List<String>> templateOrderCache = new HashMap<>();

    /**
     * Reads the natural, human-defined order of item ids for a given MMOItems
     * Type straight from its config file on disk (plugins/MMOItems/item/&lt;TYPE&gt;.yml),
     * since that's the order the admin actually built the items in, and the
     * order MMOItems' own GUIs (e.g. /mi browse) display them in — as opposed
     * to whatever order the runtime API's Collection happens to iterate in.
     * Returns an empty list (meaning "keep whatever order the API gave us")
     * if MMOItems isn't installed as a normal plugin, the file is missing,
     * or anything goes wrong reading it.
     */
    private List<String> loadTemplateOrderForType(String typeId) {
        return templateOrderCache.computeIfAbsent(typeId, id -> {
            try {
                Plugin mmoItems = Bukkit.getPluginManager().getPlugin("MMOItems");
                if (mmoItems == null) return List.of();
                File file = new File(mmoItems.getDataFolder(), "item" + File.separator + id + ".yml");
                if (!file.exists()) return List.of();
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
                return new ArrayList<>(yml.getKeys(false));
            } catch (Throwable t) {
                DebugLogger.debug("Could not read item order for MMOItems type '" + id + "': " + t);
                return List.of();
            }
        });
    }

    /**
     * Best-effort read of the icon Material MMOItems itself uses for a given
     * Type, straight from plugins/MMOItems/item-types.yml. Used so a
     * dynamic catalog category looks exactly like MMOItems' own menu instead
     * of just borrowing whichever item was found first (see mmoitems.yml
     * "match-native-icons"). Tries a few common key names/formats since the
     * exact schema can vary slightly between MMOItems builds; returns null
     * (meaning "fall back to an item's own material") if nothing usable is
     * found or MMOItems isn't installed as a normal plugin.
     */
    public org.bukkit.Material getTypeIcon(String typeId) {
        if (typeId == null) return null;
        return typeIconCache.computeIfAbsent(typeId.toUpperCase(), id -> {
            try {
                Plugin mmoItems = Bukkit.getPluginManager().getPlugin("MMOItems");
                if (mmoItems == null) return null;
                File file = new File(mmoItems.getDataFolder(), "item-types.yml");
                if (!file.exists()) return null;
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);

                org.bukkit.configuration.ConfigurationSection typeSec = null;
                for (String key : yml.getKeys(false)) {
                    if (key.equalsIgnoreCase(id)) {
                        typeSec = yml.getConfigurationSection(key);
                        break;
                    }
                }
                if (typeSec == null) return null;

                String raw = null;
                for (String candidate : new String[]{"display", "icon", "material", "item"}) {
                    if (typeSec.isString(candidate)) {
                        raw = typeSec.getString(candidate);
                        break;
                    }
                }
                if (raw == null || raw.isBlank()) return null;

                // strip legacy "MATERIAL:data" suffixes some MMOItems versions use
                String materialName = raw.contains(":") ? raw.substring(0, raw.indexOf(':')) : raw;
                return org.bukkit.Material.matchMaterial(materialName.trim().toUpperCase());
            } catch (Throwable t) {
                DebugLogger.debug("Could not read icon for MMOItems type '" + id + "' from item-types.yml: " + t);
                return null;
            }
        });
    }

    private final Map<String, org.bukkit.Material> typeIconCache = new HashMap<>();

    private Collection<Object> tryTemplatesFallback(Object templateManager, Object type) {
        for (Class<?> iface : type.getClass().getInterfaces()) {
            try {
                //noinspection unchecked
                return (Collection<Object>) ReflectionUtil.call(templateManager, "getTemplates", new Class<?>[]{iface}, type);
            } catch (Throwable ignored) {
                // try next interface
            }
        }
        Class<?> superClass = type.getClass().getSuperclass();
        if (superClass != null) {
            try {
                //noinspection unchecked
                return (Collection<Object>) ReflectionUtil.call(templateManager, "getTemplates", new Class<?>[]{superClass}, type);
            } catch (Throwable ignored) {
                // give up on this type
            }
        }
        return null;
    }

    private CatalogItem convert(String typeId, String templateId, Object template) {
        ItemStack stack = buildItemStack(template);

        String displayName = null;
        if (stack != null && stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
            displayName = stack.getItemMeta().getDisplayName();
        }
        if (displayName == null) {
            displayName = templateId;
        }

        String rarity = ReflectionUtil.tryCallAny(template, "getTier", "getRarity")
                .map(o -> ReflectionUtil.tryCallAny(o, "getId", "getName", "toString").map(String::valueOf).orElse(null))
                .orElse(null);

        boolean obtainable = true; // MMOItems doesn't expose a generic "obtainable" flag; default true, override via items.yml if needed

        return new CatalogItem(
                ProviderType.MMOITEMS,
                typeId + "." + templateId,
                displayName,
                stack,
                typeId,
                null,
                rarity,
                obtainable
        );
    }

    /** MMOItemTemplate -> MMOItem -> ItemStack, per the public builder API. */
    private ItemStack buildItemStack(Object template) {
        Object mmoItem = ReflectionUtil.call(ReflectionUtil.call(template, "newBuilder"), "build");
        Object built = ReflectionUtil.call(ReflectionUtil.call(mmoItem, "newBuilder"), "build");
        if (built instanceof ItemStack) {
            return (ItemStack) built;
        }
        // some versions return their own ItemStack wrapper with a toItem()/toBukkit() accessor
        Object bukkitStack = ReflectionUtil.tryCallAny(built, "toItem", "toBukkit", "build").orElse(null);
        return bukkitStack instanceof ItemStack ? (ItemStack) bukkitStack : null;
    }
}
