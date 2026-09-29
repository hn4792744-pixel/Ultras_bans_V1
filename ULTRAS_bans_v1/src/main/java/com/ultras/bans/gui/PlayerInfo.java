package com.ultras.bans.gui;

import com.ultras.bans.UltrasBansPlugin;
import com.ultras.bans.model.PlayerProfile;
import com.ultras.bans.punishment.PunishmentRecord;
import com.ultras.bans.punishment.PunishmentType;
import com.ultras.bans.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Builds the placeholder map describing a player (online or offline) for GUI items. */
public final class PlayerInfo {

    private PlayerInfo() {}

    public static Map<String, String> placeholders(UltrasBansPlugin plugin, PlayerProfile profile, UUID uuid, String name) {
        Map<String, String> ph = new HashMap<>();
        Player online = Bukkit.getPlayer(uuid);
        ph.put("name", name);
        ph.put("uuid", uuid.toString());
        ph.put("status", plugin.lang().get(online != null ? "player.online" : "player.offline"));
        String ip = profile == null ? null : profile.lastIp();
        if (online != null && online.getAddress() != null && online.getAddress().getAddress() != null) {
            ip = online.getAddress().getAddress().getHostAddress();
        }
        ph.put("ip", ip == null ? "-" : ip);
        ph.put("rank", profile == null ? "-" : profile.rank());
        ph.put("op", (online != null ? online.isOp() : profile != null && profile.opStatus()) ? "&aYes" : "&7No");
        ph.put("platform", profile == null ? "JAVA" : profile.platform().name());
        if (online != null) {
            Location l = online.getLocation();
            ph.put("server", plugin.mainConfig().serverId());
            ph.put("world", l.getWorld() == null ? "-" : l.getWorld().getName());
            ph.put("coords", String.format("%.0f / %.0f / %.0f", l.getX(), l.getY(), l.getZ()));
        } else if (profile != null) {
            ph.put("server", profile.lastServer());
            ph.put("world", profile.lastWorld());
            ph.put("coords", String.format("%.0f / %.0f / %.0f", profile.lastX(), profile.lastY(), profile.lastZ()));
        } else {
            ph.put("server", "-"); ph.put("world", "-"); ph.put("coords", "-");
        }
        ph.put("last_join", profile == null ? "-" : TimeUtil.formatAbsolute(profile.lastJoin()));
        ph.put("last_quit", profile == null ? "-" : TimeUtil.formatAbsolute(profile.lastQuit()));
        ph.put("playtime", profile == null ? "-" : TimeUtil.formatDuration(profile.playtimeMillis()));
        ph.put("punishments", activePunishments(plugin, uuid, ip));
        return ph;
    }

    /** Multi-line summary of currently ACTIVE punishments from the in-memory cache. */
    public static String activePunishments(UltrasBansPlugin plugin, UUID uuid, String ip) {
        StringBuilder sb = new StringBuilder();
        for (PunishmentType t : new PunishmentType[]{PunishmentType.BAN, PunishmentType.MUTE, PunishmentType.FREEZE,
                PunishmentType.JAIL, PunishmentType.VANISH}) {
            PunishmentRecord r = plugin.punishmentCache().getActive(uuid, t);
            if (r != null) append(sb, r);
        }
        if (ip != null) {
            PunishmentRecord r = plugin.punishmentCache().getActiveByIp(ip, PunishmentType.IP_BAN);
            if (r != null) append(sb, r);
        }
        return sb.length() == 0 ? "&7None" : sb.toString().stripTrailing();
    }

    private static void append(StringBuilder sb, PunishmentRecord r) {
        sb.append("&c").append(r.type().name()).append(" &8(&7")
                .append(r.permanent() ? "Permanent" : TimeUtil.formatDuration(r.remainingMillis(System.currentTimeMillis())))
                .append("&8)\n");
    }
}
