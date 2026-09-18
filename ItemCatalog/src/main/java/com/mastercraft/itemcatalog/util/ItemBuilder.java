package com.mastercraft.itemcatalog.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class ItemBuilder {

    private final ItemStack stack;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.stack = new ItemStack(material);
        this.meta = stack.getItemMeta();
    }

    public ItemBuilder(ItemStack base) {
        this.stack = base.clone();
        this.meta = stack.getItemMeta();
    }

    public ItemBuilder name(String name) {
        if (meta != null) meta.setDisplayName(ColorUtil.color(name));
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        if (meta == null) return this;
        List<String> colored = new ArrayList<>();
        for (String line : lines) colored.add(ColorUtil.color(line));
        meta.setLore(colored);
        return this;
    }

    public ItemBuilder amount(int amount) {
        stack.setAmount(Math.max(1, amount));
        return this;
    }

    public ItemBuilder flags(org.bukkit.inventory.ItemFlag... flags) {
        if (meta != null) meta.addItemFlags(flags);
        return this;
    }

    public ItemStack build() {
        if (meta != null) stack.setItemMeta(meta);
        return stack;
    }
}
