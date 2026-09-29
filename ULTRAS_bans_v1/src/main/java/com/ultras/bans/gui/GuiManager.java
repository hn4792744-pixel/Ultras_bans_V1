package com.ultras.bans.gui;

import com.ultras.bans.UltrasBansPlugin;
import com.ultras.bans.punishment.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Concrete {@link GuiService}: owns every gui/*.yml layout, routes inventory clicks to the open {@link Screen},
 * plays configurable sounds, and runs the chat-based player search flow (spec section 4).
 */
public final class GuiManager implements GuiService, Listener {

    private final UltrasBansPlugin plugin;
    private final Map<String, GuiLayout> layouts = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerSelectAction> searching = new ConcurrentHashMap<>();
    private volatile YamlConfiguration sounds;

    private static final String[] LAYOUT_NAMES = {
            "main", "players", "punishment", "duration", "history", "logs", "warnings", "warning_action",
            "jail_settings", "permissions", "ranks", "gamemode", "teleport", "sounds"
    };

    public GuiManager(UltrasBansPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        reload();
    }

    public UltrasBansPlugin plugin() { return plugin; }

    public GuiLayout layout(String name) {
        GuiLayout l = layouts.get(name);
        if (l == null) throw new IllegalStateException("Missing GUI layout: " + name + ".yml");
        return l;
    }

    @Override
    public void reload() {
        File dir = new File(plugin.getDataFolder(), "gui");
        if (!dir.exists() && !dir.mkdirs()) plugin.getLogger().warning("Could not create gui/ folder.");
        for (String name : LAYOUT_NAMES) {
            if (name.equals("sounds")) continue; // handled separately below, not a GuiLayout
            File f = new File(dir, name + ".yml");
            if (!f.exists()) {
                String resource = "gui/" + name + ".yml";
                if (plugin.getResource(resource) != null) plugin.saveResource(resource, false);
            }
            if (f.exists()) {
                try {
                    layouts.put(name, new GuiLayout(name, f));
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to parse gui/" + name + ".yml", ex);
                }
            }
        }
        File soundsFile = new File(dir, "sounds.yml");
        if (!soundsFile.exists() && plugin.getResource("gui/sounds.yml") != null) plugin.saveResource("gui/sounds.yml", false);
        sounds = soundsFile.exists() ? YamlConfiguration.loadConfiguration(soundsFile) : new YamlConfiguration();
    }

    // ---------------- sounds ----------------

    public void play(Player p, String key) {
        String raw = sounds.getString(key, "");
        if (!raw.isBlank()) playRaw(p, raw);
    }

    public void playRaw(Player p, String raw) {
        if (raw == null || raw.isBlank()) return;
        try {
            String[] parts = raw.split(":");
            Sound s = Sound.valueOf(parts[0].toUpperCase());
            float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1f;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1f;
            p.playSound(p.getLocation(), s, volume, pitch);
        } catch (Exception ignored) { }
    }

    public void sendKey(Player p, String key, String soundKey, Object... args) {
        p.sendMessage(plugin.lang().get(key, args));
        play(p, soundKey);
    }

    // ---------------- GuiService ----------------

    @Override
    public void openPlayerSelect(Player viewer, PlayerSelectAction action) {
        PlayersScreen.show(this, viewer, action, 0, null);
    }

    @Override
    public void openPunishmentMenu(Player viewer, UUID target, String targetName) {
        PunishmentScreen.show(this, viewer, target, targetName);
    }

    @Override
    public void openDurationPicker(Player viewer, UUID target, String targetName, PunishmentType type, String reasonOrNull) {
        DurationScreen screen = new DurationScreen(this, viewer, target, targetName, type, reasonOrNull);
        if (type == PunishmentType.IP_BAN) {
            Player online = Bukkit.getPlayer(target);
            if (online != null && online.getAddress() != null && online.getAddress().getAddress() != null) {
                screen.cachedIp(online.getAddress().getAddress().getHostAddress());
                screen.open();
                return;
            }
            plugin.playerRepository().findByUuid(target).thenAccept(p -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (p != null) screen.cachedIp(p.lastIp());
                if (viewer.isOnline()) screen.open();
            }));
            return;
        }
        screen.open();
    }

    @Override
    public void openWarnings(Player viewer, UUID target, String targetName) {
        WarningsScreen.show(this, viewer, target, targetName);
    }

    @Override
    public void openHistory(Player viewer, UUID target, String targetName) {
        HistoryScreen.show(this, viewer, target, targetName, null, 0);
    }

    @Override
    public void openLogs(Player viewer, UUID target, String targetName, PunishmentType typeOrNull) {
        HistoryScreen.show(this, viewer, target, targetName, typeOrNull, 0);
    }

    @Override
    public void openGamemode(Player viewer, UUID target, String targetName) {
        new GamemodeScreen(this, viewer, target, targetName).open();
    }

    @Override
    public void openTeleportMenu(Player viewer, UUID target, String targetName) {
        new TeleportScreen(this, viewer, target, targetName).open();
    }

    @Override
    public void openPermissionsMenu(Player viewer, UUID target, String targetName) {
        new PermissionsScreen(this, viewer, target, targetName).open();
    }

    @Override
    public void openRanksMenu(Player viewer) {
        new RanksScreen(this, viewer).open();
    }

    @Override
    public void openJailSettings(Player viewer, UUID target, String targetName) {
        new JailSettingsScreen(this, viewer, target, targetName).open();
    }

    public void openMainMenu(Player viewer) {
        new MainMenuScreen(this, viewer).open();
    }

    /** Called by PlayersScreen when a head is clicked, to route to the right next screen for the requested action. */
    public void dispatchSelect(Player viewer, PlayerSelectAction action, UUID uuid, String name) {
        switch (action) {
            case PUNISH_MENU, BAN, IP_BAN, MUTE, WARN, KICK, FREEZE, JAIL -> openPunishmentMenu(viewer, uuid, name);
            case WARN_LIST -> openWarnings(viewer, uuid, name);
            case LOGS_BAN -> openLogs(viewer, uuid, name, PunishmentType.BAN);
            case LOGS_IP_BAN -> openLogs(viewer, uuid, name, PunishmentType.IP_BAN);
            case LOGS_MUTE -> openLogs(viewer, uuid, name, PunishmentType.MUTE);
            case LOGS_WARN -> openLogs(viewer, uuid, name, PunishmentType.WARN);
            case LOGS_JAIL -> openLogs(viewer, uuid, name, PunishmentType.JAIL);
            case LOGS_FREEZE -> openLogs(viewer, uuid, name, PunishmentType.FREEZE);
            case LOGS_VANISH -> openLogs(viewer, uuid, name, PunishmentType.VANISH);
            case JAIL_SETTINGS -> {
                if (!plugin.jail().isJailed(uuid) || plugin.jail().session(uuid) == null) {
                    sendKey(viewer, "jail.not-in-jail-settings", "error");
                } else openJailSettings(viewer, uuid, name);
            }
            case TELEPORT -> openTeleportMenu(viewer, uuid, name);
            case PERMISSIONS -> openPermissionsMenu(viewer, uuid, name);
            case GAMEMODE -> openGamemode(viewer, uuid, name);
        }
    }

    // ---------------- chat search ----------------

    public void beginSearch(Player viewer, PlayerSelectAction action) {
        searching.put(viewer.getUniqueId(), action);
        viewer.closeInventory();
        sendKey(viewer, "gui.search-prompt", "");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        PlayerSelectAction action = searching.remove(e.getPlayer().getUniqueId());
        if (action == null) return;
        e.setCancelled(true);
        String msg = e.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (msg.equalsIgnoreCase("cancel")) {
                sendKey(e.getPlayer(), "gui.search-cancelled", "back");
                openPlayerSelect(e.getPlayer(), action);
                return;
            }
            PlayersScreen.show(this, e.getPlayer(), action, 0, msg);
        });
    }

    // ---------------- routing ----------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Screen screen)) return;
        e.setCancelled(true);
        if (!e.getWhoClicked().equals(screen.viewerOf())) return;
        screen.handleClick(e);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Screen screen) screen.onClose();
    }
}
