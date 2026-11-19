package ru.reportmenu;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Ждёт от игрока комментарий к жалобе (его сообщение в чат не уходит остальным). */
public class CommentListener implements Listener {

    private record Pending(UUID targetId, String targetName, String reasonKey, String reasonName, long token) {}

    private final ReportMenuPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public CommentListener(ReportMenuPlugin plugin) {
        this.plugin = plugin;
    }

    public void begin(Player player, UUID targetId, String targetName, String reasonKey, String reasonName) {
        Pending p = new Pending(targetId, targetName, reasonKey, reasonName, System.nanoTime());
        pending.put(player.getUniqueId(), p);

        int min = plugin.getConfig().getInt("comment.min-length", 10);
        int timeout = plugin.getConfig().getInt("comment.timeout-seconds", 90);
        List<String> cancelWords = plugin.getConfig().getStringList("comment.cancel-words");
        String cancel = cancelWords.isEmpty() ? "cancel" : cancelWords.get(cancelWords.size() - 1);
        String hint = plugin.getConfig().getString("reasons." + reasonKey + ".hint", "");

        player.sendMessage(plugin.msg("comment-prompt",
                "reason", reasonName, "player", targetName,
                "min", String.valueOf(min), "seconds", String.valueOf(timeout), "cancel", cancel));
        if (!hint.isEmpty()) {
            player.sendMessage(plugin.msg("comment-hint", "hint", hint));
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pending.remove(player.getUniqueId(), p) && player.isOnline()) {
                player.sendMessage(plugin.msg("comment-timeout"));
            }
        }, timeout * 20L);
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        Pending p = pending.get(player.getUniqueId());
        if (p == null) return;
        e.setCancelled(true);

        final String text = sanitize(e.getMessage());

        for (String word : plugin.getConfig().getStringList("comment.cancel-words")) {
            if (word.equalsIgnoreCase(text)) {
                pending.remove(player.getUniqueId(), p);
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(plugin.msg("comment-cancelled")));
                return;
            }
        }

        int min = plugin.getConfig().getInt("comment.min-length", 10);
        int max = plugin.getConfig().getInt("comment.max-length", 200);
        if (text.length() < min) {
            Bukkit.getScheduler().runTask(plugin,
                    () -> player.sendMessage(plugin.msg("comment-short", "min", String.valueOf(min))));
            return;
        }

        final String comment = text.length() > max ? text.substring(0, max) : text;
        pending.remove(player.getUniqueId(), p);
        Bukkit.getScheduler().runTask(plugin, () -> finish(player, p, comment));
    }

    private void finish(Player player, Pending p, String comment) {
        if (!player.isOnline()) return;
        ReportManager manager = plugin.manager();

        String deny = manager.denyReason(player, p.targetId(), p.targetName());
        if (deny != null) {
            player.sendMessage(deny);
            return;
        }

        boolean wasFlagged = manager.isFlagged(p.targetId());
        Report report = manager.create(player, p.targetId(), p.targetName(), p.reasonKey(), p.reasonName(), comment);
        player.sendMessage(plugin.msg("sent", "player", p.targetName(), "reason", p.reasonName()));
        manager.notifyStaff(report);
        if (!wasFlagged && manager.isFlagged(p.targetId())) {
            manager.notifyFlag(p.targetName());
        }
    }

    /** Убирает цветовые коды и лишние пробелы, чтобы игрок не мог раскрасить лор. */
    private String sanitize(String s) {
        return s.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "")
                .replace("§", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
    }
}
