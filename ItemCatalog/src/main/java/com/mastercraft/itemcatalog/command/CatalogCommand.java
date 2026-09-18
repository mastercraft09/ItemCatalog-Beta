package com.mastercraft.itemcatalog.command;

import com.mastercraft.itemcatalog.ItemCatalogPlugin;
import com.mastercraft.itemcatalog.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Handles /catalog (aliases /items, /catalogo):
 *   /catalog                 -> opens the main GUI
 *   /catalog search <name>   -> opens search results directly (requirement #11)
 */
public class CatalogCommand implements CommandExecutor, TabCompleter {

    private final ItemCatalogPlugin plugin;

    public CatalogCommand(ItemCatalogPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (!player.hasPermission("itemcatalog.use")) {
            player.sendMessage(ColorUtil.color("&cYou don't have permission to use the item catalog."));
            return true;
        }

        if (args.length == 0) {
            plugin.getGuiManager().openMain(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("search")) {
            if (!plugin.getConfigManager().isSearchEnabled()) {
                player.sendMessage(ColorUtil.color("&cSearch is currently disabled."));
                return true;
            }
            if (!player.hasPermission("itemcatalog.search")) {
                player.sendMessage(ColorUtil.color("&cYou don't have permission to search the catalog."));
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(ColorUtil.color("&cUsage: /catalog search <name>"));
                return true;
            }
            String query = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            int minLength = plugin.getConfigManager().getSearchMinLength();
            if (query.length() < minLength) {
                player.sendMessage(ColorUtil.color("&cSearch query must be at least " + minLength + " characters."));
                return true;
            }
            plugin.getGuiManager().openSearch(player, query, 0);
            return true;
        }

        plugin.getGuiManager().openMain(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("search");
        }
        return List.of();
    }
}
