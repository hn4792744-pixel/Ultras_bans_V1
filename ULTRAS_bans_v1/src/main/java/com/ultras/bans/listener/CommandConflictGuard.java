package com.ultras.bans.listener;

import com.ultras.bans.UltrasBansPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;

/**
 * Enforces "only ULTRAS_bans_v1's commands run":
 *  - protected labels (ban, kick, mute...) are rewritten to this plugin's namespaced command so no other
 *    plugin's or vanilla's implementation can execute, even if it owns the plain label;
 *  - commands disabled in commands.yml and hidden-conflicting-commands are cancelled with the vanilla
 *    "Unknown command" message and removed from tab completion (unless ultrasbans.bypass.disabled).
 */
public final class CommandConflictGuard implements Listener {

    private static final String UNKNOWN = "Unknown command. Type \"/help\" for help.";
    private final UltrasBansPlugin plugin;
    private final String namespace;

    public CommandConflictGuard(UltrasBansPlugin plugin) {
        this.plugin = plugin;
        this.namespace = plugin.getName().toLowerCase(Locale.ROOT);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage();
        if (msg.length() < 2) return;
        String[] parts = msg.substring(1).split(" ", 2);
        String label = parts[0].toLowerCase(Locale.ROOT);
        String rest = parts.length > 1 ? " " + parts[1] : "";
        Player p = e.getPlayer();
        boolean bypass = p.hasPermission("ultrasbans.bypass.disabled");

        if (plugin.commandsConfig().isHiddenConflicting(label) && !bypass) {
            e.setCancelled(true);
            p.sendMessage(UNKNOWN);
            return;
        }

        String base = label.contains(":") ? label.substring(label.indexOf(':') + 1) : label;
        Command ours = Bukkit.getPluginCommand(namespace + ":" + base);

        if (ours != null && plugin.commandsConfig().isDisabled(ours.getName()) && !bypass) {
            e.setCancelled(true);
            p.sendMessage(UNKNOWN);
            return;
        }

        if (plugin.commandsConfig().isConflictGuardEnabled() && ours != null
                && plugin.commandsConfig().isProtectedLabel(base) && !label.startsWith(namespace + ":")) {
            e.setMessage("/" + namespace + ":" + base + rest);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent e) {
        if (!plugin.commandsConfig().isConflictGuardEnabled()) return;
        String cmd = e.getCommand();
        if (cmd.isBlank()) return;
        String[] parts = cmd.split(" ", 2);
        String label = parts[0].toLowerCase(Locale.ROOT);
        String rest = parts.length > 1 ? " " + parts[1] : "";
        String base = label.contains(":") ? label.substring(label.indexOf(':') + 1) : label;
        Command ours = Bukkit.getPluginCommand(namespace + ":" + base);
        if (ours != null && plugin.commandsConfig().isProtectedLabel(base) && !label.startsWith(namespace + ":")) {
            e.setCommand(namespace + ":" + base + rest);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandSend(PlayerCommandSendEvent e) {
        if (e.getPlayer().hasPermission("ultrasbans.bypass.disabled")) return;
        e.getCommands().removeIf(label -> {
            String l = label.toLowerCase(Locale.ROOT);
            if (plugin.commandsConfig().isHiddenConflicting(l)) return true;
            Command ours = Bukkit.getPluginCommand(namespace + ":" + (l.contains(":") ? l.substring(l.indexOf(':') + 1) : l));
            return ours != null && plugin.commandsConfig().isDisabled(ours.getName());
        });
    }
}
