package com.ultras.bans.manager;

import com.ultras.bans.UltrasBansPlugin;
import com.ultras.bans.punishment.PunishmentRecord;
import com.ultras.bans.punishment.PunishmentStatus;
import com.ultras.bans.punishment.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

/**
 * Cross-server punishment sync for a proxy network (spec section 54). The shared MySQL/MariaDB database is the
 * single source of truth; this periodically reconciles the LOCAL in-memory cache against it, so a ban/mute/
 * freeze/jail/vanish applied on Server A is picked up on Server B within one poll interval - including kicking
 * an online player the moment their ban/mute record appears, and lifting local state the moment it's resolved
 * elsewhere. Runs only when features.proxy-sync is enabled and the database is MySQL (SQLite is a local file
 * and cannot be shared between server processes).
 */
public final class ProxySyncService {

    private final UltrasBansPlugin plugin;
    private BukkitTask task;

    public ProxySyncService(UltrasBansPlugin plugin) {
        this.plugin = plugin;
    }

    public void start(long intervalTicks) {
        stop();
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::syncOnce, intervalTicks, intervalTicks);
        plugin.getLogger().info("Proxy sync started (every " + (intervalTicks / 20) + "s).");
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    private void syncOnce() {
        try {
            var active = plugin.punishmentServiceImpl() == null ? null : plugin.punishmentRepositoryForSync().findAllActive().join();
            if (active == null) return;

            Set<Long> stillActiveIds = new HashSet<>();
            for (PunishmentRecord r : active) {
                stillActiveIds.add(r.id());
                boolean wasCached = r.playerUuid() != null
                        ? plugin.punishmentCache().isActive(r.playerUuid(), r.type())
                        : r.ip() != null && plugin.punishmentCache().isIpActive(r.ip(), r.type());
                plugin.punishmentCache().putActive(r);
                if (!wasCached) enforceIfOnline(r);
            }
            // Anything the cache still thinks is active but is no longer in the DB's active set was lifted elsewhere.
            purgeStale(stillActiveIds);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Proxy sync pass failed", ex);
        }
    }

    private void purgeStale(Set<Long> stillActiveIds) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (PunishmentType type : new PunishmentType[]{PunishmentType.BAN, PunishmentType.MUTE,
                    PunishmentType.FREEZE, PunishmentType.JAIL, PunishmentType.VANISH}) {
                PunishmentRecord cached = plugin.punishmentCache().getActive(p.getUniqueId(), type);
                if (cached != null && !stillActiveIds.contains(cached.id())) {
                    // Re-check by id before trusting a stale snapshot, then release it locally.
                    PunishmentRecord fresh = plugin.punishmentRepositoryForSync().findById(cached.id()).join();
                    if (fresh == null || fresh.status() != PunishmentStatus.ACTIVE) {
                        plugin.punishmentCache().removeActive(cached);
                        Bukkit.getScheduler().runTask(plugin, () -> releaseLocally(p, type));
                    }
                }
            }
        }
    }

    private void releaseLocally(Player p, PunishmentType type) {
        switch (type) {
            case FREEZE -> p.sendMessage(plugin.lang().get("freeze.unfrozen-notify"));
            case JAIL -> { /* JailService listens for the punishment hook; direct cache purge here just unblocks restrictions. */ }
            case VANISH -> plugin.vanish().forceShow(p);
            default -> { }
        }
    }

    private void enforceIfOnline(PunishmentRecord r) {
        if (r.playerUuid() == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(r.playerUuid());
            if (p == null) return;
            switch (r.type()) {
                case BAN -> p.kickPlayer(plugin.messages().banScreen(r));
                case MUTE -> p.sendMessage(plugin.messages().muteNotify(r));
                default -> { }
            }
        });
    }
}
