package com.ultras.bans.discord;

import com.ultras.bans.UltrasBansPlugin;
import com.ultras.bans.punishment.PunishmentHook;
import com.ultras.bans.punishment.PunishmentRecord;
import com.ultras.bans.punishment.PunishmentType;
import com.ultras.bans.util.TimeUtil;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * High-level Discord facade (spec sections 35-44). Formats an event with the language-appropriate embed
 * template, resolves which webhook category it belongs to, and hands off to {@link DiscordWebhookClient}.
 * Every category can be toggled independently in discord/channels.yml; secrets (webhook URLs) never touch
 * the Java source or logs - only the two on-disk YAML files the server owner edits themselves.
 */
public final class DiscordService implements PunishmentHook {

    private final UltrasBansPlugin plugin;
    private final DiscordWebhookClient client;
    private volatile DiscordConfig config;
    private volatile DiscordChannelsConfig channels;
    private volatile DiscordMessages messages;

    public DiscordService(UltrasBansPlugin plugin) {
        this.plugin = plugin;
        this.client = new DiscordWebhookClient(plugin.getLogger());
        reload();
    }

    public void reload() {
        File dir = new File(plugin.getDataFolder(), "discord");
        for (String res : new String[]{"config.yml", "channels.yml", "messages_en.yml", "messages_ar.yml"}) {
            File f = new File(dir, res);
            if (!f.exists() && plugin.getResource("discord/" + res) != null) plugin.saveResource("discord/" + res, false);
        }
        this.config = DiscordConfig.load(new File(dir, "config.yml"));
        this.channels = DiscordChannelsConfig.load(new File(dir, "channels.yml"));
        this.messages = DiscordMessages.load(dir, config.language());
        client.setRateLimitPerSecond(config.rateLimitPerSecond());
    }

    public boolean enabled() { return config.enabled(); }

    private void send(String category, String templateKey, Map<String, String> ph) {
        if (!config.enabled() || !channels.isEnabled(category)) return;
        var t = messages.get(templateKey);
        send(channels.webhook(category), t, ph);
    }

    private void send(String webhookUrl, DiscordMessages.Template t, Map<String, String> ph) {
        client.sendEmbed(webhookUrl, t.color(), fill(t.title(), ph), fill(t.description(), ph), fill(t.footer(), ph));
    }

    private static String fill(String s, Map<String, String> ph) {
        if (s == null) return "";
        for (var e : ph.entrySet()) s = s.replace("{" + e.getKey() + "}", e.getValue() == null ? "-" : e.getValue());
        return s;
    }

    // ---------------- punishment hook ----------------

    @Override
    public void onApplied(PunishmentRecord r) {
        if (r.type() == PunishmentType.KICK) { /* also logged as its own category below */ }
        Map<String, String> ph = punishmentPlaceholders(r);
        String category = categoryOf(r.type());
        if (category == null) return;
        send(category, "punishment", ph);
        if (r.type() != PunishmentType.WARN) send("admin", "punishment", ph);
    }

    @Override
    public void onLifted(PunishmentRecord r) {
        String category = categoryOf(r.type());
        if (category == null) return;
        send(category, "lift", punishmentPlaceholders(r));
    }

    private Map<String, String> punishmentPlaceholders(PunishmentRecord r) {
        Map<String, String> ph = new HashMap<>();
        ph.put("id", String.valueOf(r.id()));
        ph.put("type", r.type().name());
        ph.put("player", r.playerName() != null ? r.playerName() : String.valueOf(r.ip()));
        ph.put("uuid", r.playerUuid() == null ? "-" : r.playerUuid().toString());
        ph.put("ip", r.ip() == null ? "-" : r.ip());
        ph.put("reason", r.reason() == null ? "-" : r.reason());
        ph.put("duration", r.permanent() ? "Permanent" : TimeUtil.formatDuration(Math.max(0, r.expiresAt() - r.createdAt())));
        ph.put("operator", r.operatorName() == null ? "-" : r.operatorName());
        ph.put("server", r.server() == null ? "-" : r.server());
        ph.put("world", r.world() == null ? "-" : r.world());
        return ph;
    }

    private static String categoryOf(PunishmentType t) {
        return switch (t) {
            case BAN -> "ban";
            case IP_BAN -> "ipban";
            case MUTE -> "mute";
            case WARN -> "warn";
            case FREEZE -> "freeze";
            case JAIL -> "jail";
            case VANISH -> "vanish";
            case KICK -> "kick";
        };
    }

    // ---------------- other event categories ----------------

    public void sendJoin(String player, String platform, String server, String rank) {
        send("join", "join", Map.of("player", player, "platform", platform, "server", server, "rank", rank));
    }

    public void sendQuit(String player, String server, String sessionDuration) {
        send("quit", "quit", Map.of("player", player, "server", server, "duration", sessionDuration));
    }

    public void sendChat(String player, String server, String message) {
        send("chat", "chat", Map.of("player", player, "server", server, "message", message));
    }

    public void sendCommand(String player, String command, String result, String server) {
        send("commands", "commands", Map.of("player", player, "command", command, "result", result, "server", server));
    }

    public void sendConsole(String message) {
        send("console", "console", Map.of("message", message));
    }

    public void sendDeath(String player, String killer, String cause, String server, String world, String gamemode,
                          boolean vanished, boolean op) {
        send("death", "death", Map.of("player", player, "killer", killer, "cause", cause, "server", server,
                "world", world, "gamemode", gamemode, "vanished", vanished ? "Yes" : "No", "op", op ? "Yes" : "No"));
    }

    public void sendStatistics(String player, long blocksBroken, long blocksPlaced, long kills, long deaths,
                               String playtime, String distance, String gamemode, String server) {
        Map<String, String> ph = new HashMap<>();
        ph.put("player", player);
        ph.put("blocks_broken", String.valueOf(blocksBroken));
        ph.put("blocks_placed", String.valueOf(blocksPlaced));
        ph.put("kills", String.valueOf(kills));
        ph.put("deaths", String.valueOf(deaths));
        ph.put("playtime", playtime);
        ph.put("distance", distance);
        ph.put("gamemode", gamemode);
        ph.put("server", server);
        send("statistics", "statistics", ph);
    }

    public void sendAchievement(String player, String achievement) {
        send("achievements", "achievement", Map.of("player", player, "achievement", achievement));
    }

    public void sendSecurity(String staff, String action, int count, String window, String response) {
        send("admin", "security", Map.of("staff", staff, "action", action, "count", String.valueOf(count),
                "window", window, "response", response));
    }

    public void shutdown() {
        client.shutdown();
    }
}
