package ru.reportmenu;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class ReportsCommand implements CommandExecutor, TabCompleter {

    private final ReportMenuPlugin plugin;

    public ReportsCommand(ReportMenuPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            plugin.manager().load();
            sender.sendMessage(plugin.msg("reloaded"));
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("history")) {
            history(sender, args[1]);
            return true;
        }

        Filter filter = Filter.NONE;
        if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "player" -> filter = new Filter(Filter.Type.TARGET, args[1]);
                case "author" -> filter = new Filter(Filter.Type.AUTHOR, args[1]);
                case "reason" -> {
                    String key = plugin.manager().findReasonKey(args[1]);
                    if (key == null) {
                        sender.sendMessage(plugin.msg("unknown-reason", "reason", args[1]));
                        return true;
                    }
                    filter = new Filter(Filter.Type.REASON, key);
                }
                default -> {
                    sender.sendMessage(plugin.msg("reports-usage"));
                    return true;
                }
            }
        } else if (args.length != 0) {
            sender.sendMessage(plugin.msg("reports-usage"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("only-player"));
            return true;
        }
        new ReportsMenu(plugin, 0, filter).open(player);
        return true;
    }

    private void history(CommandSender sender, String name) {
        List<Report> list = plugin.manager().history(name);
        if (list.isEmpty()) {
            sender.sendMessage(plugin.msg("history-empty", "player", name));
            return;
        }

        int open = 0, solved = 0, falseCount = 0;
        Map<String, Integer> reasons = new LinkedHashMap<>();
        for (Report r : list) {
            if (r.isOpen()) open++;
            else if (r.status == Report.Status.FALSE) falseCount++;
            else solved++;
            reasons.merge(r.reason, 1, Integer::sum);
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> en : reasons.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(en.getKey()).append(" ×").append(en.getValue());
        }

        sender.sendMessage(plugin.msg("history-header", "player", list.get(0).targetName));
        sender.sendMessage(plugin.text("messages.history-summary",
                "total", String.valueOf(list.size()), "open", String.valueOf(open),
                "solved", String.valueOf(solved), "false", String.valueOf(falseCount)));
        sender.sendMessage(plugin.text("messages.history-reasons", "reasons", sb.toString()));

        int from = Math.max(0, list.size() - 5);
        for (int i = list.size() - 1; i >= from; i--) {
            Report r = list.get(i);
            String c = r.comment.length() > 60 ? r.comment.substring(0, 60) + "…" : r.comment;
            sender.sendMessage(plugin.text("messages.history-line",
                    "id", String.valueOf(r.id), "status", plugin.manager().statusRaw(r),
                    "reason", r.reason, "comment", c));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("player", "author", "reason", "history", "reload"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "player", "history" -> options.addAll(plugin.manager().names(true));
                case "author" -> options.addAll(plugin.manager().names(false));
                case "reason" -> {
                    ConfigurationSection sec = plugin.getConfig().getConfigurationSection("reasons");
                    if (sec != null) options.addAll(sec.getKeys(false));
                }
                default -> { }
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(prefix)) result.add(o);
        }
        return result;
    }
}
