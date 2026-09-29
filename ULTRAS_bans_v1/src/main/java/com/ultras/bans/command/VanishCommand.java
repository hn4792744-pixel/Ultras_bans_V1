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

/** /vanish (/vn, /nv) [player] | list | logs [player]. Hiding is applied by VanishService via the hook system. */
public final class VanishCommand extends UltrasCommandBase {

    public VanishCommand(UltrasBansPlugin plugin) { super(plugin, "ultrasbans.vanish"); }

    @Override
    protected void execute(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
            plugin.punishmentService().recent(PunishmentType.VANISH, 100).thenAccept(records -> sync(() -> {
                List<PunishmentRecord> active = records.stream().filter(r -> r.status() == PunishmentStatus.ACTIVE).toList();
                if (active.isEmpty()) send(sender, "gui.no-active-punishments");
                active.forEach(r -> sender.sendMessage("§8• §f" + r.playerName()));
            }));
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("logs")) {
            if (args.length < 2) {
                if (!(sender instanceof Player p)) { send(sender, "errors.player-only"); return; }
                plugin.gui().openPlayerSelect(p, PlayerSelectAction.LOGS_VANISH);
                return;
            }
            plugin.targets().resolve(args[1], false).thenAccept(t -> sync(() -> {
                if (t == null) { send(sender, "errors.player-not-found", args[1]); return; }
                if (sender instanceof Player p) { plugin.gui().openLogs(p, t.uuid(), t.name(), PunishmentType.VANISH); return; }
                plugin.punishmentService().logsOf(t.uuid(), PunishmentType.VANISH, 20).thenAccept(list -> sync(() -> {
                    if (list.isEmpty()) send(sender, "commands.no-logs-found");
                    list.forEach(r -> sender.sendMessage(SlotPunishmentCommand.formatLine(r)));
                }));
            }));
            return;
        }

        Player target;
        if (args.length == 0) {
            if (!(sender instanceof Player p)) { send(sender, "errors.player-only"); return; }
            target = p;
        } else {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { send(sender, "errors.player-offline"); return; }
        }
        boolean enable = !plugin.punishmentService().isCachedActive(target.getUniqueId(), PunishmentType.VANISH);
        final Player t = target;
        plugin.punishmentService().setVanish(t.getUniqueId(), t.getName(), enable, operator(sender), source(sender), null)
                .thenAccept(r -> sync(() -> {
                    sendResult(sender, r);
                    if (r.isSuccess() && !sender.equals(t)) {
                        send(sender, enable ? "vanish.staff-enabled-other" : "vanish.staff-disabled-other", t.getName());
                    }
                }));
    }

    @Override
    protected List<String> complete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>(filter(List.of("list", "logs"), args[0]));
            out.addAll(filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[0]));
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("logs")) return playerNames(args[1]);
        return List.of();
    }
}
