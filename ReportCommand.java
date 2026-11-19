package ru.reportmenu;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReportCommand implements CommandExecutor, TabCompleter {

    private final ReportMenuPlugin plugin;

    public ReportCommand(ReportMenuPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("only-player"));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(plugin.msg("usage"));
            return true;
        }

        OfflinePlayer target = Bukkit.getPlayerExact(args[0]);
        if (target == null) target = Bukkit.getOfflinePlayerIfCached(args[0]);
        if (target == null) {
            player.sendMessage(plugin.msg("not-found", "player", args[0]));
            return true;
        }
        String targetName = target.getName() != null ? target.getName() : args[0];

        if (target.getUniqueId().equals(player.getUniqueId())
                && !plugin.getConfig().getBoolean("allow-report-self", false)) {
            player.sendMessage(plugin.msg("self"));
            return true;
        }

        String deny = plugin.manager().denyReason(player, target.getUniqueId(), targetName);
        if (deny != null) {
            player.sendMessage(deny);
            return true;
        }

        new ReasonMenu(plugin, target.getUniqueId(), targetName).open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) result.add(p.getName());
            }
        }
        return result;
    }
}
