package com.ultras.bans.discord;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public final class DiscordConfig {

    private final boolean enabled;
    private final String language;
    private final int rateLimitPerSecond;

    private DiscordConfig(boolean enabled, String language, int rateLimitPerSecond) {
        this.enabled = enabled;
        this.language = language;
        this.rateLimitPerSecond = rateLimitPerSecond;
    }

    public boolean enabled() { return enabled; }
    public String language() { return language; }
    public int rateLimitPerSecond() { return rateLimitPerSecond; }

    public static DiscordConfig load(File file) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        return new DiscordConfig(
                y.getBoolean("enabled", false),
                y.getString("language", "en"),
                Math.max(1, y.getInt("rate-limit-per-second", 4))
        );
    }
}
