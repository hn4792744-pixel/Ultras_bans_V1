package com.ultras.bans.command;

import com.ultras.bans.UltrasBansPlugin;
import com.ultras.bans.gui.PlayerSelectAction;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** /kick [player] [reason]. Only online players can be kicked. */
public final class KickCommand extends UltrasCommandBase {

    public KickCommand(UltrasBansPlugin plugin) { super(plugin, "ultrasbans.kick"); }

    @Override
    protected void execute(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) { send(sender, "errors.player-only"); return; }
            plugin.gui().openPlayerSelect(p, PlayerSelectAction.KICK);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { send(sender, "errors.player-offline"); return; }
        if (sender instanceof Player self && self.getUniqueId().equals(target.getUniqueId())) {
            send(sender, "punishments.self-action-blocked"); return;
        }
        if (plugin.mainConfig().opProtection() && target.isOp() && sender instanceof Player
                && !sender.hasPermission("ultrasbans.bypass.op-protect")) {
            send(sender, "punishments.op-protected"); return;
        }
        String reason = args.length > 1 ? joinFrom(args, 1) : "No reason specified";
        plugin.punishmentService().kick(target.getUniqueId(), target.getName(), reason, operator(sender), source(sender), location(sender))
                .thenAccept(r -> sync(() -> sendResult(sender, r)));
    }

    @Override
    protected List<String> complete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) return List.of();
        return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[0]);
    }
}
