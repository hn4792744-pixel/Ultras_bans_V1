package com.ultras.bans.command;

import com.ultras.bans.UltrasBansPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;

/** /ultrasbans (/ub) reload|version. Reload never touches the database connection. */
public final class UltrasBansCommand extends UltrasCommandBase {

    public UltrasBansCommand(UltrasBansPlugin plugin) { super(plugin, null); }

    @Override
    protected void execute(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { send(sender, "commands.reload-usage"); return; }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                try {
                    plugin.reload();
                    send(sender, "admin.reload-success");
                } catch (Exception ex) {
                    send(sender, "admin.reload-failed", String.valueOf(ex.getMessage()));
                }
            }
            case "version" -> send(sender, "commands.version-info", plugin.getDescription().getVersion());
            default -> send(sender, "commands.unknown-subcommand", args[0]);
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, Command command, String label, String[] args) {
        return args.length == 1 ? filter(List.of("reload", "version"), args[0]) : List.of();
    }
}
