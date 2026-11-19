package ru.reportmenu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.InventoryHolder;

public class MenuListener implements Listener {

    private final ReportMenuPlugin plugin;

    public MenuListener(ReportMenuPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        InventoryHolder holder = e.getView().getTopInventory().getHolder();

        if (holder instanceof ReasonMenu reasonMenu) {
            e.setCancelled(true);
            if (e.getClickedInventory() != e.getView().getTopInventory()) return;
            if (!(e.getWhoClicked() instanceof Player player)) return;

            ReasonMenu.Choice choice = reasonMenu.choiceAt(e.getSlot());
            if (choice == null) return;

            player.closeInventory();
            String deny = plugin.manager().denyReason(player, reasonMenu.targetId(), reasonMenu.targetName());
            if (deny != null) {
                player.sendMessage(deny);
                return;
            }
            plugin.comments().begin(player, reasonMenu.targetId(), reasonMenu.targetName(),
                    choice.key(), choice.name());

        } else if (holder instanceof ReportsMenu reportsMenu) {
            handleReports(e, reportsMenu);
        }
    }

    private void handleReports(InventoryClickEvent e, ReportsMenu menu) {
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (!(e.getWhoClicked() instanceof Player staff)) return;
        if (!staff.hasPermission("reportmenu.manage")) return;

        int slot = e.getSlot();

        if (slot == ReportsMenu.SLOT_PREV && menu.page() > 0) {
            new ReportsMenu(plugin, menu.page() - 1, menu.filter()).open(staff);
            return;
        }
        if (slot == ReportsMenu.SLOT_NEXT && menu.page() + 1 < menu.pages()) {
            new ReportsMenu(plugin, menu.page() + 1, menu.filter()).open(staff);
            return;
        }
        if (slot == ReportsMenu.SLOT_RESET && menu.filter().type() != Filter.Type.NONE) {
            new ReportsMenu(plugin, 0, Filter.NONE).open(staff);
            return;
        }

        Report report = menu.reportAt(slot);
        if (report == null) return;

        switch (e.getClick()) {
            case LEFT, SHIFT_LEFT -> claim(staff, report, menu);
            case RIGHT -> resolve(staff, report, menu, false);
            case SHIFT_RIGHT -> resolve(staff, report, menu, true);
            case DROP -> new ReportsMenu(plugin, 0,
                    new Filter(Filter.Type.TARGET, report.targetName)).open(staff);
            case CONTROL_DROP -> new ReportsMenu(plugin, 0,
                    new Filter(Filter.Type.AUTHOR, report.reporterName)).open(staff);
            default -> { }
        }
    }

    private void claim(Player staff, Report report, ReportsMenu menu) {
        if (!report.isOpen()) {
            staff.sendMessage(plugin.msg("already-closed"));
        } else if (report.status == Report.Status.IN_PROGRESS
                && !report.handler.equalsIgnoreCase(staff.getName())) {
            staff.sendMessage(plugin.msg("already-taken", "moderator", report.handler));
        } else {
            plugin.manager().claim(report, staff.getName());
            staff.sendMessage(plugin.msg("taken", "id", String.valueOf(report.id)));
        }
        new ReportsMenu(plugin, menu.page(), menu.filter()).open(staff);
    }

    private void resolve(Player staff, Report report, ReportsMenu menu, boolean falseReport) {
        if (!report.isOpen()) {
            staff.sendMessage(plugin.msg("already-closed"));
        } else {
            plugin.manager().resolve(report, staff.getName(), falseReport);
            if (falseReport) {
                staff.sendMessage(plugin.msg("closed-false",
                        "id", String.valueOf(report.id), "player", report.reporterName,
                        "hours", String.valueOf(plugin.getConfig().getLong("false-report-block-hours", 24))));
            } else {
                staff.sendMessage(plugin.msg("closed", "id", String.valueOf(report.id)));
            }
        }
        new ReportsMenu(plugin, menu.page(), menu.filter()).open(staff);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        if (holder instanceof ReasonMenu || holder instanceof ReportsMenu) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            plugin.manager().deliverPending(p);
            if (p.hasPermission("reportmenu.notify")) {
                int count = plugin.manager().openCount();
                if (count > 0) {
                    p.sendMessage(plugin.msg("joined-open", "count", String.valueOf(count)));
                }
            }
        }, 40L);
    }
}
