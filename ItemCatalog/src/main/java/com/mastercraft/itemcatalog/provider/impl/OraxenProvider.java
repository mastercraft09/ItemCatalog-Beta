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
 * Reads items from Oraxen via reflection.
 * <p>
 * Verified against the public {@code io.th0rgal.oraxen.api.OraxenItems} facade:
 * <pre>
 *   OraxenItems.getItemNames()          -> Set&lt;String&gt;           all registered ids
 *   OraxenItems.getItemById(String)     -> ItemBuilder             Oraxen's own builder, not a Bukkit ItemStack
 *   ItemBuilder#build()                 -> ItemStack
 * </pre>
 * Oraxen does not expose a first-class "category" concept in its public
 * API, so per spec every item comes back with a null native category and
 * falls through to the CategoryResolver's auto-classification instead.
 */
public class OraxenProvider extends AbstractProvider {

    private static final String ORAXEN_ITEMS_CLASS = "io.th0rgal.oraxen.api.OraxenItems";

    public OraxenProvider(ItemCatalogPlugin plugin) {
        super(plugin);
    }

    @Override
    public ProviderType getType() {
        return ProviderType.ORAXEN;
    }

    @Override
    public boolean isAvailable() {
        return super.isAvailable() && ReflectionUtil.classExists(ORAXEN_ITEMS_CLASS);
    }

    @Override
    public List<CatalogItem> collectItems() {
        List<CatalogItem> results = new ArrayList<>();
        try {
            Class<?> oraxenItemsClass = Class.forName(ORAXEN_ITEMS_CLASS);

            @SuppressWarnings("unchecked")
            Set<String> ids = (Set<String>) ReflectionUtil.call(oraxenItemsClass, "getItemNames", new Class<?>[0]);

            for (String id : ids) {
                CatalogItem item = safeConvert(id, () -> convert(oraxenItemsClass, id));
                if (item != null) {
                    results.add(item);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[Oraxen] Provider failed: " + t.getMessage());
            DebugLogger.debug("OraxenProvider stack trace: " + t);
        }

        return filterExcluded(results);
    }

    private CatalogItem convert(Class<?> oraxenItemsClass, String id) {
        Object itemBuilder = ReflectionUtil.call(oraxenItemsClass, "getItemById", new Class<?>[]{String.class}, id);
        if (itemBuilder == null) {
            throw new IllegalStateException("OraxenItems.getItemById(\"" + id + "\") returned null");
        }

        Object built = ReflectionUtil.call(itemBuilder, "build");
        ItemStack stack = (built instanceof ItemStack) ? (ItemStack) built : null;
        if (stack == null) {
            throw new IllegalStateException("ItemBuilder.build() did not return a Bukkit ItemStack for '" + id + "'");
        }

        String displayName = (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName())
                ? stack.getItemMeta().getDisplayName()
                : id;

        return new CatalogItem(
                ProviderType.ORAXEN,
                id,
                displayName,
                stack,
                null,   // no native category concept in Oraxen's public API
                null,
                null,   // no generic rarity field either
                true
        );
    }
}
