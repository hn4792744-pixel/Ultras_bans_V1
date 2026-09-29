package com.ultras.bans.manager;

import com.ultras.bans.UltrasBansPlugin;
import com.ultras.bans.database.PermissionRepositoryJdbc;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom, OP-independent command permissions. Overrides live in the database (per player and per rank) and are
 * applied to online players through a PermissionAttachment. Precedence: player override > rank setting >
 * whatever the server's normal permission system already grants.
 */
public final class PermissionService implements Listener {

    public record Entry(String key, String node, Material material, String description) { }

    private final UltrasBansPlugin plugin;
    private final PermissionRepositoryJdbc repo;
    private final Map<UUID, Map<String, Boolean>> playerPerms = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Boolean>> rankPerms = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();
    private volatile LinkedHashMap<String, Entry> catalog = new LinkedHashMap<>();
    private volatile List<String> configuredRanks = List.of("default");

    public PermissionService(UltrasBansPlugin plugin, PermissionRepositoryJdbc repo) {
        this.plugin = plugin;
        this.repo = repo;
        reloadConfig();
        repo.loadPlayers().thenAccept(m -> { playerPerms.putAll(m); Bukkit.getScheduler().runTask(plugin, this::applyAll); });
        repo.loadRanks().thenAccept(m -> { rankPerms.putAll(m); Bukkit.getScheduler().runTask(plugin, this::applyAll); });
    }

    public void reloadConfig() {
        File f = new File(plugin.getDataFolder(), "permissions.yml");
        if (!f.exists()) plugin.saveResource("permissions.yml", false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        LinkedHashMap<String, Entry> map = new LinkedHashMap<>();
        ConfigurationSection cs = y.getConfigurationSection("commands");
        if (cs != null) {
            for (String key : cs.getKeys(false)) {
                ConfigurationSection e = cs.getConfigurationSection(key);
                if (e == null) continue;
                Material m = Material.matchMaterial(e.getString("material", "PAPER"));
                map.put(key.toLowerCase(Locale.ROOT), new Entry(key.toLowerCase(Locale.ROOT), e.getString("node", "ultrasbans." + key),
                        m == null ? Material.PAPER : m, e.getString("description", "")));
            }
        }
        this.catalog = map;
        List<String> ranks = y.getStringList("ranks");
        this.configuredRanks = ranks.isEmpty() ? List.of("default") : ranks;
        if (Bukkit.isPrimaryThread()) applyAll();
    }

    public Collection<Entry> commands() { return catalog.values(); }

    public Entry entry(String key) { return catalog.get(key.toLowerCase(Locale.ROOT)); }

    public List<String> ranks() {
        Set<String> all = new LinkedHashSet<>(configuredRanks);
        all.addAll(rankPerms.keySet());
        return new ArrayList<>(all);
    }

    // ---------------- queries ----------------

    public Boolean playerOverride(UUID uuid, String command) {
        Map<String, Boolean> m = playerPerms.get(uuid);
        return m == null ? null : m.get(command.toLowerCase(Locale.ROOT));
    }

    public Boolean rankSetting(String rank, String command) {
        Map<String, Boolean> m = rankPerms.get(rank);
        return m == null ? null : m.get(command.toLowerCase(Locale.ROOT));
    }

    public String rankOf(UUID uuid) {
        var profile = plugin.connections() == null ? null : plugin.connections().liveProfile(uuid);
        return profile == null ? "default" : profile.rank();
    }

    /** Effective on/off shown in the GUI. */
    public boolean effective(UUID uuid, String command) {
        Boolean o = playerOverride(uuid, command);
        if (o != null) return o;
        Boolean r = rankSetting(rankOf(uuid), command);
        if (r != null) return r;
        Player p = Bukkit.getPlayer(uuid);
        Entry e = entry(command);
        return p != null && e != null && p.hasPermission(e.node());
    }

    // ---------------- mutations ----------------

    public void setPlayer(UUID uuid, String command, Boolean valueOrNull) {
        String key = command.toLowerCase(Locale.ROOT);
        if (valueOrNull == null) {
            Map<String, Boolean> m = playerPerms.get(uuid);
            if (m != null) m.remove(key);
        } else {
            playerPerms.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(key, valueOrNull);
        }
        repo.setPlayer(uuid, key, valueOrNull);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) apply(p);
    }

    public void setRank(String rank, String command, Boolean valueOrNull) {
        String key = command.toLowerCase(Locale.ROOT);
        if (valueOrNull == null) {
            Map<String, Boolean> m = rankPerms.get(rank);
            if (m != null) m.remove(key);
        } else {
            rankPerms.computeIfAbsent(rank, k -> new ConcurrentHashMap<>()).put(key, valueOrNull);
        }
        repo.setRank(rank, key, valueOrNull);
        for (Player p : Bukkit.getOnlinePlayers()) if (rankOf(p.getUniqueId()).equals(rank)) apply(p);
    }

    // ---------------- attachments ----------------

    public void applyAll() {
        for (Player p : Bukkit.getOnlinePlayers()) apply(p);
    }

    public void apply(Player p) {
        PermissionAttachment old = attachments.remove(p.getUniqueId());
        if (old != null) {
            try { p.removeAttachment(old); } catch (IllegalArgumentException ignored) { }
        }
        PermissionAttachment att = p.addAttachment(plugin);
        String rank = rankOf(p.getUniqueId());
        for (Entry e : catalog.values()) {
            Boolean v = playerOverride(p.getUniqueId(), e.key());
            if (v == null) v = rankSetting(rank, e.key());
            if (v != null) att.setPermission(e.node(), v);
        }
        attachments.put(p.getUniqueId(), att);
        p.recalculatePermissions();
        p.updateCommands();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        attachments.remove(e.getPlayer().getUniqueId());
    }
}
