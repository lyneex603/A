package ru.reportmenu;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.*;

/** Меню выбора причины жалобы. */
public class ReasonMenu implements InventoryHolder {

    public record Choice(String key, String name) {}

    private final UUID targetId;
    private final String targetName;
    private final Inventory inventory;
    private final Map<Integer, Choice> choiceBySlot = new HashMap<>();

    public ReasonMenu(ReportMenuPlugin plugin, UUID targetId, String targetName) {
        this.targetId = targetId;
        this.targetName = targetName;

        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("reasons");
        List<String> keys = sec == null ? new ArrayList<>() : new ArrayList<>(sec.getKeys(false));
        if (keys.size() > 28) keys = keys.subList(0, 28);

        int rows = Math.max(3, Math.min(6, (keys.size() + 6) / 7 + 2));
        String title = plugin.text("menus.reason-title", "player", targetName);
        this.inventory = Bukkit.createInventory(this, rows * 9, title);

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, Items.filler(plugin));
        }

        String hint = plugin.getConfig().getString("menus.click-hint", "");
        for (int i = 0; i < keys.size(); i++) {
            ConfigurationSection r = sec.getConfigurationSection(keys.get(i));
            if (r == null) continue;

            Material mat = Material.matchMaterial(r.getString("material", "PAPER"));
            if (mat == null || !mat.isItem()) mat = Material.PAPER;

            String name = r.getString("name", keys.get(i));
            List<String> lore = new ArrayList<>();
            lore.add("&7" + r.getString("description", ""));
            lore.add("");
            lore.add(hint);

            int slot = (1 + i / 7) * 9 + 1 + (i % 7);
            inventory.setItem(slot, Items.make(plugin, mat, name, lore));
            choiceBySlot.put(slot, new Choice(keys.get(i), ChatColor.stripColor(plugin.color(name))));
        }
    }

    public Choice choiceAt(int slot) {
        return choiceBySlot.get(slot);
    }

    public UUID targetId() {
        return targetId;
    }

    public String targetName() {
        return targetName;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
