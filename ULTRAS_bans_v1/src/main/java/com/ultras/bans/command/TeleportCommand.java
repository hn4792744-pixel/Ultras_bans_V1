package com.ultras.bans.command;

import com.ultras.bans.UltrasBansPlugin;
import com.ultras.bans.gui.PlayerSelectAction;
import com.ultras.bans.punishment.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /tp [player|all] and /tphere [player|all]. Both "all" forms bring every other online player to the
 * staff member (there is no meaningful "teleport to all"); gated by config defaults.teleport-all-enabled
 * and ultrasbans.teleport.all. Optionally auto-vanishes staff (features.auto-vanish-on-teleport).
 */
public final class TeleportCommand extends UltrasCommandBase {

    public TeleportCommand(UltrasBansPlugin plugin) { super(plugin, "ultrasbans.teleport"); }

    @Override
    protected void execute(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player staff)) { send(sender, "errors.player-only"); return; }
        boolean here = command.getName().equalsIgnoreCase("tphere");

        if (args.length == 0) {
            plugin.gui().openPlayerSelect(staff, PlayerSelectAction.TELEPORT);
            return;
        }

        if (args[0].equalsIgnoreCase("all")) {
            if (!plugin.mainConfig().teleportAllEnabled()) { send(sender, "admin.teleport-all-disabled"); return; }
            if (!sender.hasPermission("ultrasbans.teleport.all")) { send(sender, "errors.no-permission"); return; }
            autoVanish(staff);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(staff)) p.teleportAsync(staff.getLocation());
            }
            send(sender, "admin.bring-success", "all");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { send(sender, "errors.player-offline"); return; }
        autoVanish(staff);
        if (here) {
            target.teleportAsync(staff.getLocation());
            send(sender, "admin.bring-success", target.getName());
        } else {
            staff.teleportAsync(target.getLocation());
            send(sender, "admin.teleport-success", target.getName());
        }
    }

    private void autoVanish(Player staff) {
        if (!plugin.mainConfig().autoVanishOnTeleport()) return;
        if (plugin.punishmentService().isCachedActive(staff.getUniqueId(), PunishmentType.VANISH)) return;
        plugin.punishmentService().setVanish(staff.getUniqueId(), staff.getName(), true,
                operator(staff), source(staff), "Auto-vanish on teleport");
    }

    @Override
    protected List<String> complete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) return List.of();
        List<String> out = new ArrayList<>(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        if (plugin.mainConfig().teleportAllEnabled() && sender.hasPermission("ultrasbans.teleport.all")) out.add("all");
        return filter(out, args[0]);
    }
}
