package com.ultras.bans.listener;

import com.ultras.bans.UltrasBansPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;

/** Relays chat, admin commands, console commands, deaths and achievements to Discord (spec sections 37-43). */
public final class DiscordEventListener implements Listener {

    private final UltrasBansPlugin plugin;

    public DiscordEventListener(UltrasBansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        if (!plugin.discord().enabled()) return;
        plugin.discord().sendChat(e.getPlayer().getName(), plugin.mainConfig().serverId(), e.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!plugin.discord().enabled()) return;
        String base = e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        String our = base.contains(":") ? base.substring(base.indexOf(':') + 1) : base;
        if (org.bukkit.Bukkit.getPluginCommand(plugin.getName().toLowerCase(Locale.ROOT) + ":" + our) == null) return;
        plugin.discord().sendCommand(e.getPlayer().getName(), e.getMessage(), "Executed", plugin.mainConfig().serverId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(ServerCommandEvent e) {
        if (!plugin.discord().enabled()) return;
        plugin.discord().sendConsole("/" + e.getCommand());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        if (!plugin.discord().enabled()) return;
        Player p = e.getEntity();
        String killer = p.getKiller() != null ? p.getKiller().getName()
                : e.getDamageSource().getCausingEntity() != null ? e.getDamageSource().getCausingEntity().getName() : "-";
        boolean vanished = plugin.vanish() != null && plugin.vanish().isVanished(p);
        plugin.discord().sendDeath(p.getName(), killer, e.getDamageSource().getDamageType().toString(),
                plugin.mainConfig().serverId(), p.getWorld().getName(), p.getGameMode().name(), vanished, p.isOp());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        if (!plugin.discord().enabled()) return;
        var display = e.getAdvancement().getDisplay();
        if (display == null) return;
        plugin.discord().sendAchievement(e.getPlayer().getName(), net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(display.title()));
    }
}
