package com.ultras.bans.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public final class MainConfig {

    private final String language;
    private final String serverName;
    private final String serverId;
    private final boolean proxySync;
    private final boolean geyserSupport;
    private final boolean discordIntegration;
    private final boolean securitySystem;
    private final boolean vanishHideFromTab;
    private final boolean autoVanishOnTeleport;
    private final String discordLink;
    private final int maxTemporaryDurationDays;
    private final boolean broadcastPunishmentsToStaff;
    private final boolean opProtection;
    private final boolean teleportAllEnabled;

    private MainConfig(Builder b) {
        this.language = b.language;
        this.serverName = b.serverName;
        this.serverId = b.serverId;
        this.proxySync = b.proxySync;
        this.geyserSupport = b.geyserSupport;
        this.discordIntegration = b.discordIntegration;
        this.securitySystem = b.securitySystem;
        this.vanishHideFromTab = b.vanishHideFromTab;
        this.autoVanishOnTeleport = b.autoVanishOnTeleport;
        this.discordLink = b.discordLink;
        this.maxTemporaryDurationDays = b.maxTemporaryDurationDays;
        this.broadcastPunishmentsToStaff = b.broadcastPunishmentsToStaff;
        this.opProtection = b.opProtection;
        this.teleportAllEnabled = b.teleportAllEnabled;
    }

    public String language() { return language; }
    public String serverName() { return serverName; }
    public String serverId() { return serverId; }
    public boolean proxySync() { return proxySync; }
    public boolean geyserSupport() { return geyserSupport; }
    public boolean discordIntegration() { return discordIntegration; }
    public boolean securitySystem() { return securitySystem; }
    public boolean vanishHideFromTab() { return vanishHideFromTab; }
    public boolean autoVanishOnTeleport() { return autoVanishOnTeleport; }
    public String discordLink() { return discordLink; }
    public int maxTemporaryDurationDays() { return maxTemporaryDurationDays; }
    public boolean broadcastPunishmentsToStaff() { return broadcastPunishmentsToStaff; }
    public boolean opProtection() { return opProtection; }
    public boolean teleportAllEnabled() { return teleportAllEnabled; }

    public static MainConfig load(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Builder b = new Builder();
        b.language = yaml.getString("language", "en");
        b.serverName = yaml.getString("server-name", "ULTRAS");
        b.serverId = yaml.getString("server-id", "server-1");
        b.proxySync = yaml.getBoolean("features.proxy-sync", false);
        b.geyserSupport = yaml.getBoolean("features.geyser-support", true);
        b.discordIntegration = yaml.getBoolean("features.discord-integration", false);
        b.securitySystem = yaml.getBoolean("features.security-system", true);
        b.vanishHideFromTab = yaml.getBoolean("features.vanish-hide-from-tab", true);
        b.autoVanishOnTeleport = yaml.getBoolean("features.auto-vanish-on-teleport", false);
        b.discordLink = yaml.getString("links.discord", "https://discord.gg/ucx");
        b.maxTemporaryDurationDays = yaml.getInt("defaults.max-temporary-duration-days", 30);
        b.broadcastPunishmentsToStaff = yaml.getBoolean("defaults.broadcast-punishments-to-staff", true);
        b.opProtection = yaml.getBoolean("defaults.op-protection", true);
        b.teleportAllEnabled = yaml.getBoolean("defaults.teleport-all-enabled", true);
        return new MainConfig(b);
    }

    private static final class Builder {
        String language;
        String serverName;
        String serverId;
        boolean proxySync;
        boolean geyserSupport;
        boolean discordIntegration;
        boolean securitySystem;
        boolean vanishHideFromTab;
        boolean autoVanishOnTeleport;
        String discordLink;
        int maxTemporaryDurationDays;
        boolean broadcastPunishmentsToStaff;
        boolean opProtection;
        boolean teleportAllEnabled;
    }
}
