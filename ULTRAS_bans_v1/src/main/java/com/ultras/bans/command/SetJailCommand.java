package com.ultras.bans.command;

import com.ultras.bans.UltrasBansPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** /setjail <name> - saves your current position as a jail spawn. */
public final class SetJailCommand extends UltrasCommandBase {

    public SetJailCommand(UltrasBansPlugin plugin) { super(plugin, "ultrasbans.jail.set"); }

    @Override
    protected void execute(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { send(sender, "errors.player-only"); return; }
        String name = args.length > 0 ? args[0] : "default";
        plugin.jail().setJail(name, p.getLocation());
        send(sender, "jail.jail-set-success", name);
    }

    @Override
    protected List<String> complete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) return List.of();
        return filter(plugin.jail().allJails().stream().map(j -> j.name()).toList(), args[0]);
    }
}
