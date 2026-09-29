package com.ultras.bans.punishment;

import com.ultras.bans.config.LanguageManager;
import com.ultras.bans.config.MainConfig;
import com.ultras.bans.util.TimeUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Fills the named placeholders ({reason}, {duration}, {id}, {discord}) in the
 * boxed disconnect-screen templates from lang/&lt;code&gt;/punishments.yml.
 * Used by the login/pre-login listener (BAN/IP_BAN) and the kick command.
 */
public final class PunishmentMessageFactory {

    private final LanguageManager lang;
    private final MainConfig config;

    public PunishmentMessageFactory(LanguageManager lang, MainConfig config) {
        this.lang = lang;
        this.config = config;
    }

    public String banScreen(PunishmentRecord record) {
        return screen("punishments.ban-screen", record);
    }

    public String ipBanScreen(PunishmentRecord record) {
        return screen("punishments.ipban-screen", record);
    }

    public String kickScreen(String reason) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("reason", reason == null || reason.isBlank() ? "No reason specified" : reason);
        return lang.getNamed("punishments.kick-screen", placeholders);
    }

    private String screen(String key, PunishmentRecord record) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("reason", record.reason() == null || record.reason().isBlank() ? "No reason specified" : record.reason());
        placeholders.put("duration", record.permanent() ? "Permanent" : TimeUtil.formatAbsolute(record.expiresAt()));
        placeholders.put("id", String.valueOf(record.id()));
        placeholders.put("discord", config.discordLink());
        return lang.getNamed(key, placeholders);
    }

    public String muteNotify(PunishmentRecord record) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("reason", record.reason());
        placeholders.put("duration", record.permanent() ? "Permanent" : TimeUtil.formatAbsolute(record.expiresAt()));
        return lang.getNamed("punishments.mute-notify", placeholders);
    }

    public String muteBlockedChat(PunishmentRecord record) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("duration", record.permanent() ? "Permanent" : TimeUtil.formatAbsolute(record.expiresAt()));
        return lang.getNamed("punishments.mute-blocked-chat", placeholders);
    }

    public String broadcast(PunishmentRecord record) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", record.playerName() != null ? record.playerName() : String.valueOf(record.ip()));
        placeholders.put("type", record.type().name());
        placeholders.put("reason", record.reason() == null ? "-" : record.reason());
        placeholders.put("operator", record.operatorName());
        placeholders.put("duration", record.permanent() ? "Permanent" : TimeUtil.formatDuration(record.remainingMillis(System.currentTimeMillis())));
        return lang.getNamed("punishments.broadcast-format", placeholders);
    }
}
