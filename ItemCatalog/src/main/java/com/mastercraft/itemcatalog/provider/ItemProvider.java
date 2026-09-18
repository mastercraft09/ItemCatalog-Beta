package com.mastercraft.itemcatalog.provider;

import com.mastercraft.itemcatalog.model.CatalogItem;
import com.mastercraft.itemcatalog.model.ProviderType;

import java.util.List;

/**
 * A read-only adapter over one external custom-item plugin.
 * <p>
 * Implementations MUST NOT let any exception escape {@link #collectItems()}
 * — catch everything internally, log via DebugLogger, and skip the
 * offending item. The core never depends on the provider's classes; every
 * implementation talks to its target plugin purely through reflection
 * (see {@link com.mastercraft.itemcatalog.util.ReflectionUtil}) so a
 * missing/incompatible plugin can never even fail to compile the core.
 */
public interface ItemProvider {

    ProviderType getType();

    /**
     * @return true if the target plugin is installed, enabled, and this
     * provider is enabled in config.yml. ProviderManager only calls
     * {@link #collectItems()} when this returns true.
     */
    boolean isAvailable();

    /**
     * Reads every item currently known to the target plugin and converts
     * it into {@link CatalogItem}s. Must never throw — on internal error,
     * log and return whatever was successfully collected so far (partial
     * results are fine and expected).
     */
    List<CatalogItem> collectItems();
}
