package com.ultras.bans.proxy.common;

import com.ultras.bans.punishment.PunishmentRecord;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Bukkit-free reader of lang/&lt;code&gt;/punishments.yml's ban-screen / ipban-screen templates, for the proxy
 * modules so a network-wide ban/IP-ban shows the exact same boxed message whether it's caught on a backend
 * server or at the proxy's own login stage. Returns raw '&'-coded legacy text; each proxy platform converts it
 * to its own Component/ChatColor type.
 */
public final class ProxyBanMessages {

    private final Map<String, Object> messages;
    private final String discordLink;

    @SuppressWarnings("unchecked")
    private ProxyBanMessages(File langFile, String discordLink) {
        Map<String, Object> loaded;
        try (InputStream in = new FileInputStream(langFile)) {
            Object o = new Yaml().load(in);
            loaded = o instanceof Map ? (Map<String, Object>) o : Map.of();
        } catch (Exception ex) {
            loaded = Map.of();
        }
        this.messages = loaded;
        this.discordLink = discordLink;
    }

    public static ProxyBanMessages load(File dataFolder, String language, String discordLink, Logger logger) {
        File dir = new File(new File(dataFolder, "lang"), language);
        File file = new File(dir, "punishments.yml");
        if (!file.exists()) {
            logger.warning("lang/" + language + "/punishments.yml not found on proxy; falling back to a plain message.");
        }
        return new ProxyBanMessages(file, discordLink);
    }

    public String banScreen(PunishmentRecord r) { return fill(str("ban-screen", defaultBan()), r); }

    public String ipBanScreen(PunishmentRecord r) { return fill(str("ipban-screen", defaultBan()), r); }

    private String fill(String template, PunishmentRecord r) {
        String reason = r.reason() == null || r.reason().isBlank() ? "No reason specified" : r.reason();
        String duration = r.permanent() ? "Permanent" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(r.expiresAt()));
        return template.replace("{reason}", reason)
                .replace("{duration}", duration)
                .replace("{id}", String.valueOf(r.id()))
                .replace("{discord}", discordLink == null ? "" : discordLink);
    }

    private String str(String key, String def) {
        Object v = messages.get(key);
        return v == null ? def : String.valueOf(v);
    }

    private static String defaultBan() {
        return "&cYou are banned.\n&7Reason: &f{reason}\n&7Duration: &f{duration}\n&7ID: &f#{id}\n&7Discord: &f{discord}";
    }
}
