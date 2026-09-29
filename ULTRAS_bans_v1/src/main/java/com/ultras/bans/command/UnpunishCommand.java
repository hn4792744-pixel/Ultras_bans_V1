package com.ultras.bans.command;

import com.ultras.bans.UltrasBansPlugin;
import com.ultras.bans.gui.PlayerSelectAction;
import com.ultras.bans.punishment.PunishmentResult;
import com.ultras.bans.punishment.PunishmentType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** /unban /unipban /unmute /unfreeze /unjail. */
public final class UnpunishCommand extends UltrasCommandBase {

    private final PunishmentType type;

    public UnpunishCommand(UltrasBansPlugin plugin, PunishmentType type, String permission) {
        super(plugin, permission);
        this.type = type;
    }

    @Override
    protected void execute(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) {
                plugin.gui().openPlayerSelect(p, PlayerSelectAction.PUNISH_MENU);
            } else send(sender, "errors.invalid-arguments", "/" + label + " <player>");
            return;
        }
        String reason = args.length > 1 ? joinFrom(args, 1) : "Lifted by staff";
        plugin.targets().resolve(args[0], type == PunishmentType.IP_BAN).thenAccept(t -> sync(() -> {
            if (t == null) { send(sender, "errors.player-not-found", args[0]); return; }
            CompletableFuture<PunishmentResult> f = switch (type) {
                case BAN -> plugin.punishmentService().unban(t.uuid(), operator(sender), source(sender), reason);
                case MUTE -> plugin.punishmentService().unmute(t.uuid(), operator(sender), source(sender), reason);
                case FREEZE -> plugin.punishmentService().unfreeze(t.uuid(), operator(sender), source(sender), reason);
                case JAIL -> plugin.punishmentService().unjail(t.uuid(), operator(sender), source(sender), reason);
                case IP_BAN -> {
                    if (t.ip() == null) { send(sender, "errors.player-not-found", args[0]); yield null; }
                    yield plugin.punishmentService().unIpBan(t.ip(), operator(sender), source(sender), reason);
                }
                default -> null;
            };
            if (f != null) f.thenAccept(r -> sync(() -> sendResult(sender, r)));
        }));
    }

    @Override
    protected List<String> complete(CommandSender sender, Command command, String label, String[] args) {
        return args.length == 1 ? playerNames(args[0]) : List.of();
    }
}
