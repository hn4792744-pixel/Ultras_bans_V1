package com.ultras.bans.command;

import com.ultras.bans.UltrasBansPlugin;
import com.ultras.bans.punishment.PunishmentType;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;

/** Binds every command declared in plugin.yml to its executor. */
public final class CommandRegistrar {

    private CommandRegistrar() {}

    public static void registerAll(UltrasBansPlugin plugin) {
        slot(plugin, "ban", PunishmentType.BAN, "ultrasbans.ban");
        slot(plugin, "tempban", PunishmentType.BAN, "ultrasbans.ban");
        slot(plugin, "ipban", PunishmentType.IP_BAN, "ultrasbans.ipban");
        slot(plugin, "mute", PunishmentType.MUTE, "ultrasbans.mute");

        unpunish(plugin, "unban", PunishmentType.BAN, "ultrasbans.ban");
        unpunish(plugin, "unipban", PunishmentType.IP_BAN, "ultrasbans.ipban");
        unpunish(plugin, "unmute", PunishmentType.MUTE, "ultrasbans.mute");
        unpunish(plugin, "unfreeze", PunishmentType.FREEZE, "ultrasbans.freeze");
        unpunish(plugin, "unjail", PunishmentType.JAIL, "ultrasbans.jail");

        bind(plugin, "warn", new WarnCommand(plugin));
        bind(plugin, "kick", new KickCommand(plugin));
        bind(plugin, "freeze", new FreezeCommand(plugin));
        bind(plugin, "vanish", new VanishCommand(plugin));

        GameModeCommand gm = new GameModeCommand(plugin);
        for (String n : new String[]{"game0", "game1", "game2", "game3", "games"}) bind(plugin, n, gm);

        TeleportCommand tp = new TeleportCommand(plugin);
        bind(plugin, "tp", tp);
        bind(plugin, "tphere", tp);

        bind(plugin, "jail", new JailCommand(plugin));
        bind(plugin, "setjail", new SetJailCommand(plugin));
        bind(plugin, "admin", new AdminCommand(plugin));
        bind(plugin, "tam", new TamCommand(plugin));
        bind(plugin, "ip_list", new IpListCommand(plugin));
        bind(plugin, "ultrasbans", new UltrasBansCommand(plugin));
    }

    private static void slot(UltrasBansPlugin plugin, String name, PunishmentType type, String perm) {
        bind(plugin, name, new SlotPunishmentCommand(plugin, type, perm));
    }

    private static void unpunish(UltrasBansPlugin plugin, String name, PunishmentType type, String perm) {
        bind(plugin, name, new UnpunishCommand(plugin, type, perm));
    }

    public static <T extends CommandExecutor & TabCompleter> void bind(UltrasBansPlugin plugin, String name, T handler) {
        PluginCommand cmd = plugin.getCommand(name);
        if (cmd == null) {
            plugin.getLogger().warning("Command '" + name + "' is not declared in plugin.yml");
            return;
        }
        cmd.setExecutor(handler);
        cmd.setTabCompleter(handler);
    }
}
