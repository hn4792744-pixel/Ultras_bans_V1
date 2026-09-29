package com.ultras.bans.command;

import com.ultras.bans.UltrasBansPlugin;
import com.ultras.bans.model.PlayerProfile;
import com.ultras.bans.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;

/** /ip_list <player> - works for offline players since it reads stored profile data. */
public final class IpListCommand extends UltrasCommandBase {

    public IpListCommand(UltrasBansPlugin plugin) { super(plugin, "ultrasbans.logs"); }

    @Override
    protected void execute(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { send(sender, "errors.invalid-arguments", "/ip_list <player>"); return; }
        plugin.targets().resolve(args[0], false).thenCompose(t -> {
            if (t == null) return java.util.concurrent.CompletableFuture.<PlayerProfile>completedFuture(null);
            return plugin.playerRepository().findByUuid(t.uuid());
        }).thenAccept(p -> sync(() -> {
            if (p == null) { send(sender, "player.no-data-found", args[0]); return; }
            boolean online = Bukkit.getPlayer(p.uuid()) != null;
            send(sender, "player.info-header", p.username());
            send(sender, "player.info-line", "UUID", p.uuid());
            send(sender, "player.info-line", "Status", plugin.lang().get(online ? "player.online" : "player.offline"));
            send(sender, "player.info-line", "IP", p.lastIp());
            send(sender, "player.info-line", "Platform", p.platform());
            send(sender, "player.info-line", "Server", p.lastServer());
            send(sender, "player.info-line", "World", p.lastWorld());
            send(sender, "player.info-line", "Location", String.format("%.1f, %.1f, %.1f", p.lastX(), p.lastY(), p.lastZ()));
            send(sender, "player.info-line", "Last Join", TimeUtil.formatAbsolute(p.lastJoin()));
            send(sender, "player.info-line", "Last Quit", TimeUtil.formatAbsolute(p.lastQuit()));
        }));
    }

    @Override
    protected List<String> complete(CommandSender sender, Command command, String label, String[] args) {
        return args.length == 1 ? playerNames(args[0]) : List.of();
    }
}
