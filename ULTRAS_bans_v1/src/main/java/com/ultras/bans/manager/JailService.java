package com.ultras.bans.manager;

import com.ultras.bans.UltrasBansPlugin;
import com.ultras.bans.database.JailRepositoryJdbc;
import com.ultras.bans.model.JailLocation;
import com.ultras.bans.model.JailSession;
import com.ultras.bans.punishment.PunishmentHook;
import com.ultras.bans.punishment.PunishmentRecord;
import com.ultras.bans.punishment.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * JAIL: on jail the full inventory/armor/offhand and previous location are saved (persisted BEFORE clearing),
 * the player is moved to the jail spawn and restrictions apply per player. Jail survives death, relog and
 * restart (respawn/join re-route to jail). On unjail everything is restored and the player is returned to the
 * pre-jail location; the jail is never re-applied afterwards. Freeze is NOT applied and movement is free by default.
 * Offline players are jailed/released lazily on their next join because the punishment record is the source of truth.
 */
public final class JailService implements PunishmentHook, Listener {

    public enum Flag { CHAT, COMMANDS, MOVEMENT, INTERACTION, ITEMS }

    private final UltrasBansPlugin plugin;
    private final JailRepositoryJdbc repo;
    private final Map<String, JailLocation> locations = new ConcurrentHashMap<>();
    private final Map<UUID, JailSession> sessions = new ConcurrentHashMap<>();
    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();
    private volatile YamlConfiguration config;

    public JailService(UltrasBansPlugin plugin, JailRepositoryJdbc repo) {
        this.plugin = plugin;
        this.repo = repo;
        reloadConfig();
        repo.loadLocations().thenAccept(list -> list.forEach(l -> locations.put(l.name().toLowerCase(Locale.ROOT), l)));
        repo.loadSessions().thenAccept(list -> list.forEach(s -> sessions.put(s.playerUuid, s)));
    }

    public void reloadConfig() {
        File f = new File(plugin.getDataFolder(), "jail.yml");
        if (!f.exists()) plugin.saveResource("jail.yml", false);
        config = YamlConfiguration.loadConfiguration(f);
    }

    // ---------------- locations ----------------

    public boolean hasAnyJail() { return !locations.isEmpty(); }

    public Collection<JailLocation> allJails() { return locations.values(); }

    public JailLocation findJail(String nameOrNull) {
        if (nameOrNull != null) return locations.get(nameOrNull.toLowerCase(Locale.ROOT));
        return locations.values().stream().min(Comparator.comparing(JailLocation::name)).orElse(null);
    }

    public void setJail(String name, Location l) {
        JailLocation jl = new JailLocation(name, plugin.mainConfig().serverId(),
                l.getWorld() == null ? "unknown" : l.getWorld().getName(),
                l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
        locations.put(name.toLowerCase(Locale.ROOT), jl);
        repo.saveLocation(jl);
    }

    private Location toBukkit(JailLocation jl) {
        World w = Bukkit.getWorld(jl.world());
        if (w == null) return null;
        return new Location(w, jl.x(), jl.y(), jl.z(), jl.yaw(), jl.pitch());
    }

    // ---------------- state ----------------

    public boolean isJailed(UUID uuid) {
        return plugin.punishmentCache().isActive(uuid, PunishmentType.JAIL);
    }

    public JailSession session(UUID uuid) { return sessions.get(uuid); }

    /** UUIDs of players who are currently jailed and have an active session (for the jail-settings player list). */
    public List<UUID> jailedUuids() {
        List<UUID> out = new ArrayList<>();
        for (UUID id : sessions.keySet()) if (isJailed(id)) out.add(id);
        return out;
    }

    public boolean toggle(UUID uuid, Flag flag) {
        JailSession s = sessions.get(uuid);
        if (s == null) return false;
        switch (flag) {
            case CHAT -> s.chatBlocked = !s.chatBlocked;
            case COMMANDS -> s.commandsBlocked = !s.commandsBlocked;
            case MOVEMENT -> s.movementRestricted = !s.movementRestricted;
            case INTERACTION -> s.interactionRestricted = !s.interactionRestricted;
            case ITEMS -> s.itemsRestricted = !s.itemsRestricted;
        }
        repo.updateFlags(uuid, s.flagsCsv());
        return true;
    }

    public boolean get(UUID uuid, Flag flag) {
        JailSession s = sessions.get(uuid);
        if (s == null) return false;
        return switch (flag) {
            case CHAT -> s.chatBlocked;
            case COMMANDS -> s.commandsBlocked;
            case MOVEMENT -> s.movementRestricted;
            case INTERACTION -> s.interactionRestricted;
            case ITEMS -> s.itemsRestricted;
        };
    }

    // ---------------- hook ----------------

    @Override
    public void onApplied(PunishmentRecord r) {
        if (r.type() != PunishmentType.JAIL || r.playerUuid() == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(r.playerUuid());
            if (p != null) enforce(p, r);
        });
    }

    @Override
    public void onLifted(PunishmentRecord r) {
        if (r.type() != PunishmentType.JAIL || r.playerUuid() == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(r.playerUuid());
            if (p != null) release(p);
        });
    }

    // ---------------- enforce / release ----------------

    private void enforce(Player p, PunishmentRecord record) {
        UUID id = p.getUniqueId();
        JailSession existing = sessions.get(id);
        JailLocation jl = findJail(jailNameOf(record, existing));
        Location dest = jl == null ? null : toBukkit(jl);

        if (existing != null) { // already captured (death/relog): just route back to the cell
            if (dest != null && !insideJailWorld(p, dest)) p.teleportAsync(dest);
            return;
        }
        if (!processing.add(id)) return;

        var inv = p.getInventory();
        String invB64 = encode(inv.getStorageContents());
        String armorB64 = encode(inv.getArmorContents());
        String offB64 = encode(new ItemStack[]{inv.getItemInOffHand()});
        Location prev = p.getLocation();

        JailSession s = new JailSession(id, record.id(), jl == null ? "default" : jl.name(),
                plugin.mainConfig().serverId(), prev.getWorld() == null ? "unknown" : prev.getWorld().getName(),
                prev.getX(), prev.getY(), prev.getZ(), prev.getYaw(), prev.getPitch(), invB64, armorB64, offB64,
                System.currentTimeMillis());
        s.chatBlocked = config.getBoolean("defaults.chat-blocked", true);
        s.commandsBlocked = config.getBoolean("defaults.commands-blocked", true);
        s.movementRestricted = config.getBoolean("defaults.movement-restricted", false);
        s.interactionRestricted = config.getBoolean("defaults.interaction-restricted", true);
        s.itemsRestricted = config.getBoolean("defaults.items-restricted", true);

        // Persist first, THEN clear: a crash can never lose the player's items.
        repo.saveSession(s).whenComplete((v, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
            processing.remove(id);
            if (ex != null) {
                plugin.getLogger().log(Level.SEVERE, "Jail session save failed; inventory left untouched for " + p.getName(), ex);
                return;
            }
            sessions.put(id, s);
            if (!p.isOnline()) return;
            inv.clear();
            inv.setArmorContents(new ItemStack[4]);
            inv.setItemInOffHand(null);
            if (dest != null) p.teleportAsync(dest);
            p.sendMessage(plugin.lang().getNamed("jail.jailed-notify", Map.of("reason", s.jailName)));
        }));
    }

    private void release(Player p) {
        UUID id = p.getUniqueId();
        JailSession s = sessions.get(id);
        if (s == null || isJailed(id)) return;
        if (!processing.add(id)) return;
        try {
            var inv = p.getInventory();
            ItemStack[] storage = decode(s.inventoryB64);
            ItemStack[] armor = decode(s.armorB64);
            ItemStack[] off = decode(s.offhandB64);
            inv.clear();
            if (storage.length > 0) inv.setStorageContents(Arrays.copyOf(storage, inv.getStorageContents().length));
            if (armor.length > 0) inv.setArmorContents(Arrays.copyOf(armor, 4));
            inv.setItemInOffHand(off.length > 0 ? off[0] : null);

            World w = Bukkit.getWorld(s.prevWorld);
            if (w != null) p.teleportAsync(new Location(w, s.prevX, s.prevY, s.prevZ, s.prevYaw, s.prevPitch));
            sessions.remove(id);
            repo.deleteSession(id);
            p.sendMessage(plugin.lang().get("jail.unjailed-notify"));
            p.sendMessage(plugin.lang().get("jail.inventory-restored"));
        } finally {
            processing.remove(id);
        }
    }

    private String jailNameOf(PunishmentRecord r, JailSession s) {
        if (s != null) return s.jailName;
        String reason = r.reason();
        return reason != null && reason.startsWith("Jailed: ") ? reason.substring(8) : null;
    }

    private boolean insideJailWorld(Player p, Location dest) {
        return p.getWorld().equals(dest.getWorld()) && p.getLocation().distanceSquared(dest) < 400;
    }

    // ---------------- listeners ----------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // Delay a tick so the punishment cache/session maps are populated and the player is fully in the world.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            PunishmentRecord rec = plugin.punishmentCache().getActive(p.getUniqueId(), PunishmentType.JAIL);
            if (rec != null) enforce(p, rec);
            else if (sessions.containsKey(p.getUniqueId())) release(p); // was released while offline
        }, 5L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        JailSession s = sessions.get(p.getUniqueId());
        if (s == null || !isJailed(p.getUniqueId())) return;
        JailLocation jl = findJail(s.jailName);
        Location dest = jl == null ? null : toBukkit(jl);
        if (dest != null) e.setRespawnLocation(dest);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        if (!isJailed(e.getEntity().getUniqueId())) return;
        e.setKeepInventory(true); // inventory is empty anyway; guarantees nothing drops or duplicates
        e.getDrops().clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void onChat(AsyncPlayerChatEvent e) {
        JailSession s = jailedSession(e.getPlayer());
        if (s == null || !s.chatBlocked) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(plugin.lang().get("jail.jail-chat-blocked"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        JailSession s = jailedSession(e.getPlayer());
        if (s == null || !s.commandsBlocked) return;
        if (e.getPlayer().hasPermission("ultrasbans.bypass.jail")) return;
        String label = e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (config.getStringList("allowed-commands").stream().anyMatch(c -> c.equalsIgnoreCase(label))) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(plugin.lang().get("jail.jail-command-blocked"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (to == null) return;
        JailSession s = jailedSession(e.getPlayer());
        if (s == null || !s.movementRestricted) return;
        JailLocation jl = findJail(s.jailName);
        Location center = jl == null ? null : toBukkit(jl);
        if (center == null || !center.getWorld().equals(to.getWorld())) return;
        double r = config.getDouble("movement-radius", 8);
        if (center.distanceSquared(to) > r * r) e.setTo(e.getFrom());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { interact(e.getPlayer(), e); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { interact(e.getPlayer(), e); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) { interact(e.getPlayer(), e); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) { items(e.getPlayer(), e); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(org.bukkit.event.entity.EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p) items(p, e);
    }

    private JailSession jailedSession(Player p) {
        if (!isJailed(p.getUniqueId())) return null;
        return sessions.get(p.getUniqueId());
    }

    private void interact(Player p, org.bukkit.event.Cancellable e) {
        JailSession s = jailedSession(p);
        if (s != null && s.interactionRestricted) e.setCancelled(true);
    }

    private void items(Player p, org.bukkit.event.Cancellable e) {
        JailSession s = jailedSession(p);
        if (s != null && s.itemsRestricted) e.setCancelled(true);
    }

    // ---------------- serialization (Paper API, version-stable) ----------------

    private static String encode(ItemStack[] items) {
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(items));
    }

    private static ItemStack[] decode(String b64) {
        if (b64 == null || b64.isEmpty()) return new ItemStack[0];
        return ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(b64));
    }
}
