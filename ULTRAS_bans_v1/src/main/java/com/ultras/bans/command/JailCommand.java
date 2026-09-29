package com.ultras.bans.command;

import com.ultras.bans.UltrasBansPlugin;
import com.ultras.bans.gui.PlayerSelectAction;
import com.ultras.bans.model.JailLocation;
import com.ultras.bans.punishment.PunishmentRecord;
import com.ultras.bans.punishment.PunishmentStatus;
import com.ultras.bans.punishment.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /jail [player] [jailName] | list | logs [player] | setting [player]. Refuses to jail until a jail is set. */
public final class JailCommand extends UltrasCommandBase {

    public JailCommand(UltrasBansPlugin plugin) { super(plugin, "ultrasbans.jail"); }

    @Override
    protected void execute(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) { send(sender, "errors.player-only"); return; }
            if (!plugin.jail().hasAnyJail()) { send(sender, "jail.no-jail-set"); return; }
            plugin.gui().openPlayerSelect(p, PlayerSelectAction.JAIL);
            return;
        }
        switch (args[0].toLowerCase()) {
            case "list" -> { list(sender); return; }
            case "logs" -> { logs(sender, args); return; }
            case "setting", "settings" -> { settings(sender, args); return; }
            default -> { }
        }

        if (!plugin.jail().hasAnyJail()) { send(sender, "jail.no-jail-set"); return; }
        String jailName = args.length > 1 ? args[1] : null;
        if (jailName != null && plugin.jail().findJail(jailName) == null) { send(sender, "jail.no-jail-set"); return; }
        JailLocation jl = plugin.jail().findJail(jailName);

        plugin.targets().resolve(args[0], false).thenAccept(t -> sync(() -> {
            if (t == null) { send(sender, "errors.player-not-found", args[0]); return; }
            Player online = Bukkit.getPlayer(t.uuid());
            if (online != null && online.hasPermission("ultrasbans.bypass.jail")) { send(sender, "punishments.op-protected"); return; }
            plugin.punishmentService().jail(t.uuid(), t.name(), jl.name(), operator(sender), source(sender), location(sender))
                    .thenAccept(r -> sync(() -> sendResult(sender, r)));
        }));
    }

    private void list(CommandSender sender) {
        plugin.punishmentService().recent(PunishmentType.JAIL, 100).thenAccept(records -> sync(() -> {
            List<PunishmentRecord> active = records.stream().filter(r -> r.status() == PunishmentStatus.ACTIVE).toList();
            if (active.isEmpty()) send(sender, "gui.no-active-punishments");
            active.forEach(r -> sender.sendMessage(SlotPunishmentCommand.formatLine(r)));
        }));
    }

    private void logs(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (!(sender instanceof Player p)) { send(sender, "errors.player-only"); return; }
            plugin.gui().openPlayerSelect(p, PlayerSelectAction.LOGS_JAIL);
            return;
        }
        plugin.targets().resolve(args[1], false).thenAccept(t -> sync(() -> {
            if (t == null) { send(sender, "errors.player-not-found", args[1]); return; }
            if (sender instanceof Player p) { plugin.gui().openLogs(p, t.uuid(), t.name(), PunishmentType.JAIL); return; }
            plugin.punishmentService().logsOf(t.uuid(), PunishmentType.JAIL, 20).thenAccept(list -> sync(() -> {
                if (list.isEmpty()) send(sender, "commands.no-logs-found");
                list.forEach(r -> sender.sendMessage(SlotPunishmentCommand.formatLine(r)));
            }));
        }));
    }

    private void settings(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { send(sender, "errors.player-only"); return; }
        if (args.length < 2) { plugin.gui().openPlayerSelect(p, PlayerSelectAction.JAIL_SETTINGS); return; }
        plugin.targets().resolve(args[1], false).thenAccept(t -> sync(() -> {
            if (t == null) { send(sender, "errors.player-not-found", args[1]); return; }
            if (!plugin.jail().isJailed(t.uuid()) || plugin.jail().session(t.uuid()) == null) {
                send(sender, "jail.not-in-jail-settings"); return;
            }
            plugin.gui().openJailSettings(p, t.uuid(), t.name());
        }));
    }

    @Override
    protected List<String> complete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>(filter(List.of("list", "logs", "setting"), args[0]));
            out.addAll(playerNames(args[0]));
            return out;
        }
        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "logs", "setting", "settings" -> { return playerNames(args[1]); }
                case "list" -> { return List.of(); }
                default -> { return filter(plugin.jail().allJails().stream().map(JailLocation::name).toList(), args[1]); }
            }
        }
        return List.of();
    }
}
