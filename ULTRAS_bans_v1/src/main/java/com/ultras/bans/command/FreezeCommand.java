package com.ultras.bans.command;

import com.ultras.bans.UltrasBansPlugin;
import com.ultras.bans.gui.PlayerSelectAction;
import com.ultras.bans.punishment.PunishmentRecord;
import com.ultras.bans.punishment.PunishmentStatus;
import com.ultras.bans.punishment.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /freeze (/frz) [player] | list | logs [player]. Effects are applied by FreezeService via the hook system. */
public final class FreezeCommand extends UltrasCommandBase {

    public FreezeCommand(UltrasBansPlugin plugin) { super(plugin, "ultrasbans.freeze"); }

    @Override
    protected void execute(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) { send(sender, "errors.player-only"); return; }
            plugin.gui().openPlayerSelect(p, PlayerSelectAction.FREEZE);
            return;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("list")) {
            plugin.punishmentService().recent(PunishmentType.FREEZE, 50).thenAccept(records -> sync(() -> {
                List<PunishmentRecord> active = records.stream().filter(r -> r.status() == PunishmentStatus.ACTIVE).toList();
                if (active.isEmpty()) send(sender, "gui.no-active-punishments");
                active.forEach(r -> sender.sendMessage(SlotPunishmentCommand.formatLine(r)));
            }));
            return;
        }
        if (sub.equals("logs")) {
            if (args.length < 2) {
                if (!(sender instanceof Player p)) { send(sender, "errors.player-only"); return; }
                plugin.gui().openPlayerSelect(p, PlayerSelectAction.LOGS_FREEZE);
                return;
            }
            plugin.targets().resolve(args[1], false).thenAccept(t -> sync(() -> {
                if (t == null) { send(sender, "errors.player-not-found", args[1]); return; }
                if (sender instanceof Player p) { plugin.gui().openLogs(p, t.uuid(), t.name(), PunishmentType.FREEZE); return; }
                plugin.punishmentService().logsOf(t.uuid(), PunishmentType.FREEZE, 20).thenAccept(list -> sync(() -> {
                    if (list.isEmpty()) send(sender, "commands.no-logs-found");
                    list.forEach(r -> sender.sendMessage(SlotPunishmentCommand.formatLine(r)));
                }));
            }));
            return;
        }
        plugin.targets().resolve(args[0], false).thenAccept(t -> sync(() -> {
            if (t == null) { send(sender, "errors.player-not-found", args[0]); return; }
            Player online = Bukkit.getPlayer(t.uuid());
            if (online != null && online.hasPermission("ultrasbans.bypass.freeze")) {
                send(sender, "punishments.op-protected"); return;
            }
            plugin.punishmentService().freeze(t.uuid(), t.name(), operator(sender), source(sender), location(sender))
                    .thenAccept(r -> sync(() -> sendResult(sender, r)));
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
