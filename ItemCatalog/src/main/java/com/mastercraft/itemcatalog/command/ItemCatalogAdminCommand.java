package com.mastercraft.itemcatalog.command;

import com.mastercraft.itemcatalog.ItemCatalogPlugin;
import com.mastercraft.itemcatalog.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/** Handles /itemcatalog reload. */
public class ItemCatalogAdminCommand implements CommandExecutor, TabCompleter {

    private final ItemCatalogPlugin plugin;

    public ItemCatalogAdminCommand(ItemCatalogPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(ColorUtil.color("&cUsage: /itemcatalog reload"));
            return true;
        }

        if (!sender.hasPermission("itemcatalog.reload")) {
            sender.sendMessage(ColorUtil.color("&cYou don't have permission to do that."));
            return true;
        }

        long start = System.currentTimeMillis();
        int count = plugin.reloadAll();
        long took = System.currentTimeMillis() - start;

        sender.sendMessage(ColorUtil.color("&8[&6ItemCatalog&8] &aReloaded successfully — &f" + count
                + " &aitems loaded in &f" + took + "ms&a."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload");
        }
        return List.of();
    }
}
