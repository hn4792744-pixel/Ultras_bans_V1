package com.ultras.bans.command;

import com.ultras.bans.UltrasBansPlugin;
import com.ultras.bans.model.LocationSnapshot;
import com.ultras.bans.model.OperatorInfo;
import com.ultras.bans.punishment.PunishmentResult;
import com.ultras.bans.punishment.PunishmentSource;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared behaviour for every ULTRAS command:
 *  1. commands.yml "disabled-commands": a disabled command behaves as UNKNOWN (vanilla message,
 *     no tab completion) unless the sender has ultrasbans.bypass.disabled.
 *  2. permission check with a lang message.
 *  3. helpers for messages, operator/location snapshots, and applying engine results.
 */
public abstract class UltrasCommandBase implements CommandExecutor, TabCompleter {

    protected final UltrasBansPlugin plugin;
    private final String permission;

    protected UltrasCommandBase(UltrasBansPlugin plugin, String permission) {
        this.plugin = plugin;
        this.permission = permission;
    }

    protected abstract void execute(CommandSender sender, Command command, String label, String[] args);

    protected abstract List<String> complete(CommandSender sender, Command command, String label, String[] args);

    @Override
    public final boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (isHidden(sender, command)) {
            sender.sendMessage("Unknown command. Type \"/help\" for help.");
            return true;
        }
        if (permission != null && !sender.hasPermission(permission)) {
            send(sender, "errors.no-permission");
            return true;
        }
        if (sender instanceof Player pl && plugin.security() != null && plugin.security().isLocked(pl.getUniqueId())) {
            send(sender, "punishments.security-blocked");
            return true;
        }
        try {
            execute(sender, command, label, args);
        } catch (Exception ex) {
            plugin.getLogger().severe("Command /" + label + " failed: " + ex);
            ex.printStackTrace();
            send(sender, "errors.unknown-error");
        }
        return true;
    }

    @Override
    public final List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (isHidden(sender, command)) return List.of();
        if (permission != null && !sender.hasPermission(permission)) return List.of();
        List<String> result = complete(sender, command, alias, args);
        return result == null ? List.of() : result;
    }

    private boolean isHidden(CommandSender sender, Command command) {
        return plugin.commandsConfig().isDisabled(command.getName())
                && !sender.hasPermission("ultrasbans.bypass.disabled");
    }

    // ---- helpers ----

    protected void send(CommandSender to, String key, Object... args) {
        to.sendMessage(plugin.lang().get(key, args));
    }

    protected void sendResult(CommandSender to, PunishmentResult r) {
        to.sendMessage(plugin.lang().get(r.messageKey(), r.placeholders()));
    }

    protected OperatorInfo operator(CommandSender sender) {
        return sender instanceof Player p ? new OperatorInfo(p.getUniqueId(), p.getName()) : OperatorInfo.CONSOLE;
    }

    protected PunishmentSource source(CommandSender sender) {
        return sender instanceof Player ? PunishmentSource.PLAYER : PunishmentSource.CONSOLE;
    }

    protected LocationSnapshot location(CommandSender sender) {
        String server = plugin.mainConfig().serverId();
        if (sender instanceof Player p) {
            Location l = p.getLocation();
            return new LocationSnapshot(server, l.getWorld() == null ? "unknown" : l.getWorld().getName(), l.getX(), l.getY(), l.getZ());
        }
        return LocationSnapshot.unknown(server);
    }

    /** Runs a task on the main server thread (engine callbacks arrive on the DB executor). */
    protected void sync(Runnable r) {
        if (Bukkit.isPrimaryThread()) r.run(); else Bukkit.getScheduler().runTask(plugin, r);
    }

    protected static String joinFrom(String[] args, int start) {
        if (start >= args.length) return "";
        return String.join(" ", java.util.Arrays.copyOfRange(args, start, args.length));
    }

    protected List<String> playerNames(String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(p)) out.add(online.getName());
        }
        for (String n : plugin.playerNameCache().startingWith(prefix, 50)) {
            if (!out.contains(n)) out.add(n);
        }
        return out;
    }

    protected static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(p)) out.add(o);
        return out;
    }

    protected static final List<String> DURATIONS = List.of("1m", "10m", "30m", "1h", "6h", "1d", "7d", "1w", "30d");
}
