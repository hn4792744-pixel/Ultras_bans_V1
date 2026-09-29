package com.ultras.bans.command;

import com.ultras.bans.UltrasBansPlugin;
import com.ultras.bans.gui.PlayerSelectAction;
import com.ultras.bans.model.LocationSnapshot;
import com.ultras.bans.punishment.PunishmentRecord;
import com.ultras.bans.punishment.PunishmentResult;
import com.ultras.bans.punishment.PunishmentStatus;
import com.ultras.bans.punishment.PunishmentType;
import com.ultras.bans.util.DurationParser;
import com.ultras.bans.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** /ban /tempban /ipban /ip_ban /mute - timed slot punishments (BAN, IP_BAN, MUTE). */
public final class SlotPunishmentCommand extends UltrasCommandBase {

    private final PunishmentType type;

    public SlotPunishmentCommand(UltrasBansPlugin plugin, PunishmentType type, String permission) {
        super(plugin, permission);
        this.type = type;
    }

    private PlayerSelectAction selectAction() {
        return switch (type) {
            case IP_BAN -> PlayerSelectAction.IP_BAN;
            case MUTE -> PlayerSelectAction.MUTE;
            default -> PlayerSelectAction.BAN;
        };
    }

    private PlayerSelectAction logsAction() {
        return switch (type) {
            case IP_BAN -> PlayerSelectAction.LOGS_IP_BAN;
            case MUTE -> PlayerSelectAction.LOGS_MUTE;
            default -> PlayerSelectAction.LOGS_BAN;
        };
    }

    @Override
    protected void execute(CommandSender sender, Command command, String label, String[] args) {
        boolean tempAlias = command.getName().equalsIgnoreCase("tempban");

        if (args.length == 0) {
            if (!(sender instanceof Player p)) { send(sender, "errors.player-only"); return; }
            plugin.gui().openPlayerSelect(p, selectAction());
            return;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("list")) { list(sender); return; }
        if (sub.equals("logs")) { logs(sender, args); return; }

        boolean allowIp = type == PunishmentType.IP_BAN;
        plugin.targets().resolve(args[0], allowIp).thenAccept(target -> sync(() -> {
            if (target == null) { send(sender, "errors.player-not-found", args[0]); return; }
            handle(sender, target, args, tempAlias);
        }));
    }

    private void handle(CommandSender sender, ResolvedTarget target, String[] args, boolean tempAlias) {
        if (sender instanceof Player self && target.uuid() != null && self.getUniqueId().equals(target.uuid())) {
            send(sender, "punishments.self-action-blocked"); return;
        }
        if (target.uuid() != null && isOpProtected(sender, target.uuid())) {
            send(sender, "punishments.op-protected"); return;
        }

        // No duration -> open GUI (players only)
        if (args.length == 1) {
            if (tempAlias) { send(sender, "errors.invalid-arguments", "/tempban <player> <time> [reason]"); return; }
            if (!(sender instanceof Player p)) {
                if (type == PunishmentType.MUTE || type == PunishmentType.BAN) {
                    apply(sender, target, null, "No reason specified"); // console: permanent by default
                } else apply(sender, target, null, "No reason specified");
                return;
            }
            if (target.uuid() == null) { // raw IP
                plugin.gui().openPlayerSelect(p, selectAction());
                return;
            }
            if (type == PunishmentType.MUTE) plugin.gui().openDurationPicker(p, target.uuid(), target.name(), type, null);
            else plugin.gui().openPunishmentMenu(p, target.uuid(), target.name());
            return;
        }

        String timeArg = args[1];
        Long duration;
        if (DurationParser.isPermanentKeyword(timeArg)) {
            duration = null;
        } else {
            long ms = DurationParser.parseMillis(timeArg);
            if (ms <= 0 || ms > DurationParser.clamp(ms, plugin.mainConfig().maxTemporaryDurationDays())) {
                send(sender, "punishments.invalid-duration"); return;
            }
            duration = ms;
        }
        String reason = args.length > 2 ? joinFrom(args, 2) : "No reason specified";
        apply(sender, target, duration, reason);
    }

    private boolean isOpProtected(CommandSender sender, UUID uuid) {
        if (!plugin.mainConfig().opProtection()) return false;
        if (sender.hasPermission("ultrasbans.bypass.op-protect") || !(sender instanceof Player)) return false;
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.isOp();
    }

    private void apply(CommandSender sender, ResolvedTarget t, Long duration, String reason) {
        LocationSnapshot loc = location(sender);
        CompletableFuture<PunishmentResult> f;
        switch (type) {
            case BAN -> f = plugin.punishmentService().ban(t.uuid(), t.name(), duration, reason, operator(sender), source(sender), loc);
            case MUTE -> f = plugin.punishmentService().mute(t.uuid(), t.name(), duration, reason, operator(sender), source(sender), loc);
            case IP_BAN -> {
                if (t.ip() == null) { send(sender, "errors.player-not-found", t.name()); return; }
                f = plugin.punishmentService().ipBan(t.ip(), t.uuid(), t.name(), duration, reason, operator(sender), source(sender), loc);
            }
            default -> throw new IllegalStateException("Unsupported type " + type);
        }
        f.thenAccept(r -> sync(() -> sendResult(sender, r)));
    }

    private void list(CommandSender sender) {
        plugin.punishmentService().recent(type, 50).thenAccept(records -> sync(() -> {
            List<PunishmentRecord> active = new ArrayList<>();
            for (PunishmentRecord r : records) if (r.status() == PunishmentStatus.ACTIVE) active.add(r);
            if (active.isEmpty()) { send(sender, "gui.no-active-punishments"); return; }
            for (PunishmentRecord r : active) sender.sendMessage(formatLine(r));
        }));
    }

    private void logs(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (!(sender instanceof Player p)) { send(sender, "errors.player-only"); return; }
            plugin.gui().openPlayerSelect(p, logsAction());
            return;
        }
        plugin.targets().resolve(args[1], false).thenAccept(t -> sync(() -> {
            if (t == null) { send(sender, "errors.player-not-found", args[1]); return; }
            if (sender instanceof Player p) { plugin.gui().openLogs(p, t.uuid(), t.name(), type); return; }
            plugin.punishmentService().logsOf(t.uuid(), type, 20).thenAccept(list -> sync(() -> {
                if (list.isEmpty()) send(sender, "commands.no-logs-found");
                for (PunishmentRecord r : list) sender.sendMessage(formatLine(r));
            }));
        }));
    }

    static String formatLine(PunishmentRecord r) {
        String who = r.playerName() != null ? r.playerName() : String.valueOf(r.ip());
        String dur = r.permanent() ? "Permanent" : "until " + TimeUtil.formatAbsolute(r.expiresAt());
        return "§8#" + r.id() + " §c" + r.type() + " §f" + who + " §8| §7" + r.status() + " §8| §7" + dur
                + " §8| §7" + r.reason() + " §8| §fby " + r.operatorName();
    }

    @Override
    protected List<String> complete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>(filter(List.of("list", "logs"), args[0]));
            out.addAll(playerNames(args[0]));
            return out;
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("logs")) return playerNames(args[1]);
            List<String> d = new ArrayList<>(DURATIONS);
            d.add("perm");
            return filter(d, args[1]);
        }
        return List.of();
    }
}
