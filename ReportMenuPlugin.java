package ru.reportmenu;

import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public class ReportMenuPlugin extends JavaPlugin {

    private ReportManager manager;
    private CommentListener comments;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new ReportManager(this);
        manager.load();
        comments = new CommentListener(this);

        ReportCommand reportCommand = new ReportCommand(this);
        getCommand("report").setExecutor(reportCommand);
        getCommand("report").setTabCompleter(reportCommand);

        ReportsCommand reportsCommand = new ReportsCommand(this);
        getCommand("reports").setExecutor(reportsCommand);
        getCommand("reports").setTabCompleter(reportsCommand);

        getServer().getPluginManager().registerEvents(comments, this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
    }

    public ReportManager manager() {
        return manager;
    }

    public CommentListener comments() {
        return comments;
    }

    public String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    /** Сообщение из секции messages с префиксом. Пары: ключ, значение, ключ, значение... */
    public String msg(String key, String... pairs) {
        String raw = getConfig().getString("messages." + key, "&cНет сообщения: " + key);
        return color(getConfig().getString("prefix", "") + replace(raw, pairs));
    }

    /** Любая строка из конфига без префикса. */
    public String text(String path, String... pairs) {
        String raw = getConfig().getString(path, path);
        return color(replace(raw, pairs));
    }

    public static String replace(String raw, String... pairs) {
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            raw = raw.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return raw;
    }
}
