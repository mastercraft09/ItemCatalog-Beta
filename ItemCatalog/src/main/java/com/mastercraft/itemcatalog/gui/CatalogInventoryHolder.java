package com.mastercraft.itemcatalog.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * Tags every inventory opened by ItemCatalog so {@link GUIListener} can
 * recognise clicks that belong to us (and safely ignore everything else),
 * and stores click-slot -> action bindings for that specific inventory
 * instance so the listener doesn't need to recompute layout on every click.
 */
public class CatalogInventoryHolder implements InventoryHolder {

    public enum SlotAction { NONE, PREVIOUS_PAGE, NEXT_PAGE, BACK, SEARCH, FILTER, OPEN_CATEGORY, OPEN_SUBCATEGORY, VIEW_ITEM }

    private Inventory inventory;
    private final Map<Integer, SlotAction> actions = new HashMap<>();
    private final Map<Integer, String> actionData = new HashMap<>(); // e.g. category id, catalog item id

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void bind(int slot, SlotAction action) {
        actions.put(slot, action);
    }

    public void bind(int slot, SlotAction action, String data) {
        actions.put(slot, action);
        actionData.put(slot, data);
    }

    public SlotAction getAction(int slot) {
        return actions.getOrDefault(slot, SlotAction.NONE);
    }

    public String getActionData(int slot) {
        return actionData.get(slot);
    }
}
