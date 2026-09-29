package com.ultras.bans.command;

import com.ultras.bans.UltrasBansPlugin;
import com.ultras.bans.gui.PlayerSelectAction;
import com.ultras.bans.punishment.PunishmentType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /warn [player] [reason] | /warn list | /warn logs [player]. */
public final class WarnCommand extends UltrasCommandBase {

    public WarnCommand(UltrasBansPlugin plugin) { super(plugin, "ultrasbans.warn"); }

    @Override
    protected void execute(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) { send(sender, "errors.player-only"); return; }
            plugin.gui().openPlayerSelect(p, PlayerSelectAction.WARN);
            return;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("list")) {
            if (!(sender instanceof Player p)) { send(sender, "errors.player-only"); return; }
            plugin.gui().openPlayerSelect(p, PlayerSelectAction.WARN_LIST);
            return;
        }
        if (sub.equals("logs")) {
            if (args.length < 2) {
                if (!(sender instanceof Player p)) { send(sender, "errors.player-only"); return; }
                plugin.gui().openPlayerSelect(p, PlayerSelectAction.LOGS_WARN);
                return;
            }
            plugin.targets().resolve(args[1], false).thenAccept(t -> sync(() -> {
                if (t == null) { send(sender, "errors.player-not-found", args[1]); return; }
                if (sender instanceof Player p) { plugin.gui().openLogs(p, t.uuid(), t.name(), PunishmentType.WARN); return; }
                plugin.punishmentService().logsOf(t.uuid(), PunishmentType.WARN, 20).thenAccept(list -> sync(() -> {
                    if (list.isEmpty()) send(sender, "commands.no-logs-found");
                    list.forEach(r -> sender.sendMessage(SlotPunishmentCommand.formatLine(r)));
                }));
            }));
            return;
        }
        String reason = args.length > 1 ? joinFrom(args, 1) : "No reason specified";
        plugin.targets().resolve(args[0], false).thenAccept(t -> sync(() -> {
            if (t == null) { send(sender, "errors.player-not-found", args[0]); return; }
            plugin.punishmentService().warn(t.uuid(), t.name(), null, reason, operator(sender), source(sender), location(sender))
                    .thenAccept(r -> sync(() -> {
                        sendResult(sender, r);
                    }));
        }));
    }

    @Override
    protected List<String> complete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>(filter(List.of("list", "logs"), args[0]));
            out.addAll(playerNames(args[0]));
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("logs")) return playerNames(args[1]);
        return List.of();
    }
}
