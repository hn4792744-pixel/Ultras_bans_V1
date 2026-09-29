package com.ultras.bans.manager;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class SecurityConfig {

    public record Threshold(int actions, int windowSeconds) { }

    private final boolean enabled;
    private final Map<String, Threshold> thresholds = new HashMap<>();
    private final boolean alertStaff, discordAlert, logEvent;
    private final int repeatViolationsBeforeLockdown, escalationWindowMinutes;
    private final boolean removeOp, removePermissions, removeRank, setDefaultRank, lockAccount, notifyAdmins;
    private final String defaultRank;

    private SecurityConfig(YamlConfiguration y) {
        this.enabled = y.getBoolean("enabled", true);
        var t = y.getConfigurationSection("thresholds");
        if (t != null) {
            for (String key : t.getKeys(false)) {
                var s = t.getConfigurationSection(key);
                if (s != null) thresholds.put(key.toLowerCase(), new Threshold(s.getInt("actions", 5), s.getInt("window", 20)));
            }
        }
        this.alertStaff = y.getBoolean("on-detection.alert-staff", true);
        this.discordAlert = y.getBoolean("on-detection.discord-alert", true);
        this.logEvent = y.getBoolean("on-detection.log-security-event", true);
        this.repeatViolationsBeforeLockdown = y.getInt("escalation.repeat-violations-before-lockdown", 3);
        this.escalationWindowMinutes = y.getInt("escalation.escalation-window-minutes", 30);
        this.removeOp = y.getBoolean("escalation.lockdown.remove-op", true);
        this.removePermissions = y.getBoolean("escalation.lockdown.remove-plugin-permissions", true);
        this.removeRank = y.getBoolean("escalation.lockdown.remove-configured-rank", true);
        this.setDefaultRank = y.getBoolean("escalation.lockdown.set-default-rank", true);
        this.lockAccount = y.getBoolean("escalation.lockdown.lock-account", true);
        this.notifyAdmins = y.getBoolean("escalation.lockdown.notify-administrators", true);
        this.defaultRank = y.getString("default-rank", "default");
    }

    public static SecurityConfig load(File file) {
        return new SecurityConfig(YamlConfiguration.loadConfiguration(file));
    }

    public boolean enabled() { return enabled; }
    public Threshold threshold(String actionType) { return thresholds.get(actionType.toLowerCase()); }
    public boolean alertStaff() { return alertStaff; }
    public boolean discordAlert() { return discordAlert; }
    public boolean logEvent() { return logEvent; }
    public int repeatViolationsBeforeLockdown() { return repeatViolationsBeforeLockdown; }
    public int escalationWindowMinutes() { return escalationWindowMinutes; }
    public boolean removeOp() { return removeOp; }
    public boolean removePermissions() { return removePermissions; }
    public boolean removeRank() { return removeRank; }
    public boolean setDefaultRank() { return setDefaultRank; }
    public boolean lockAccount() { return lockAccount; }
    public boolean notifyAdmins() { return notifyAdmins; }
    public String defaultRank() { return defaultRank; }
}
