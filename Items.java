package ru.reportmenu;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class Items {
    private Items() {}

    /** &r в начале убирает курсив у названий и описаний. */
    public static ItemStack make(ReportMenuPlugin plugin, Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.color("&r" + name));
            if (lore != null && !lore.isEmpty()) {
                List<String> colored = new ArrayList<>();
                for (String line : lore) colored.add(plugin.color("&r" + line));
                meta.setLore(colored);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack filler(ReportMenuPlugin plugin) {
        return make(plugin, Material.GRAY_STAINED_GLASS_PANE, " ", null);
    }
}
