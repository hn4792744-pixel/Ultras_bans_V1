package com.ultras.bans.command;

import com.ultras.bans.UltrasBansPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.*;

/**
 * /tam - central administrative dispatcher (spec section 28). Re-executes the equivalent dedicated command so
 * behaviour (permissions, GUIs, conflict rules) is defined in exactly one place.
 */
public final class TamCommand extends UltrasCommandBase {

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("ban", "ban"), Map.entry("tempban", "tempban"), Map.entry("unban", "unban"),
            Map.entry("ipban", "ipban"), Map.entry("ip_ban", "ipban"), Map.entry("unipban", "unipban"),
            Map.entry("mute", "mute"), Map.entry("unmute", "unmute"), Map.entry("warn", "warn"),
            Map.entry("kick", "kick"), Map.entry("freeze", "freeze"), Map.entry("unfreeze", "unfreeze"),
            Map.entry("jail", "jail"), Map.entry("unjail", "unjail"), Map.entry("setjail", "setjail"),
            Map.entry("vn", "vanish"), Map.entry("vanish", "vanish"), Map.entry("nv", "vanish"),
            Map.entry("admin", "admin"), Map.entry("tp", "tp"), Map.entry("tphere", "tphere"),
            Map.entry("gamemode", "games"), Map.entry("ip_list", "ip_list")
    );

    public TamCommand(UltrasBansPlugin plugin) { super(plugin, "ultrasbans.admin"); }

    @Override
    protected void execute(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { send(sender, "errors.invalid-arguments", "/tam <action> ..."); return; }
        String target = ALIASES.get(args[0].toLowerCase(Locale.ROOT));
        if (target == null) { send(sender, "commands.unknown-subcommand", args[0]); return; }
        StringBuilder full = new StringBuilder(target);
        for (int i = 1; i < args.length; i++) full.append(' ').append(args[i]);
        org.bukkit.Bukkit.dispatchCommand(sender, full.toString());
    }

    @Override
    protected List<String> complete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return filter(new ArrayList<>(ALIASES.keySet()), args[0]);
        String target = ALIASES.get(args[0].toLowerCase(Locale.ROOT));
        if (target == null) return List.of();
        var cmd = org.bukkit.Bukkit.getPluginCommand(target);
        if (cmd == null || cmd.getTabCompleter() == null) return List.of();
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        try {
            List<String> r = cmd.getTabCompleter().onTabComplete(sender, cmd, label, rest);
            return r == null ? List.of() : r;
        } catch (Exception ex) {
            return List.of();
        }
    }
}
