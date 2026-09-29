package com.ultras.bans.manager;

import com.ultras.bans.UltrasBansPlugin;
import com.ultras.bans.database.SecurityLogRepositoryJdbc;
import com.ultras.bans.punishment.PunishmentHook;
import com.ultras.bans.punishment.PunishmentRecord;
import com.ultras.bans.punishment.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mass-Punishment Protection (spec section 45). Tracks a sliding window of punishment actions per staff member
 * per action type; when the configured threshold is exceeded it alerts staff/Discord/log, and if the SAME staff
 * member trips detection repeatedly within the escalation window, applies the configured lockdown response.
 * All state is in-memory (per-session) except the permanent audit trail in security_logs.
 */
public final class SecurityService implements PunishmentHook {

    private final UltrasBansPlugin plugin;
    private final SecurityLogRepositoryJdbc repo;
    private volatile SecurityConfig config;

    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>(); // key: uuid|type
    private final Map<UUID, Deque<Long>> violations = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> locked = ConcurrentHashMap.newKeySet();

    public SecurityService(UltrasBansPlugin plugin, SecurityLogRepositoryJdbc repo) {
        this.plugin = plugin;
        this.repo = repo;
        reloadConfig();
    }

    public void reloadConfig() {
        File f = new File(plugin.getDataFolder(), "security.yml");
        if (!f.exists()) plugin.saveResource("security.yml", false);
        config = SecurityConfig.load(f);
    }

    public boolean isLocked(UUID uuid) { return locked.contains(uuid); }

    @Override
    public void onApplied(PunishmentRecord r) {
        if (!config.enabled() || r.operatorUuid() == null) return;
        String type = actionKey(r.type());
        if (type == null) return;
        SecurityConfig.Threshold threshold = config.threshold(type);
        if (threshold == null) return;

        long now = System.currentTimeMillis();
        String key = r.operatorUuid() + "|" + type;
        Deque<Long> dq = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (dq) {
            dq.addLast(now);
            long windowStart = now - threshold.windowSeconds() * 1000L;
            while (!dq.isEmpty() && dq.peekFirst() < windowStart) dq.pollFirst();
            if (dq.size() >= threshold.actions()) {
                dq.clear();
                onDetected(r.operatorUuid(), r.operatorName(), type, threshold);
            }
        }
    }

    @Override
    public void onLifted(PunishmentRecord record) { /* only new punishments count toward abuse detection */ }

    private void onDetected(UUID staff, String staffName, String type, SecurityConfig.Threshold threshold) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            String response = "Alerted";
            if (config.alertStaff()) alertStaff(staffName, type, threshold);
            if (config.discordAlert() && plugin.discord() != null && plugin.discord().enabled()) {
                plugin.discord().sendSecurity(staffName, type.toUpperCase(), threshold.actions(), threshold.windowSeconds() + "s", "Alert sent");
            }

            boolean lockdown = registerViolationAndCheckEscalation(staff);
            if (lockdown) {
                response = "Lockdown applied";
                applyLockdown(staff, staffName);
            }
            if (config.logEvent()) repo.log(staff, staffName, type, threshold.actions(), threshold.windowSeconds(), response);
        });
    }

    private boolean registerViolationAndCheckEscalation(UUID staff) {
        long now = System.currentTimeMillis();
        Deque<Long> dq = violations.computeIfAbsent(staff, k -> new ArrayDeque<>());
        synchronized (dq) {
            dq.addLast(now);
            long windowStart = now - config.escalationWindowMinutes() * 60_000L;
            while (!dq.isEmpty() && dq.peekFirst() < windowStart) dq.pollFirst();
            return dq.size() >= config.repeatViolationsBeforeLockdown();
        }
    }

    private void alertStaff(String staffName, String type, SecurityConfig.Threshold threshold) {
        String msg = plugin.lang().get("punishments.security-blocked");
        String detail = "§c⚠ §fSecurity: §c" + staffName + " §fexceeded §c" + threshold.actions()
                + " " + type.toUpperCase() + " §factions in §c" + threshold.windowSeconds() + "s§f.";
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("ultrasbans.admin") || p.isOp()) p.sendMessage(detail);
        }
        Bukkit.getConsoleSender().sendMessage(detail);
    }

    private void applyLockdown(UUID staff, String staffName) {
        var lockdownCfg = config;
        OfflinePlayer op = Bukkit.getOfflinePlayer(staff);

        if (lockdownCfg.removeOp() && op.isOp()) op.setOp(false);

        if (lockdownCfg.removePermissions() && plugin.permissions() != null) {
            for (var entry : plugin.permissions().commands()) plugin.permissions().setPlayer(staff, entry.key(), false);
        }

        if (lockdownCfg.setDefaultRank() || lockdownCfg.removeRank()) {
            var profile = plugin.connections() == null ? null : plugin.connections().liveProfile(staff);
            if (profile != null) {
                profile.rank(lockdownCfg.defaultRank());
                plugin.playerRepository().upsert(profile);
                Player online = Bukkit.getPlayer(staff);
                if (online != null && plugin.permissions() != null) plugin.permissions().apply(online);
            }
        }

        if (lockdownCfg.lockAccount()) {
            locked.add(staff);
            Player online = Bukkit.getPlayer(staff);
            if (online != null) online.sendMessage(plugin.lang().get("punishments.security-blocked"));
        }

        if (lockdownCfg.notifyAdmins()) {
            String msg = "§4§l⚠ LOCKDOWN §c" + staffName + " §fhas been locked down by the security system for repeated abuse.";
            for (Player p : Bukkit.getOnlinePlayers()) if (p.hasPermission("ultrasbans.admin") || p.isOp()) p.sendMessage(msg);
            Bukkit.getConsoleSender().sendMessage(msg);
            if (plugin.discord() != null && plugin.discord().enabled()) {
                plugin.discord().sendSecurity(staffName, "LOCKDOWN", 0, "-", "Account locked, OP/rank/permissions revoked");
            }
        }
    }

    private static String actionKey(PunishmentType t) {
        return switch (t) {
            case KICK -> "kick"; case BAN -> "ban"; case IP_BAN -> "ipban";
            case MUTE -> "mute"; case JAIL -> "jail"; case FREEZE -> "freeze";
            default -> null;
        };
    }
}
