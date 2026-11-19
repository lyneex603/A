package ru.reportmenu;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class ReportManager {

    private final ReportMenuPlugin plugin;
    private final File file;
    private final Map<Integer, Report> reports = new TreeMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    /** Блокировки /report за ложные жалобы: автор -> до какого времени. */
    private final Map<UUID, Long> blocks = new HashMap<>();
    private int nextId = 1;

    public ReportManager(ReportMenuPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "reports.yml");
    }

    // ---------- хранение ----------

    public void load() {
        reports.clear();
        blocks.clear();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        nextId = cfg.getInt("next-id", 1);
        long now = System.currentTimeMillis();

        ConfigurationSection bsec = cfg.getConfigurationSection("blocks");
        if (bsec != null) {
            for (String key : bsec.getKeys(false)) {
                try {
                    long until = bsec.getLong(key);
                    if (until > now) blocks.put(UUID.fromString(key), until);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        ConfigurationSection sec = cfg.getConfigurationSection("reports");
        if (sec == null) return;
        long purgeMs = plugin.getConfig().getLong("purge-closed-after-days", 90) * 86_400_000L;

        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) continue;
            try {
                Report r = new Report(Integer.parseInt(key));
                r.reporterId = UUID.fromString(s.getString("reporter-id", ""));
                r.reporterName = s.getString("reporter-name", "?");
                r.targetId = UUID.fromString(s.getString("target-id", ""));
                r.targetName = s.getString("target-name", "?");
                r.reasonKey = s.getString("reason-key", "");
                r.reason = s.getString("reason", "?");
                r.comment = s.getString("comment", "");
                r.time = s.getLong("time");
                if (s.contains("status")) {
                    try {
                        r.status = Report.Status.valueOf(s.getString("status", "NEW"));
                    } catch (IllegalArgumentException e) {
                        r.status = Report.Status.NEW;
                    }
                } else {
                    r.status = s.getBoolean("open", true) ? Report.Status.NEW : Report.Status.CLOSED;
                }
                r.handler = s.getString("handler", "");
                r.closedBy = s.getString("closed-by", "");
                r.closedTime = s.getLong("closed-time");
                r.notified = s.getBoolean("notified", true);

                long ref = r.closedTime > 0 ? r.closedTime : r.time;
                if (!r.isOpen() && r.notified && purgeMs > 0 && now - ref > purgeMs) continue;

                reports.put(r.id, r);
                if (r.id >= nextId) nextId = r.id + 1;
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Пропущена повреждённая жалоба #" + key);
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("next-id", nextId);
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> b : blocks.entrySet()) {
            if (b.getValue() > now) cfg.set("blocks." + b.getKey(), b.getValue());
        }
        for (Report r : reports.values()) {
            String p = "reports." + r.id + ".";
            cfg.set(p + "reporter-id", r.reporterId.toString());
            cfg.set(p + "reporter-name", r.reporterName);
            cfg.set(p + "target-id", r.targetId.toString());
            cfg.set(p + "target-name", r.targetName);
            cfg.set(p + "reason-key", r.reasonKey);
            cfg.set(p + "reason", r.reason);
            cfg.set(p + "comment", r.comment);
            cfg.set(p + "time", r.time);
            cfg.set(p + "status", r.status.name());
            cfg.set(p + "handler", r.handler);
            cfg.set(p + "closed-by", r.closedBy);
            cfg.set(p + "closed-time", r.closedTime);
            cfg.set(p + "notified", r.notified);
        }
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить reports.yml: " + e.getMessage());
        }
    }

    // ---------- создание и проверки ----------

    public Report create(Player reporter, UUID targetId, String targetName,
                         String reasonKey, String reasonName, String comment) {
        Report r = new Report(nextId++);
        r.reporterId = reporter.getUniqueId();
        r.reporterName = reporter.getName();
        r.targetId = targetId;
        r.targetName = targetName;
        r.reasonKey = reasonKey;
        r.reason = reasonName;
        r.comment = comment;
        r.time = System.currentTimeMillis();
        reports.put(r.id, r);
        cooldowns.put(reporter.getUniqueId(), r.time);
        save();
        return r;
    }

    /** Возвращает готовое сообщение об отказе или null, если жаловаться можно. */
    public String denyReason(Player player, UUID targetId, String targetName) {
        long block = blockLeft(player.getUniqueId());
        if (block > 0) {
            return plugin.msg("blocked", "time", formatDuration(block));
        }
        if (!player.hasPermission("reportmenu.bypass")) {
            long left = cooldownLeft(player.getUniqueId());
            if (left > 0) {
                return plugin.msg("cooldown", "seconds", String.valueOf(left));
            }
        }
        if (plugin.getConfig().getBoolean("prevent-duplicates", true)
                && hasOpenFrom(player.getUniqueId(), targetId)) {
            return plugin.msg("duplicate", "player", targetName);
        }
        return null;
    }

    public boolean hasOpenFrom(UUID reporter, UUID target) {
        for (Report r : reports.values()) {
            if (r.isOpen() && r.reporterId.equals(reporter) && r.targetId.equals(target)) return true;
        }
        return false;
    }

    public long cooldownLeft(UUID reporter) {
        long cd = plugin.getConfig().getLong("cooldown-seconds", 60) * 1000L;
        Long last = cooldowns.get(reporter);
        if (last == null || cd <= 0) return 0;
        long left = cd - (System.currentTimeMillis() - last);
        return left > 0 ? (left + 999) / 1000 : 0;
    }

    public long blockLeft(UUID reporter) {
        Long until = blocks.get(reporter);
        if (until == null) return 0;
        long left = until - System.currentTimeMillis();
        if (left <= 0) {
            blocks.remove(reporter);
            return 0;
        }
        return left;
    }

    // ---------- работа с жалобами ----------

    public List<Report> openReports(Filter filter) {
        Set<UUID> flagged = flaggedTargets();
        List<Report> list = new ArrayList<>();
        for (Report r : reports.values()) {
            if (r.isOpen() && filter.matches(r)) list.add(r);
        }
        list.sort((a, b) -> {
            boolean fa = flagged.contains(a.targetId);
            boolean fb = flagged.contains(b.targetId);
            if (fa != fb) return fa ? -1 : 1;
            return Integer.compare(b.id, a.id); // новые выше
        });
        return list;
    }

    public int openCount() {
        int n = 0;
        for (Report r : reports.values()) if (r.isOpen()) n++;
        return n;
    }

    public void claim(Report r, String moderator) {
        r.status = Report.Status.IN_PROGRESS;
        r.handler = moderator;
        save();
    }

    public void resolve(Report r, String moderator, boolean falseReport) {
        long now = System.currentTimeMillis();
        r.status = falseReport ? Report.Status.FALSE : Report.Status.CLOSED;
        r.closedBy = moderator;
        r.closedTime = now;
        r.notified = false;
        if (falseReport) {
            long hours = plugin.getConfig().getLong("false-report-block-hours", 24);
            if (hours > 0) blocks.put(r.reporterId, now + hours * 3_600_000L);
        }
        save();
        deliverResult(r);
    }

    /** Сообщает автору результат, если он онлайн. */
    public void deliverResult(Report r) {
        if (r.isOpen() || r.notified) return;
        Player p = Bukkit.getPlayer(r.reporterId);
        if (p == null) return;
        String hours = String.valueOf(plugin.getConfig().getLong("false-report-block-hours", 24));
        String key = r.status == Report.Status.FALSE ? "result-false" : "result-ok";
        p.sendMessage(plugin.msg(key,
                "id", String.valueOf(r.id), "player", r.targetName,
                "moderator", r.closedBy, "hours", hours));
        r.notified = true;
        save();
    }

    /** При входе: показать результаты жалоб, закрытых пока автор был оффлайн. */
    public void deliverPending(Player p) {
        for (Report r : new ArrayList<>(reports.values())) {
            if (r.reporterId.equals(p.getUniqueId())) deliverResult(r);
        }
    }

    // ---------- авто-флаг ----------

    public Set<UUID> flaggedTargets() {
        int need = plugin.getConfig().getInt("autoflag.count", 3);
        long window = plugin.getConfig().getLong("autoflag.minutes", 60) * 60_000L;
        Set<UUID> result = new HashSet<>();
        if (need <= 0) return result;
        long now = System.currentTimeMillis();

        Map<UUID, Set<UUID>> byTarget = new HashMap<>();
        for (Report r : reports.values()) {
            if (!r.isOpen() || now - r.time > window) continue;
            byTarget.computeIfAbsent(r.targetId, k -> new HashSet<>()).add(r.reporterId);
        }
        for (Map.Entry<UUID, Set<UUID>> en : byTarget.entrySet()) {
            if (en.getValue().size() >= need) result.add(en.getKey());
        }
        return result;
    }

    public boolean isFlagged(UUID target) {
        return flaggedTargets().contains(target);
    }

    // ---------- история и справочники ----------

    public List<Report> history(String targetName) {
        List<Report> list = new ArrayList<>();
        for (Report r : reports.values()) {
            if (r.targetName.equalsIgnoreCase(targetName)) list.add(r);
        }
        return list;
    }

    public Set<String> names(boolean targets) {
        Set<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Report r : reports.values()) set.add(targets ? r.targetName : r.reporterName);
        return set;
    }

    public String findReasonKey(String input) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("reasons");
        if (sec == null) return null;
        for (String key : sec.getKeys(false)) {
            if (key.equalsIgnoreCase(input)) return key;
        }
        return null;
    }

    // ---------- уведомления персоналу ----------

    public void notifyStaff(Report r) {
        broadcastStaff(plugin.msg("notify",
                "reporter", r.reporterName, "player", r.targetName,
                "reason", r.reason, "id", String.valueOf(r.id)));
        plugin.getLogger().info("Жалоба #" + r.id + ": " + r.reporterName + " -> " + r.targetName + " (" + r.reason + ")");
    }

    public void notifyFlag(String targetName) {
        broadcastStaff(plugin.msg("autoflag",
                "player", targetName,
                "count", String.valueOf(plugin.getConfig().getInt("autoflag.count", 3)),
                "minutes", String.valueOf(plugin.getConfig().getInt("autoflag.minutes", 60))));
    }

    private void broadcastStaff(String message) {
        String sound = plugin.getConfig().getString("notify-sound", "");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.hasPermission("reportmenu.notify")) continue;
            p.sendMessage(message);
            if (!sound.isEmpty()) p.playSound(p.getLocation(), sound, 1f, 1f);
        }
    }

    // ---------- форматирование ----------

    public String statusRaw(Report r) {
        String raw = plugin.getConfig().getString("statuses." + r.status.name(), r.status.name());
        return ReportMenuPlugin.replace(raw, "handler", r.handler);
    }

    public String formatTime(long millis) {
        String pattern = plugin.getConfig().getString("date-format", "dd.MM HH:mm");
        try {
            return new SimpleDateFormat(pattern).format(new Date(millis));
        } catch (IllegalArgumentException e) {
            return new SimpleDateFormat("dd.MM HH:mm").format(new Date(millis));
        }
    }

    public String formatDuration(long millis) {
        long totalMinutes = Math.max(1, (millis + 59_999) / 60_000);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return hours > 0 ? hours + " ч " + minutes + " мин" : minutes + " мин";
    }
}
