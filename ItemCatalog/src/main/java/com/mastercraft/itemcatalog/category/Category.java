package com.mastercraft.itemcatalog.category;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * A category (or subcategory — subcategories are just Categories nested
 * one level deep, subcategory.getParent() != null).
 */
public class Category {

    private final String id;
    private final String displayName;
    private final Material icon;
    private final int order;
    private final boolean enabled;
    private final Category parent; // null for top-level categories
    private final int slot;        // explicit GUI slot (0-based, within the content area), -1 = auto-place
    private final boolean dynamic; // true if this category was auto-created from a provider's native category
    private final boolean requirePermission; // if true, itemcatalog.category.<fullId> is required to see this category

    private final List<Category> children = new ArrayList<>();

    public Category(String id, String displayName, Material icon, int order, boolean enabled, Category parent, int slot, boolean dynamic) {
        this(id, displayName, icon, order, enabled, parent, slot, dynamic, false);
    }

    public Category(String id, String displayName, Material icon, int order, boolean enabled, Category parent, int slot, boolean dynamic, boolean requirePermission) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.order = order;
        this.enabled = enabled;
        this.parent = parent;
        this.slot = slot;
        this.dynamic = dynamic;
        this.requirePermission = requirePermission;
    }

    public String getId() {
        return id;
    }

    /** Full dotted id, e.g. "weapons.swords", used for permission nodes. */
    public String getFullId() {
        return parent == null ? id : parent.getId() + "." + id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public int getOrder() {
        return order;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Category getParent() {
        return parent;
    }

    public boolean isSubCategory() {
        return parent != null;
    }

    public List<Category> getChildren() {
        return children;
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    /** -1 means "no explicit slot configured, place automatically". */
    public int getSlot() {
        return slot;
    }

    public boolean hasExplicitSlot() {
        return slot >= 0;
    }

    /** True if this category was auto-created from a provider's native category (e.g. an MMOItems Type) rather than defined in categories.yml. */
    public boolean isDynamic() {
        return dynamic;
    }

    /**
     * True if this specific category requires itemcatalog.category.&lt;fullId&gt;
     * to be shown to a player, set per-category via categories.yml
     * ("require-permission: true"). Categories that don't set this stay open
     * to everyone by default — there's no global on/off switch anymore, it's
     * fully per-category.
     */
    public boolean requiresPermission() {
        return requirePermission;
    }
}
