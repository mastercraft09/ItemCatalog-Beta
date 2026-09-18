package com.mastercraft.itemcatalog.model;

import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A single, provider-agnostic entry in the catalog.
 * <p>
 * Instances are effectively immutable once built except for the "resolved"
 * category/subcategory fields, which are filled in afterwards by the
 * {@link com.mastercraft.itemcatalog.category.CategoryResolver}.
 */
public final class CatalogItem {

    private final ProviderType provider;
    private final String rawId;              // id exactly as returned by the provider (no "provider:" prefix)
    private final String catalogId;           // "<provider>:<rawId>", globally unique, lowercase
    private final String displayName;
    private final String plainName;           // display name with colour codes stripped, lowercased, for search
    private final ItemStack itemStack;

    private final String nativeCategory;      // nullable
    private final String nativeSubCategory;   // nullable
    private final String rarity;              // nullable
    private final boolean obtainable;

    private final Map<String, Object> metadata = new HashMap<>();

    // filled in later by the resolver
    private String resolvedCategory = "miscellaneous";
    private String resolvedSubCategory = null;

    public CatalogItem(ProviderType provider,
                        String rawId,
                        String displayName,
                        ItemStack itemStack,
                        String nativeCategory,
                        String nativeSubCategory,
                        String rarity,
                        boolean obtainable) {
        this.provider = Objects.requireNonNull(provider);
        this.rawId = Objects.requireNonNull(rawId).toLowerCase();
        this.catalogId = provider.configKey() + ":" + this.rawId;
        this.displayName = displayName == null ? rawId : displayName;
        this.plainName = stripColor(this.displayName).toLowerCase();
        this.itemStack = itemStack == null ? new ItemStack(org.bukkit.Material.BARRIER) : itemStack.clone();
        this.nativeCategory = nativeCategory;
        this.nativeSubCategory = nativeSubCategory;
        this.rarity = rarity;
        this.obtainable = obtainable;
    }

    private static String stripColor(String s) {
        return org.bukkit.ChatColor.stripColor(org.bukkit.ChatColor.translateAlternateColorCodes('&', s));
    }

    public ProviderType getProvider() {
        return provider;
    }

    public String getRawId() {
        return rawId;
    }

    /** Globally unique id across every provider, e.g. "mmoitems:sword.excalibur". */
    public String getCatalogId() {
        return catalogId;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Lowercase, colour-stripped name — used by the search index. */
    public String getPlainName() {
        return plainName;
    }

    /** Always returns a defensive clone so callers can safely mutate it (e.g. lore). */
    public ItemStack getItemStack() {
        return itemStack.clone();
    }

    public String getNativeCategory() {
        return nativeCategory;
    }

    public String getNativeSubCategory() {
        return nativeSubCategory;
    }

    public String getRarity() {
        return rarity;
    }

    public boolean isObtainable() {
        return obtainable;
    }

    public String getResolvedCategory() {
        return resolvedCategory;
    }

    public String getResolvedSubCategory() {
        return resolvedSubCategory;
    }

    public void setResolvedCategory(String category, String subCategory) {
        this.resolvedCategory = category == null ? "miscellaneous" : category;
        this.resolvedSubCategory = subCategory;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CatalogItem)) return false;
        return catalogId.equals(((CatalogItem) o).catalogId);
    }

    @Override
    public int hashCode() {
        return catalogId.hashCode();
    }

    @Override
    public String toString() {
        return "CatalogItem{" + catalogId + ", cat=" + resolvedCategory + "/" + resolvedSubCategory + "}";
    }
}
