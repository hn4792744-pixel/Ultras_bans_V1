package com.ultras.bans.listener;

import com.ultras.bans.UltrasBansPlugin;
import com.ultras.bans.model.PlayerPlatform;
import com.ultras.bans.model.PlayerProfile;
import com.ultras.bans.punishment.PunishmentRecord;
import com.ultras.bans.punishment.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Login enforcement (BAN / IP_BAN via cache, no DB call on the login thread), chat mute enforcement,
 * and player profile tracking (IP, platform, rank, OP, session times, last location, playtime).
 * Bedrock players (Geyser/Floodgate) are detected by the Floodgate UUID prefix (all-zero most significant
 * bits) which needs no hard dependency on the Floodgate API.
 */
public final class PlayerConnectionListener implements Listener {

    private final UltrasBansPlugin plugin;
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessionStart = new ConcurrentHashMap<>();

    public PlayerConnectionListener(UltrasBansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        PunishmentRecord ban = plugin.punishmentCache().getActive(e.getUniqueId(), PunishmentType.BAN);
        if (ban != null) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, plugin.messages().banScreen(ban));
            return;
        }
        String ip = e.getAddress().getHostAddress();
        PunishmentRecord ipBan = plugin.punishmentCache().getActiveByIp(ip, PunishmentType.IP_BAN);
        if (ipBan != null) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, plugin.messages().ipBanScreen(ipBan));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        sessionStart.put(p.getUniqueId(), now);
        plugin.playerNameCache().add(p.getName());

        plugin.playerRepository().findByUuid(p.getUniqueId()).thenAccept(existing -> {
            PlayerProfile profile = existing != null ? existing : new PlayerProfile(p.getUniqueId(), p.getName());
            if (existing == null) profile.firstJoin(now);
            profile.username(p.getName());
            profile.lastJoin(now);
            profile.platform(isBedrock(p.getUniqueId()) ? PlayerPlatform.BEDROCK : PlayerPlatform.JAVA);
            profile.opStatus(p.isOp());
            profile.rank(resolveRank(p));
            if (p.getAddress() != null && p.getAddress().getAddress() != null) {
                profile.lastIp(p.getAddress().getAddress().getHostAddress());
            }
            profile.lastServer(plugin.mainConfig().serverId());
            Location l = p.getLocation();
            profile.lastWorld(l.getWorld() == null ? "unknown" : l.getWorld().getName());
            profile.lastLocation(l.getX(), l.getY(), l.getZ());
            profiles.put(p.getUniqueId(), profile);
            plugin.playerRepository().upsert(profile);
            if (plugin.permissions() != null) plugin.permissions().apply(p);
            if (plugin.discord() != null && plugin.discord().enabled()) {
                plugin.discord().sendJoin(p.getName(), profile.platform().name(), plugin.mainConfig().serverId(), profile.rank());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        PlayerProfile profile = profiles.remove(p.getUniqueId());
        Long start = sessionStart.remove(p.getUniqueId());
        if (profile == null) return;
        profile.lastQuit(now);
        if (start != null) profile.addPlaytime(now - start);
        Location l = p.getLocation();
        profile.lastWorld(l.getWorld() == null ? "unknown" : l.getWorld().getName());
        profile.lastLocation(l.getX(), l.getY(), l.getZ());
        profile.opStatus(p.isOp());
        plugin.playerRepository().upsert(profile);
        if (plugin.discord() != null && plugin.discord().enabled() && start != null) {
            plugin.discord().sendQuit(p.getName(), plugin.mainConfig().serverId(),
                    com.ultras.bans.util.TimeUtil.formatDuration(now - start));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void onChat(AsyncPlayerChatEvent e) {
        PunishmentRecord mute = plugin.punishmentCache().getActive(e.getPlayer().getUniqueId(), PunishmentType.MUTE);
        if (mute == null) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(plugin.messages().muteBlockedChat(mute));
    }

    /** Live profile for an online player (used by GUIs); null when offline. */
    public PlayerProfile liveProfile(UUID uuid) {
        return profiles.get(uuid);
    }

    /** Flushes all online profiles synchronously-safe (async repo) - called on plugin disable. */
    public void flushAll() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerProfile profile = profiles.get(p.getUniqueId());
            Long start = sessionStart.get(p.getUniqueId());
            if (profile == null) continue;
            if (start != null) { profile.addPlaytime(now - start); sessionStart.put(p.getUniqueId(), now); }
            plugin.playerRepository().upsert(profile);
        }
    }

    private static boolean isBedrock(UUID uuid) {
        return uuid.getMostSignificantBits() == 0L;
    }

    private String resolveRank(Player p) {
        // Highest matching "ultrasbans.rank.<name>" permission wins; falls back to "default".
        for (var perm : p.getEffectivePermissions()) {
            String n = perm.getPermission();
            if (perm.getValue() && n.startsWith("ultrasbans.rank.")) return n.substring("ultrasbans.rank.".length());
        }
        return p.isOp() ? "op" : "default";
    }
}
