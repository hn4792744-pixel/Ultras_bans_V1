package com.ultras.bans.manager;

import com.ultras.bans.UltrasBansPlugin;
import com.ultras.bans.punishment.PunishmentHook;
import com.ultras.bans.punishment.PunishmentRecord;
import com.ultras.bans.punishment.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Vanish via Bukkit hidePlayer: the entity (armor, potion effects, particles) AND its tab-list entry are
 * removed for every viewer lacking ultrasbans.vanish.see. Mobs stop targeting vanished players and their
 * join/quit messages are suppressed. State comes from the punishment cache (record type VANISH, ACTIVE).
 */
public final class VanishService implements PunishmentHook, Listener {

    private final UltrasBansPlugin plugin;

    public VanishService(UltrasBansPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isVanished(Player p) {
        return plugin.punishmentCache().isActive(p.getUniqueId(), PunishmentType.VANISH);
    }

    private boolean canSee(Player viewer) {
        return viewer.hasPermission("ultrasbans.vanish.see") || viewer.isOp();
    }

    @Override
    public void onApplied(PunishmentRecord r) {
        if (r.type() != PunishmentType.VANISH || r.playerUuid() == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player target = Bukkit.getPlayer(r.playerUuid());
            if (target != null) hide(target);
        });
    }

    @Override
    public void onLifted(PunishmentRecord r) {
        if (r.type() != PunishmentType.VANISH || r.playerUuid() == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player target = Bukkit.getPlayer(r.playerUuid());
            if (target != null) show(target);
        });
    }

    private void hide(Player target) {
        target.setCollidable(false);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target)) continue;
            if (!canSee(viewer)) viewer.hidePlayer(plugin, target);
        }
    }

    /** Public entry point for external callers (e.g. ProxySyncService) that need to un-hide a player without a punishment record. */
    public void forceShow(Player target) {
        show(target);
    }

    private void show(Player target) {
        target.setCollidable(true);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(target)) viewer.showPlayer(plugin, target);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    @SuppressWarnings("deprecation")
    public void onJoin(PlayerJoinEvent e) {
        Player joiner = e.getPlayer();
        if (isVanished(joiner)) {
            e.setJoinMessage(null);
            hide(joiner);
        }
        if (!canSee(joiner)) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(joiner) && isVanished(other)) joiner.hidePlayer(plugin, other);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    @SuppressWarnings("deprecation")
    public void onQuit(PlayerQuitEvent e) {
        if (isVanished(e.getPlayer())) e.setQuitMessage(null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent e) {
        if (e.getTarget() instanceof Player p && isVanished(p)) e.setCancelled(true);
    }
}
