package com.ultras.bans.manager;

import com.ultras.bans.UltrasBansPlugin;
import com.ultras.bans.punishment.PunishmentHook;
import com.ultras.bans.punishment.PunishmentRecord;
import com.ultras.bans.punishment.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces FREEZE: no movement/jump, no commands, no interaction, no item drop, no damage in/out, no
 * teleport escapes. State comes from the punishment cache (O(1), no DB). Frozen players stay frozen across
 * relogs. After death the respawn point is the freeze anchor and is re-checked 3s later. Unfreezing NEVER
 * teleports the player back to the anchor.
 */
public final class FreezeService implements PunishmentHook, Listener {

    private final UltrasBansPlugin plugin;
    private final Map<UUID, Location> anchors = new ConcurrentHashMap<>();

    public FreezeService(UltrasBansPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean frozen(Player p) {
        return plugin.punishmentCache().isActive(p.getUniqueId(), PunishmentType.FREEZE);
    }

    // ---- hook ----

    @Override
    public void onApplied(PunishmentRecord r) {
        if (r.type() != PunishmentType.FREEZE || r.playerUuid() == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(r.playerUuid());
            if (p == null) return;
            anchors.put(p.getUniqueId(), p.getLocation().clone());
            p.sendMessage(plugin.lang().get("freeze.frozen-notify"));
        });
    }

    @Override
    public void onLifted(PunishmentRecord r) {
        if (r.type() != PunishmentType.FREEZE || r.playerUuid() == null) return;
        anchors.remove(r.playerUuid());
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(r.playerUuid());
            if (p != null) p.sendMessage(plugin.lang().get("freeze.unfrozen-notify"));
        });
    }

    // ---- listeners ----

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!frozen(p)) return;
        anchors.put(p.getUniqueId(), p.getLocation().clone());
        p.sendMessage(plugin.lang().get("freeze.frozen-notify"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        anchors.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom(), to = e.getTo();
        if (to == null) return;
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return; // head turn only
        if (!frozen(e.getPlayer())) return;
        Location back = from.clone();
        back.setYaw(to.getYaw());
        back.setPitch(to.getPitch());
        e.setTo(back);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (!frozen(e.getPlayer())) return;
        PlayerTeleportEvent.TeleportCause c = e.getCause();
        // Block self-escape causes; allow PLUGIN/COMMAND so staff (and this plugin) can still move a frozen player.
        if (c == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || c == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT
                || c == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL || c == PlayerTeleportEvent.TeleportCause.END_PORTAL
                || c == PlayerTeleportEvent.TeleportCause.END_GATEWAY) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!frozen(e.getPlayer())) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(plugin.lang().get("freeze.frozen-blocked-command"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) { block(e.getPlayer(), e); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) { block(e.getPlayer(), e); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) { block(e.getPlayer(), e); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { block(e.getPlayer(), e); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { block(e.getPlayer(), e); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && frozen(p) && !plugin.isPluginGui(e.getInventory())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && frozen(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && frozen(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!frozen(p)) return;
        UUID id = p.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player online = Bukkit.getPlayer(id);
            Location anchor = anchors.get(id);
            if (online != null && online.isOnline() && anchor != null && frozen(online)) online.teleportAsync(anchor);
        }, 60L); // 3 seconds
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Location anchor = anchors.get(e.getPlayer().getUniqueId());
        if (anchor != null && frozen(e.getPlayer())) e.setRespawnLocation(anchor);
    }

    private void block(Player p, org.bukkit.event.Cancellable e) {
        if (!frozen(p)) return;
        e.setCancelled(true);
        p.sendMessage(plugin.lang().get("freeze.frozen-blocked-action"));
    }
}
