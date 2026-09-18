package com.mastercraft.itemcatalog.util;

import org.bukkit.ChatColor;

public final class ColorUtil {

    private ColorUtil() {}

    public static String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public static String strip(String s) {
        if (s == null) return "";
        return ChatColor.stripColor(color(s));
    }
}
