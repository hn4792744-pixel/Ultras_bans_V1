package com.ultras.bans.command;

import com.ultras.bans.UltrasBansPlugin;
import com.ultras.bans.gui.PlayerSelectAction;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** /game0../game3 [player] and /games [player] (GUI). */
public final class GameModeCommand extends UltrasCommandBase {

    public GameModeCommand(UltrasBansPlugin plugin) { super(plugin, "ultrasbans.gamemode"); }

    @Override
    protected void execute(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();

        if (name.equals("games")) {
            if (!(sender instanceof Player viewer)) { send(sender, "errors.player-only"); return; }
            if (args.length == 0) { plugin.gui().openGamemode(viewer, viewer.getUniqueId(), viewer.getName()); return; }
            Player t = Bukkit.getPlayerExact(args[0]);
            if (t == null) { send(sender, "errors.player-offline"); return; }
            plugin.gui().openGamemode(viewer, t.getUniqueId(), t.getName());
            return;
        }

        GameMode mode = switch (name) {
            case "game0" -> GameMode.SURVIVAL;
            case "game1" -> GameMode.CREATIVE;
            case "game2" -> GameMode.ADVENTURE;
            default -> GameMode.SPECTATOR;
        };
        Player target;
        if (args.length == 0) {
            if (!(sender instanceof Player p)) { send(sender, "errors.player-only"); return; }
            target = p;
        } else {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { send(sender, "errors.player-offline"); return; }
        }
        target.setGameMode(mode);
        send(sender, "admin.gamemode-updated", mode.name(), target.getName());
    }

    @Override
    protected List<String> complete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) return List.of();
        return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[0]);
    }
}
