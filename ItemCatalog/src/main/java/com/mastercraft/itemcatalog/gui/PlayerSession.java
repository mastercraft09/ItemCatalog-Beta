package com.mastercraft.itemcatalog.gui;

import com.mastercraft.itemcatalog.model.ProviderType;

/**
 * Holds a single player's current place in the catalog GUI: which
 * category/subcategory (or search query) they're browsing, which page,
 * and any active filters (requirement #12).
 */
public class PlayerSession {

    public enum View { MAIN, SUBCATEGORIES, ITEMS, SEARCH_RESULTS }

    private View view = View.MAIN;
    private String categoryId;
    private String subCategoryId;
    private String searchQuery;
    private int page = 0;

    private ProviderType providerFilter; // null = All
    private String rarityFilter;         // null = All

    private boolean awaitingChatSearch = false;
    private long chatSearchExpiresAt = 0L;

    public View getView() {
        return view;
    }

    public void setView(View view) {
        this.view = view;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getSubCategoryId() {
        return subCategoryId;
    }

    public void setSubCategoryId(String subCategoryId) {
        this.subCategoryId = subCategoryId;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(0, page);
    }

    public ProviderType getProviderFilter() {
        return providerFilter;
    }

    public void setProviderFilter(ProviderType providerFilter) {
        this.providerFilter = providerFilter;
    }

    public String getRarityFilter() {
        return rarityFilter;
    }

    public void setRarityFilter(String rarityFilter) {
        this.rarityFilter = rarityFilter;
    }

    public boolean isAwaitingChatSearch() {
        return awaitingChatSearch && System.currentTimeMillis() < chatSearchExpiresAt;
    }

    public void startAwaitingChatSearch(int timeoutSeconds) {
        this.awaitingChatSearch = true;
        this.chatSearchExpiresAt = System.currentTimeMillis() + timeoutSeconds * 1000L;
    }

    public void stopAwaitingChatSearch() {
        this.awaitingChatSearch = false;
    }

    /** "weapons.swords" style key for registry lookups, or just "weapons" if no subcategory is selected. */
    public String getResolvedCategoryKey() {
        if (categoryId == null) return null;
        return subCategoryId == null ? categoryId : categoryId + "." + subCategoryId;
    }
}
