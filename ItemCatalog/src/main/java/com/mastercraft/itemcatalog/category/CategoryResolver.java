package com.mastercraft.itemcatalog.category;

import com.mastercraft.itemcatalog.ItemCatalogPlugin;
import com.mastercraft.itemcatalog.model.CatalogItem;
import com.mastercraft.itemcatalog.util.ColorUtil;
import com.mastercraft.itemcatalog.util.DebugLogger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides the final (category, subCategory) for every {@link CatalogItem},
 * in this strict priority order:
 * <p>
 * 1. Manual override in items.yml (by catalog id)
 * 2. Native category reported by the provider, mapped via
 *    categories.yml -> native-category-mappings
 * 3. Auto-classification heuristics from config.yml -> auto-classify.rules
 * 4. "miscellaneous"
 */
public class CategoryResolver {

    private final ItemCatalogPlugin plugin;
    private List<AutoClassifyRule> rules = new ArrayList<>();

    public CategoryResolver(ItemCatalogPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean autoClassifyEnabled = true;

    public void loadRules() {
        rules = new ArrayList<>();
        autoClassifyEnabled = plugin.getConfigManager().getConfig().getBoolean("auto-classify.enabled", true);
        List<?> rawRules = plugin.getConfigManager().getConfig().getList("auto-classify.rules");
        if (rawRules == null) return;

        for (Object o : rawRules) {
            if (!(o instanceof ConfigurationSection) && !(o instanceof java.util.Map)) continue;

            ConfigurationSection sec;
            if (o instanceof ConfigurationSection) {
                sec = (ConfigurationSection) o;
            } else {
                // SnakeYAML sometimes hands back a raw Map instead of a ConfigurationSection
                sec = new org.bukkit.configuration.MemoryConfiguration();
                //noinspection unchecked
                for (var entry : ((java.util.Map<String, Object>) o).entrySet()) {
                    sec.set(entry.getKey(), entry.getValue());
                }
            }

            String category = sec.getString("category");
            if (category == null) continue;
            String subCategory = sec.getString("sub-category", null);
            List<String> materials = sec.getStringList("materials");
            List<String> keywords = sec.getStringList("keywords");
            rules.add(new AutoClassifyRule(category, subCategory, materials, keywords));
        }

        DebugLogger.debug("Loaded " + rules.size() + " auto-classify rules.");
    }

    /** Resolves and stores the category directly on the given item. */
    public void resolve(CatalogItem item) {
        // ---------- priority 1: manual override ----------
        ConfigurationSection itemsSec = plugin.getConfigManager().getItemsConfig().getConfigurationSection("items");
        if (itemsSec != null) {
            ConfigurationSection override = findOverrideSection(itemsSec, item.getCatalogId());
            if (override != null) {
                String category = override.getString("category");
                if (category != null && plugin.getCategoryManager().categoryExists(category)) {
                    String sub = override.getString("sub-category", null);
                    item.setResolvedCategory(category, sub);
                    DebugLogger.debug(item.getCatalogId() + " -> manual override -> " + category + "/" + sub);
                    return;
                } else if (category != null) {
                    DebugLogger.warn("items.yml overrides '" + item.getCatalogId() + "' to unknown category '" + category + "', ignoring.");
                }
            }
        }

        // ---------- priority 2: native category from provider ----------
        if (item.getNativeCategory() != null) {
            // 2a) admin explicitly mapped this native category onto one of our internal categories
            String[] mapped = plugin.getCategoryManager().resolveNativeMapping(item.getProvider().configKey(), item.getNativeCategory());
            if (mapped != null && plugin.getCategoryManager().categoryExists(mapped[0])) {
                item.setResolvedCategory(mapped[0], mapped[1]);
                DebugLogger.debug(item.getCatalogId() + " -> native mapping -> " + mapped[0] + "/" + mapped[1]);
                return;
            }

            // 2b) no explicit mapping — use the provider's own category as-is (this is what
            // makes MMOItems' own Types show up as real catalog categories automatically;
            // CategoryManager.registerDynamicCategoriesFromItems() creates these before resolve() runs)
            String dynamicId = com.mastercraft.itemcatalog.category.CategoryManager.slug(item.getNativeCategory());
            if (plugin.getCategoryManager().categoryExists(dynamicId)) {
                item.setResolvedCategory(dynamicId, null);
                DebugLogger.debug(item.getCatalogId() + " -> native category (as-is) -> " + dynamicId);
                return;
            }
        }

        // ---------- priority 3: auto-classification ----------
        if (autoClassifyEnabled) {
            AutoClassifyRule match = findAutoClassifyMatch(item);
            if (match != null) {
                item.setResolvedCategory(match.category, match.subCategory);
                DebugLogger.debug(item.getCatalogId() + " -> auto-classified -> " + match.category + "/" + match.subCategory);
                return;
            }
        }

        // ---------- priority 4: fallback ----------
        item.setResolvedCategory("miscellaneous", null);
        DebugLogger.debug(item.getCatalogId() + " -> fell through to miscellaneous");
    }

    /** items.yml keys look like "itemsadder:magic_staff" — case sensitivity is forgiving. */
    private ConfigurationSection findOverrideSection(ConfigurationSection itemsSec, String catalogId) {
        for (String key : itemsSec.getKeys(false)) {
            if (key.equalsIgnoreCase(catalogId)) {
                return itemsSec.getConfigurationSection(key);
            }
        }
        return null;
    }

    private AutoClassifyRule findAutoClassifyMatch(CatalogItem item) {
        ItemStack stack = item.getItemStack();
        Material material = stack.getType();
        String materialName = material.name();

        String loreText = "";
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasLore() && meta.getLore() != null) {
            loreText = String.join(" ", meta.getLore());
        }
        String haystack = (ColorUtil.strip(item.getDisplayName()) + " " + ColorUtil.strip(loreText)).toLowerCase();

        for (AutoClassifyRule rule : rules) {
            if (rule.matchesMaterial(materialName) || rule.matchesKeyword(haystack)) {
                return rule;
            }
        }
        return null;
    }

    private static class AutoClassifyRule {
        final String category;
        final String subCategory;
        final List<String> materials;
        final List<String> keywords;

        AutoClassifyRule(String category, String subCategory, List<String> materials, List<String> keywords) {
            this.category = category;
            this.subCategory = subCategory;
            this.materials = materials == null ? List.of() : materials;
            this.keywords = keywords == null ? List.of() : keywords;
        }

        boolean matchesMaterial(String materialName) {
            for (String pattern : materials) {
                if (wildcardMatch(pattern.toUpperCase(), materialName)) {
                    return true;
                }
            }
            return false;
        }

        boolean matchesKeyword(String haystackLower) {
            for (String kw : keywords) {
                if (haystackLower.contains(kw.toLowerCase())) {
                    return true;
                }
            }
            return false;
        }

        /** Supports a single leading/trailing '*' wildcard, e.g. "*_SWORD" or "COOKED_*". */
        private boolean wildcardMatch(String pattern, String value) {
            if (pattern.equals(value)) return true;
            if (pattern.startsWith("*") && pattern.endsWith("*") && pattern.length() > 1) {
                return value.contains(pattern.substring(1, pattern.length() - 1));
            }
            if (pattern.startsWith("*")) {
                return value.endsWith(pattern.substring(1));
            }
            if (pattern.endsWith("*")) {
                return value.startsWith(pattern.substring(0, pattern.length() - 1));
            }
            return false;
        }
    }
}
