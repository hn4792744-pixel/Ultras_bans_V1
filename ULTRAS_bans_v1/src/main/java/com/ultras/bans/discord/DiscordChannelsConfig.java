package com.ultras.bans.discord;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class DiscordChannelsConfig {

    private final Map<String, String> webhooks = new HashMap<>();
    private final Map<String, Boolean> enabled = new HashMap<>();

    public static DiscordChannelsConfig load(File file) {
        DiscordChannelsConfig c = new DiscordChannelsConfig();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        var wh = y.getConfigurationSection("webhooks");
        if (wh != null) for (String k : wh.getKeys(false)) c.webhooks.put(category(k), wh.getString(k, ""));
        var en = y.getConfigurationSection("enabled");
        if (en != null) for (String k : en.getKeys(false)) c.enabled.put(k.toLowerCase(Locale.ROOT), en.getBoolean(k, true));
        return c;
    }

    private static String category(String webhookKey) {
        // "ban-channel" -> "ban"
        String k = webhookKey.toLowerCase(Locale.ROOT);
        return k.endsWith("-channel") ? k.substring(0, k.length() - "-channel".length()) : k;
    }

    public boolean isEnabled(String category) {
        return enabled.getOrDefault(category.toLowerCase(Locale.ROOT), true) && hasWebhook(category);
    }

    public boolean hasWebhook(String category) {
        String url = webhooks.get(category.toLowerCase(Locale.ROOT));
        return url != null && !url.isBlank();
    }

    public String webhook(String category) {
        return webhooks.get(category.toLowerCase(Locale.ROOT));
    }
}
