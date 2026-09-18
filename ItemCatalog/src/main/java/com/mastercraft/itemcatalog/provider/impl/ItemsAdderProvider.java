package com.mastercraft.itemcatalog.provider.impl;

import com.mastercraft.itemcatalog.ItemCatalogPlugin;
import com.mastercraft.itemcatalog.model.CatalogItem;
import com.mastercraft.itemcatalog.model.ProviderType;
import com.mastercraft.itemcatalog.provider.AbstractProvider;
import com.mastercraft.itemcatalog.util.DebugLogger;
import com.mastercraft.itemcatalog.util.ReflectionUtil;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads items from ItemsAdder via reflection.
 * <p>
 * Verified against the public {@code dev.lone.itemsadder.api.CustomStack} facade:
 * <pre>
 *   CustomStack.getNamespacedIdsInRegistry()   -> Set&lt;String&gt;   e.g. "mynamespace:magic_staff"
 *   CustomStack.getInstance(String)            -> CustomStack (nullable)
 *   CustomStack#getItemStack()                 -> ItemStack
 *   CustomStack#getNamespace()                 -> String
 *   CustomStack#getId()                        -> String   (id without namespace)
 * </pre>
 * ItemsAdder does not expose a generic, ready-to-use "category" per item
 * through this facade (categories live in GUI-group config, not the item
 * registry), so per spec every item is returned with a null native
 * category rather than being dumped into a single bucket — the
 * CategoryResolver's auto-classification / manual overrides take it from there.
 */
public class ItemsAdderProvider extends AbstractProvider {

    private static final String CUSTOM_STACK_CLASS = "dev.lone.itemsadder.api.CustomStack";

    public ItemsAdderProvider(ItemCatalogPlugin plugin) {
        super(plugin);
    }

    @Override
    public ProviderType getType() {
        return ProviderType.ITEMSADDER;
    }

    @Override
    public boolean isAvailable() {
        return super.isAvailable() && ReflectionUtil.classExists(CUSTOM_STACK_CLASS);
    }

    @Override
    public List<CatalogItem> collectItems() {
        List<CatalogItem> results = new ArrayList<>();
        try {
            Class<?> customStackClass = Class.forName(CUSTOM_STACK_CLASS);

            @SuppressWarnings("unchecked")
            Set<String> namespacedIds = (Set<String>) ReflectionUtil.call(customStackClass, "getNamespacedIdsInRegistry", new Class<?>[0]);

            for (String namespacedId : namespacedIds) {
                CatalogItem item = safeConvert(namespacedId, () -> convert(customStackClass, namespacedId));
                if (item != null) {
                    results.add(item);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[ItemsAdder] Provider failed: " + t.getMessage());
            DebugLogger.debug("ItemsAdderProvider stack trace: " + t);
        }

        return filterExcluded(results);
    }

    private CatalogItem convert(Class<?> customStackClass, String namespacedId) {
        Object customStack = ReflectionUtil.call(customStackClass, "getInstance", new Class<?>[]{String.class}, namespacedId);
        if (customStack == null) {
            throw new IllegalStateException("CustomStack.getInstance(\"" + namespacedId + "\") returned null");
        }

        Object itemStackObj = ReflectionUtil.call(customStack, "getItemStack");
        ItemStack stack = (itemStackObj instanceof ItemStack) ? (ItemStack) itemStackObj : null;
        if (stack == null) {
            throw new IllegalStateException("CustomStack.getItemStack() did not return a Bukkit ItemStack for '" + namespacedId + "'");
        }

        String displayName = (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName())
                ? stack.getItemMeta().getDisplayName()
                : namespacedId;

        return new CatalogItem(
                ProviderType.ITEMSADDER,
                namespacedId,
                displayName,
                stack,
                null,  // no generic native category exposed by the public API
                null,
                null,  // no generic rarity field either
                true
        );
    }
}
