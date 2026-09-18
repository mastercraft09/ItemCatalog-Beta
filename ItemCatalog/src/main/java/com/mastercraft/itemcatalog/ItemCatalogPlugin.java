package com.mastercraft.itemcatalog;

import com.mastercraft.itemcatalog.category.Category;
import com.mastercraft.itemcatalog.category.CategoryManager;
import com.mastercraft.itemcatalog.category.CategoryResolver;
import com.mastercraft.itemcatalog.command.CatalogCommand;
import com.mastercraft.itemcatalog.command.ItemCatalogAdminCommand;
import com.mastercraft.itemcatalog.config.ConfigManager;
import com.mastercraft.itemcatalog.gui.CatalogGUIManager;
import com.mastercraft.itemcatalog.gui.GUIListener;
import com.mastercraft.itemcatalog.model.CatalogItem;
import com.mastercraft.itemcatalog.provider.ProviderManager;
import com.mastercraft.itemcatalog.registry.ItemRegistry;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class ItemCatalogPlugin extends JavaPlugin {

    private static ItemCatalogPlugin instance;

    private ConfigManager configManager;
    private CategoryManager categoryManager;
    private CategoryResolver categoryResolver;
    private ItemRegistry itemRegistry;
    private ProviderManager providerManager;
    private CatalogGUIManager guiManager;

    public static ItemCatalogPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        this.configManager = new ConfigManager(this);
        this.configManager.load();

        this.categoryManager = new CategoryManager(this);
        this.categoryManager.load();

        this.categoryResolver = new CategoryResolver(this);
        this.categoryResolver.loadRules();

        this.itemRegistry = new ItemRegistry();
        this.providerManager = new ProviderManager(this);
        this.guiManager = new CatalogGUIManager(this);

        // Static-only permission pass now; reloadAll() (below) re-runs this
        // once dynamic (provider-native) categories are known too.
        registerCategoryPermissions();

        getServer().getPluginManager().registerEvents(new GUIListener(this), this);

        CatalogCommand catalogCommand = new CatalogCommand(this);
        getCommand("catalog").setExecutor(catalogCommand);
        getCommand("catalog").setTabCompleter(catalogCommand);

        ItemCatalogAdminCommand adminCommand = new ItemCatalogAdminCommand(this);
        getCommand("itemcatalog").setExecutor(adminCommand);
        getCommand("itemcatalog").setTabCompleter(adminCommand);

        // Providers read live plugin state, so detecting/collecting one tick
        // after enable avoids races with softdepend plugins that are still
        // finishing their own onEnable().
        getServer().getScheduler().runTask(this, () -> {
            int count = reloadAll();
            getLogger().info("ItemCatalog loaded with " + count + " items.");
        });
    }

    @Override
    public void onDisable() {
        instance = null;
    }

    /**
     * Full reload: configs -> categories -> auto-classify rules -> re-detect
     * providers -> pull every item -> resolve categories -> rebuild registry.
     * Used both at startup and by /itemcatalog reload.
     */
    public int reloadAll() {
        configManager.reload();
        categoryManager.load();
        categoryResolver.loadRules();

        providerManager.detectProviders();
        List<CatalogItem> items = providerManager.collectAll();

        // Global "who is this catalog for" filter (config.yml catalog-scope).
        // Applied before anything else touches the item list so it also
        // naturally hides MMOItems' dynamic categories when scope=="other",
        // and every other provider's categories when scope=="mmoitems".
        ConfigManager.CatalogScope scope = configManager.getCatalogScope();
        if (scope != ConfigManager.CatalogScope.ALL) {
            items = items.stream()
                    .filter(item -> switch (scope) {
                        case MMOITEMS -> item.getProvider() == com.mastercraft.itemcatalog.model.ProviderType.MMOITEMS;
                        case OTHER -> item.getProvider() != com.mastercraft.itemcatalog.model.ProviderType.MMOITEMS;
                        case ALL -> true;
                    })
                    .collect(java.util.stream.Collectors.toList());
        }

        // Auto-create categories straight from providers' own native categories
        // (e.g. MMOItems Types) BEFORE resolving items, so priority-2 resolution
        // and permission registration both see them.
        categoryManager.registerDynamicCategoriesFromItems(items);
        registerCategoryPermissions();

        for (CatalogItem item : items) {
            try {
                categoryResolver.resolve(item);
            } catch (Throwable t) {
                getLogger().warning("Failed to resolve category for '" + item.getCatalogId() + "', defaulting to miscellaneous: " + t.getMessage());
                item.setResolvedCategory("miscellaneous", null);
            }
        }

        itemRegistry.replaceAll(items);
        return itemRegistry.size();
    }

    private void registerCategoryPermissions() {
        PluginManager pm = getServer().getPluginManager();

        for (Category category : categoryManager.getAllCategoriesAndSubcategories()) {
            String node = "itemcatalog.category." + category.getFullId();
            PermissionDefault def = category.requiresPermission() ? PermissionDefault.FALSE : PermissionDefault.TRUE;
            Permission existing = pm.getPermission(node);
            if (existing == null) {
                pm.addPermission(new Permission(node, "Access to the '" + category.getFullId() + "' catalog category", def));
            } else if (existing.getDefault() != def) {
                // require-permission was toggled for this category since the permission
                // node was first registered (e.g. via /itemcatalog reload) — keep it in sync.
                existing.setDefault(def);
            }
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public CategoryManager getCategoryManager() {
        return categoryManager;
    }

    public CategoryResolver getCategoryResolver() {
        return categoryResolver;
    }

    public ItemRegistry getItemRegistry() {
        return itemRegistry;
    }

    public ProviderManager getProviderManager() {
        return providerManager;
    }

    public CatalogGUIManager getGuiManager() {
        return guiManager;
    }
}
