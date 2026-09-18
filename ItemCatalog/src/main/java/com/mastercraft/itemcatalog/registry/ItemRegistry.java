package com.mastercraft.itemcatalog.registry;

import com.mastercraft.itemcatalog.model.CatalogItem;
import com.mastercraft.itemcatalog.model.ProviderType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central, thread-safe(ish) store for every {@link CatalogItem} currently
 * loaded. Rebuilt wholesale on startup and on /itemcatalog reload —
 * designed to comfortably hold several thousand items:
 * lookups are all O(1)/O(log n) via pre-built indexes, never a linear
 * scan of "all items" except for the one-time index rebuild itself.
 */
public class ItemRegistry {

    private volatile Map<String, CatalogItem> byCatalogId = new HashMap<>();
    private volatile Map<String, List<CatalogItem>> byCategory = new HashMap<>();       // "weapons" or "weapons.swords"
    private volatile Map<ProviderType, List<CatalogItem>> byProvider = new EnumMap<>(ProviderType.class);
    private volatile List<CatalogItem> all = List.of();

    // very small in-memory inverted index: token -> items, for fast prefix/substring search on modest servers
    private final Map<String, List<CatalogItem>> searchIndex = new ConcurrentHashMap<>();

    /** Wholesale replace of the registry contents. Call this once per reload, not per-item. */
    public synchronized void replaceAll(Collection<CatalogItem> items) {
        Map<String, CatalogItem> newById = new HashMap<>(items.size() * 2);
        Map<String, List<CatalogItem>> newByCategory = new HashMap<>();
        Map<ProviderType, List<CatalogItem>> newByProvider = new EnumMap<>(ProviderType.class);
        List<CatalogItem> newAll = new ArrayList<>(items.size());
        Map<String, List<CatalogItem>> newSearchIndex = new HashMap<>();

        for (CatalogItem item : items) {
            newById.put(item.getCatalogId(), item);
            newAll.add(item);

            newByProvider.computeIfAbsent(item.getProvider(), k -> new ArrayList<>()).add(item);

            String cat = item.getResolvedCategory();
            String sub = item.getResolvedSubCategory();
            if (cat != null) {
                newByCategory.computeIfAbsent(cat, k -> new ArrayList<>()).add(item);
                if (sub != null) {
                    newByCategory.computeIfAbsent(cat + "." + sub, k -> new ArrayList<>()).add(item);
                }
            }

            for (String token : tokenize(item.getPlainName())) {
                newSearchIndex.computeIfAbsent(token, k -> new ArrayList<>()).add(item);
            }
        }

        this.byCatalogId = newById;
        this.byCategory = newByCategory;
        this.byProvider = newByProvider;
        this.all = Collections.unmodifiableList(newAll);
        this.searchIndex.clear();
        this.searchIndex.putAll(newSearchIndex);
    }

    private static List<String> tokenize(String plainName) {
        String[] parts = plainName.split("[^a-z0-9]+");
        List<String> tokens = new ArrayList<>();
        for (String p : parts) {
            if (!p.isBlank()) tokens.add(p);
        }
        return tokens;
    }

    public Optional<CatalogItem> get(String catalogId) {
        return Optional.ofNullable(byCatalogId.get(catalogId.toLowerCase()));
    }

    public List<CatalogItem> getAll() {
        return all;
    }

    public List<CatalogItem> getByCategory(String categoryOrFullId) {
        return byCategory.getOrDefault(categoryOrFullId, List.of());
    }

    public List<CatalogItem> getByProvider(ProviderType provider) {
        return byProvider.getOrDefault(provider, List.of());
    }

    /** Case-insensitive substring search across display names, keeping the catalog's native item order. */
    public List<CatalogItem> search(String query) {
        return search(query, true);
    }

    /**
     * Substring search across display names. Uses the token index for a
     * fast pre-filter, then confirms with a real substring check so
     * partial-word queries ("drag") still work well.
     * <p>
     * Results come back in the SAME stable order as the rest of the catalog
     * (the order items were originally collected in — MMOItems' own
     * Type/template order, etc.), NOT alphabetically: this used to be forced
     * alphabetical here regardless of gui.item-order, which is why searching
     * e.g. an armor set could show its pieces in a different order than
     * browsing the category directly (helmet/chestplate/leggings/boots
     * scrambled into alphabetical order instead). The final sort order
     * ("provider" vs "alphabetical") is decided once, centrally, by
     * gui.item-order in CatalogGUIManager#applyFilters — not here.
     *
     * @param caseInsensitive when false, requires exact-case substring match (search.yml case-insensitive)
     */
    public List<CatalogItem> search(String query, boolean caseInsensitive) {
        if (query == null || query.isBlank()) return List.of();
        String needle = caseInsensitive ? query.toLowerCase().trim() : query.trim();

        // gather candidates from any token that starts with the first word of the query
        String firstWordLower = needle.toLowerCase().split("\\s+")[0];
        Set<CatalogItem> candidates = new HashSet<>();
        boolean usedIndex = false;
        for (Map.Entry<String, List<CatalogItem>> e : searchIndex.entrySet()) {
            if (e.getKey().startsWith(firstWordLower)) {
                candidates.addAll(e.getValue());
                usedIndex = true;
            }
        }
        // fall back to a full scan if the token index missed (e.g. query matches mid-word)
        if (!usedIndex || candidates.isEmpty()) {
            candidates = new HashSet<>(all);
        }

        // Walk "all" in its natural/native order so results come out stable
        // and consistent, instead of whatever order the HashSet/index happened to give us.
        List<CatalogItem> results = new ArrayList<>();
        for (CatalogItem item : all) {
            if (!candidates.contains(item)) continue;
            String haystack = caseInsensitive
                    ? item.getPlainName()
                    : org.bukkit.ChatColor.stripColor(org.bukkit.ChatColor.translateAlternateColorCodes('&', item.getDisplayName()));
            if (haystack != null && haystack.contains(needle)) {
                results.add(item);
            }
        }
        return results;
    }

    public int size() {
        return all.size();
    }
}
