package com.ultras.bans.command;

import com.ultras.bans.UltrasBansPlugin;
import com.ultras.bans.gui.PlayerSelectAction;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /admin [permissions [player|ranks] ...] - central admin GUI + the custom permission system (spec 21-23). */
public final class AdminCommand extends UltrasCommandBase {

    public AdminCommand(UltrasBansPlugin plugin) { super(plugin, "ultrasbans.admin"); }

    @Override
    protected void execute(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { send(sender, "errors.player-only"); return; }

        if (args.length == 0) {
            plugin.gui().openPlayerSelect(p, PlayerSelectAction.PUNISH_MENU);
            return;
        }

        if (!args[0].equalsIgnoreCase("permissions")) {
            send(sender, "commands.unknown-subcommand", args[0]);
            return;
        }
        if (args.length == 1) {
            plugin.gui().openPlayerSelect(p, PlayerSelectAction.PERMISSIONS);
            return;
        }
        if (args[1].equalsIgnoreCase("ranks")) {
            plugin.gui().openRanksMenu(p);
            return;
        }

        // /admin permissions <player> <add|remove> <command> <true|false>
        if (args.length == 5 && (args[2].equalsIgnoreCase("add") || args[2].equalsIgnoreCase("remove"))) {
            String playerName = args[1];
            String cmdKey = args[3];
            boolean value = Boolean.parseBoolean(args[4]);
            boolean remove = args[2].equalsIgnoreCase("remove");
            plugin.targets().resolve(playerName, false).thenAccept(t -> sync(() -> {
                if (t == null) { send(sender, "errors.player-not-found", playerName); return; }
                if (plugin.permissions().entry(cmdKey) == null) { send(sender, "commands.unknown-subcommand", cmdKey); return; }
                plugin.permissions().setPlayer(t.uuid(), cmdKey, remove ? null : value);
                send(sender, "admin.permission-updated", cmdKey, remove ? "default" : value, t.name());
            }));
            return;
        }

        // /admin permissions <player> -> open GUI directly for that player
        plugin.targets().resolve(args[1], false).thenAccept(t -> sync(() -> {
            if (t == null) { send(sender, "errors.player-not-found", args[1]); return; }
            plugin.gui().openPermissionsMenu(p, t.uuid(), t.name());
        }));
    }

    @Override
    protected List<String> complete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return filter(List.of("permissions"), args[0]);
        if (!args[0].equalsIgnoreCase("permissions")) return List.of();
        if (args.length == 2) {
            List<String> out = new ArrayList<>(List.of("ranks"));
            out.addAll(playerNames(args[1]));
            return filter(out, args[1]);
        }
        if (args.length == 3) return filter(List.of("add", "remove"), args[2]);
        if (args.length == 4) return filter(plugin.permissions().commands().stream().map(e -> e.key()).toList(), args[3]);
        if (args.length == 5) return filter(List.of("true", "false"), args[4]);
        return List.of();
    }
}
