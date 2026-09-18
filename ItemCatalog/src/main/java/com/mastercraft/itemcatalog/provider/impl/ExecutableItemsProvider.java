package com.mastercraft.itemcatalog.provider.impl;

import com.mastercraft.itemcatalog.ItemCatalogPlugin;
import com.mastercraft.itemcatalog.model.CatalogItem;
import com.mastercraft.itemcatalog.model.ProviderType;
import com.mastercraft.itemcatalog.provider.AbstractProvider;
import com.mastercraft.itemcatalog.util.DebugLogger;
import com.mastercraft.itemcatalog.util.ReflectionUtil;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Reads items from ExecutableItems (Ssomar Development) via reflection.
 * <p>
 * Verified against the commonly documented API entry point:
 * <pre>
 *   ExecutableItemsAPI.getExecutableItemsManager()                 -> manager
 *   manager.getAllIds() / getIds()                                 -> Collection&lt;String&gt;
 *   manager.getExecutableItem(String)                              -> Optional&lt;ExecutableItemInterface&gt;
 *   ExecutableItemInterface#buildItem(int amount) / buildItem()    -> ItemStack
 * </pre>
 * ExecutableItems has no first-class generic "category" in its public API
 * either, so — matching the same rule as Oraxen/ItemsAdder — items come
 * back with a null native category and are handled by the resolver.
 */
public class ExecutableItemsProvider extends AbstractProvider {

    private static final String API_CLASS = "com.ssomar.executableitems.ExecutableItemsAPI";

    public ExecutableItemsProvider(ItemCatalogPlugin plugin) {
        super(plugin);
    }

    @Override
    public ProviderType getType() {
        return ProviderType.EXECUTABLEITEMS;
    }

    @Override
    public boolean isAvailable() {
        return super.isAvailable() && ReflectionUtil.classExists(API_CLASS);
    }

    @Override
    public List<CatalogItem> collectItems() {
        List<CatalogItem> results = new ArrayList<>();
        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            Object manager = ReflectionUtil.call(apiClass, "getExecutableItemsManager", new Class<?>[0]);

            Collection<?> ids = (Collection<?>) ReflectionUtil.tryCallAny(manager, "getAllIds", "getIds", "getExecutableItemsIds")
                    .orElseThrow(() -> new IllegalStateException("No id-listing method found on ExecutableItemsManager"));

            for (Object rawId : ids) {
                String id = String.valueOf(rawId);
                CatalogItem item = safeConvert(id, () -> convert(manager, id));
                if (item != null) {
                    results.add(item);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[ExecutableItems] Provider failed: " + t.getMessage());
            DebugLogger.debug("ExecutableItemsProvider stack trace: " + t);
        }

        return filterExcluded(results);
    }

    private CatalogItem convert(Object manager, String id) {
        Object rawResult = ReflectionUtil.call(manager, "getExecutableItem", new Class<?>[]{String.class}, id);

        Object executableItem = rawResult;
        if (rawResult instanceof Optional) {
            Optional<?> opt = (Optional<?>) rawResult;
            if (opt.isEmpty()) {
                throw new IllegalStateException("getExecutableItem(\"" + id + "\") returned an empty Optional");
            }
            executableItem = opt.get();
        }
        if (executableItem == null) {
            throw new IllegalStateException("getExecutableItem(\"" + id + "\") returned null");
        }

        ItemStack stack = buildItemStack(executableItem);
        if (stack == null) {
            throw new IllegalStateException("Could not build an ItemStack for '" + id + "'");
        }

        String displayName = (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName())
                ? stack.getItemMeta().getDisplayName()
                : id;

        return new CatalogItem(
                ProviderType.EXECUTABLEITEMS,
                id,
                displayName,
                stack,
                null,
                null,
                null,
                true
        );
    }

    private ItemStack buildItemStack(Object executableItem) {
        Object built = ReflectionUtil.tryCall(executableItem, "buildItem", new Class<?>[]{int.class}, 1).orElse(null);
        if (!(built instanceof ItemStack)) {
            built = ReflectionUtil.tryCallAny(executableItem, "buildItem", "build", "getItemStack").orElse(null);
        }
        return (built instanceof ItemStack) ? (ItemStack) built : null;
    }
}
