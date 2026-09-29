package com.ultras.bans.punishment;

import com.ultras.bans.UltrasBansPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralised real-world effect of a punishment: kicking banned players with the boxed screen,
 * notifying muted/warned players, and broadcasting to staff. Registered as a PunishmentHook so
 * commands, GUIs, the proxy bridge and the API all get identical behaviour.
 * Freeze/Jail/Vanish effects are handled by their own services (listener phase).
 */
public final class PunishmentEnforcer implements PunishmentHook {

    private final UltrasBansPlugin plugin;

    public PunishmentEnforcer(UltrasBansPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onApplied(PunishmentRecord r) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            switch (r.type()) {
                case BAN -> {
                    Player p = r.playerUuid() == null ? null : Bukkit.getPlayer(r.playerUuid());
                    if (p != null) p.kickPlayer(plugin.messages().banScreen(r));
                }
                case IP_BAN -> {
                    List<Player> matches = new ArrayList<>();
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getAddress() != null && p.getAddress().getAddress() != null
                                && p.getAddress().getAddress().getHostAddress().equals(r.ip())) matches.add(p);
                    }
                    for (Player p : matches) p.kickPlayer(plugin.messages().ipBanScreen(r));
                }
                case KICK -> {
                    Player p = r.playerUuid() == null ? null : Bukkit.getPlayer(r.playerUuid());
                    if (p != null) p.kickPlayer(plugin.messages().kickScreen(r.reason()));
                }
                case MUTE -> {
                    Player p = r.playerUuid() == null ? null : Bukkit.getPlayer(r.playerUuid());
                    if (p != null) p.sendMessage(plugin.messages().muteNotify(r));
                }
                case WARN -> {
                    Player p = r.playerUuid() == null ? null : Bukkit.getPlayer(r.playerUuid());
                    if (p != null) p.sendMessage(plugin.lang().getNamed("warnings.warn-issued", java.util.Map.of("reason", String.valueOf(r.reason()))));
                }
                default -> { }
            }
            broadcast(r);
        });
    }

    @Override
    public void onLifted(PunishmentRecord r) {
        // Lift notifications are sent by the commands/GUIs that trigger them; nothing global to enforce.
    }

    private void broadcast(PunishmentRecord r) {
        if (!plugin.mainConfig().broadcastPunishmentsToStaff()) return;
        if (r.type() == PunishmentType.VANISH || r.type() == PunishmentType.FREEZE || r.type() == PunishmentType.JAIL) return;
        String msg = "\n" + plugin.messages().broadcast(r) + "\n";
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("ultrasbans.admin") || p.isOp()) p.sendMessage(msg);
        }
        Bukkit.getConsoleSender().sendMessage(msg);
    }
}
