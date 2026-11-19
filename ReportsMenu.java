package ru.reportmenu;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Список открытых жалоб для персонала. Только просмотр и управление, без телепорта. */
public class ReportsMenu implements InventoryHolder {

    public static final int PER_PAGE = 45;
    public static final int SLOT_PREV = 45;
    public static final int SLOT_RESET = 47;
    public static final int SLOT_INFO = 49;
    public static final int SLOT_NEXT = 53;

    private final ReportMenuPlugin plugin;
    private final List<Report> all;
    private final Set<UUID> flagged;
    private final Filter filter;
    private final int page;
    private final int pages;
    private final Inventory inventory;

    public ReportsMenu(ReportMenuPlugin plugin, int requestedPage, Filter filter) {
        this.plugin = plugin;
        this.filter = filter;
        this.all = plugin.manager().openReports(filter);
        this.flagged = plugin.manager().flaggedTargets();
        this.pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        this.page = Math.max(0, Math.min(requestedPage, pages - 1));

        String title = plugin.text("menus.reports-title",
                "page", String.valueOf(page + 1), "pages", String.valueOf(pages));
        this.inventory = Bukkit.createInventory(this, 54, title);

        for (int i = PER_PAGE; i < 54; i++) {
            inventory.setItem(i, Items.filler(plugin));
        }

        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < all.size(); i++) {
            Report r = all.get(start + i);
            inventory.setItem(i, headFor(r, flagged.contains(r.targetId)));
        }

        if (page > 0) {
            inventory.setItem(SLOT_PREV, Items.make(plugin, Material.ARROW,
                    plugin.getConfig().getString("menus.prev-page", "<"), null));
        }
        if (page + 1 < pages) {
            inventory.setItem(SLOT_NEXT, Items.make(plugin, Material.ARROW,
                    plugin.getConfig().getString("menus.next-page", ">"), null));
        }

        List<String> infoLore = new ArrayList<>();
        if (filter.type() != Filter.Type.NONE) {
            String raw = plugin.getConfig().getString(
                    "menus.filter-" + filter.type().name().toLowerCase(Locale.ROOT), "");
            infoLore.add(ReportMenuPlugin.replace(raw, "value", filter.value()));
            inventory.setItem(SLOT_RESET, Items.make(plugin, Material.BARRIER,
                    plugin.getConfig().getString("menus.filter-reset", "Reset"), null));
        }
        inventory.setItem(SLOT_INFO, Items.make(plugin, Material.BOOK,
                ReportMenuPlugin.replace(plugin.getConfig().getString("menus.info-name", ""),
                        "count", String.valueOf(all.size())),
                infoLore));
    }

    private ItemStack headFor(Report r, boolean isFlagged) {
        List<String> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("menus.report-lore")) {
            String t = line.trim();
            if (t.equals("{flag}")) {
                if (isFlagged) {
                    lore.add(ReportMenuPlugin.replace(plugin.getConfig().getString("menus.flag-line", ""),
                            "minutes", String.valueOf(plugin.getConfig().getInt("autoflag.minutes", 60))));
                }
            } else if (t.equals("{comment}")) {
                for (String w : wrap(r.comment, 38)) lore.add("&f" + w);
            } else {
                lore.add(ReportMenuPlugin.replace(line,
                        "reason", r.reason,
                        "reporter", r.reporterName,
                        "player", r.targetName,
                        "time", plugin.manager().formatTime(r.time),
                        "id", String.valueOf(r.id),
                        "status", plugin.manager().statusRaw(r)));
            }
        }

        String nameKey = isFlagged ? "menus.report-name-flagged" : "menus.report-name";
        String name = ReportMenuPlugin.replace(plugin.getConfig().getString(nameKey, "#{id}"),
                "id", String.valueOf(r.id), "player", r.targetName);

        ItemStack item = Items.make(plugin, Material.PLAYER_HEAD, name, lore);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(r.targetId));
        }
        if (meta != null) {
            if (isFlagged) meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            while (word.length() > width) {
                if (line.length() > 0) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                out.add(word.substring(0, width));
                word = word.substring(width);
            }
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    public Report reportAt(int slot) {
        if (slot < 0 || slot >= PER_PAGE) return null;
        int index = page * PER_PAGE + slot;
        return index < all.size() ? all.get(index) : null;
    }

    public int page() {
        return page;
    }

    public int pages() {
        return pages;
    }

    public Filter filter() {
        return filter;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
