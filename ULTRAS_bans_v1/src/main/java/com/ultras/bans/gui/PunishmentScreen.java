package com.ultras.bans.gui;

import com.ultras.bans.model.LocationSnapshot;
import com.ultras.bans.model.OperatorInfo;
import com.ultras.bans.model.PlayerProfile;
import com.ultras.bans.punishment.PunishmentResult;
import com.ultras.bans.punishment.PunishmentSource;
import com.ultras.bans.punishment.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Main Punishment GUI: the per-player control centre. Every button goes through the central PunishmentService,
 * so conflict rules (already muted / already banned ...) are enforced by the engine and the result message is
 * shown to the staff member - the GUI can never create a conflicting punishment.
 */
public final class PunishmentScreen extends Screen {

    private final UUID target;
    private final String targetName;
    private PlayerProfile profile;

    private PunishmentScreen(GuiManager gui, Player viewer, UUID target, String targetName, PlayerProfile profile) {
        super(gui, viewer, "punishment");
        this.target = target;
        this.targetName = targetName;
        this.profile = profile;
    }

    public static void show(GuiManager gui, Player viewer, UUID target, String targetName) {
        gui.plugin().playerRepository().findByUuid(target).thenAccept(p ->
                Bukkit.getScheduler().runTask(gui.plugin(), () -> {
                    if (viewer.isOnline()) new PunishmentScreen(gui, viewer, target, targetName, p).open();
                }));
    }

    private void updatePlaceholders() {
        placeholders.clear();
        placeholders.putAll(PlayerInfo.placeholders(gui.plugin(), profile, target, targetName));
    }

    @Override
    protected void render() {
        updatePlaceholders();

        ItemSpec headSpec = layout.item("head");
        if (headSpec != null && headSpec.enabled) {
            for (int slot : headSpec.slots) {
                ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                if (skull.getItemMeta() instanceof SkullMeta meta) {
                    meta.setOwningPlayer(Bukkit.getOfflinePlayer(target));
                    skull.setItemMeta(meta);
                }
                headSpec.decorate(skull, placeholders);
                inv.setItem(slot, skull);
            }
        }

        String[] ids = {"ban", "temp_ban", "ip_ban", "temp_ip_ban", "mute", "temp_mute", "warn", "temp_warn", "kick",
                "freeze", "jail", "unban", "unipban", "unmute", "unfreeze", "unjail", "clear_all", "history", "warnings", "info"};
        for (String id : ids) place(id);

        bind("ban", () -> apply(PunishmentType.BAN, null));
        bind("temp_ban", () -> duration(PunishmentType.BAN));
        bind("ip_ban", () -> apply(PunishmentType.IP_BAN, null));
        bind("temp_ip_ban", () -> duration(PunishmentType.IP_BAN));
        bind("mute", () -> apply(PunishmentType.MUTE, null));
        bind("temp_mute", () -> duration(PunishmentType.MUTE));
        bind("warn", () -> apply(PunishmentType.WARN, null));
        bind("temp_warn", () -> duration(PunishmentType.WARN));
        bind("kick", () -> apply(PunishmentType.KICK, null));
        bind("freeze", () -> apply(PunishmentType.FREEZE, null));
        bind("jail", () -> apply(PunishmentType.JAIL, null));
        bind("unban", () -> lift(PunishmentType.BAN));
        bind("unipban", () -> lift(PunishmentType.IP_BAN));
        bind("unmute", () -> lift(PunishmentType.MUTE));
        bind("unfreeze", () -> lift(PunishmentType.FREEZE));
        bind("unjail", () -> lift(PunishmentType.JAIL));
        bind("clear_all", this::clearAll);
        bind("history", () -> gui.openHistory(viewer, target, targetName));
        bind("warnings", () -> gui.openWarnings(viewer, target, targetName));
        bind("info", () -> {
            viewer.closeInventory();
            viewer.performCommand("ip_list " + targetName);
        });
        back(() -> gui.openPlayerSelect(viewer, PlayerSelectAction.PUNISH_MENU));
    }

    // ---------------- actions ----------------

    private boolean allowed() {
        if (viewer.getUniqueId().equals(target)) {
            gui.sendKey(viewer, "punishments.self-action-blocked", "error");
            return false;
        }
        OfflinePlayer op = Bukkit.getOfflinePlayer(target);
        if (gui.plugin().mainConfig().opProtection() && op.isOp() && !viewer.hasPermission("ultrasbans.bypass.op-protect")) {
            gui.sendKey(viewer, "punishments.op-protected", "error");
            return false;
        }
        return true;
    }

    private OperatorInfo operator() { return new OperatorInfo(viewer.getUniqueId(), viewer.getName()); }

    private LocationSnapshot location() {
        var l = viewer.getLocation();
        return new LocationSnapshot(gui.plugin().mainConfig().serverId(),
                l.getWorld() == null ? "unknown" : l.getWorld().getName(), l.getX(), l.getY(), l.getZ());
    }

    private String targetIp() {
        String ip = placeholders.get("ip");
        return ip == null || ip.equals("-") ? null : ip;
    }

    private String defaultReason() { return gui.plugin().lang().get("punishments.default-reason-gui"); }

    private void duration(PunishmentType type) {
        if (!allowed()) return;
        gui.openDurationPicker(viewer, target, targetName, type, null);
    }

    private void apply(PunishmentType type, Long duration) {
        if (!allowed()) return;
        var svc = gui.plugin().punishmentService();
        String reason = defaultReason();
        CompletableFuture<PunishmentResult> f;
        switch (type) {
            case BAN -> f = svc.ban(target, targetName, duration, reason, operator(), PunishmentSource.PLAYER, location());
            case MUTE -> f = svc.mute(target, targetName, duration, reason, operator(), PunishmentSource.PLAYER, location());
            case WARN -> f = svc.warn(target, targetName, duration, reason, operator(), PunishmentSource.PLAYER, location());
            case IP_BAN -> {
                String ip = targetIp();
                if (ip == null) { gui.sendKey(viewer, "errors.player-not-found", "error", targetName); return; }
                f = svc.ipBan(ip, target, targetName, duration, reason, operator(), PunishmentSource.PLAYER, location());
            }
            case KICK -> {
                if (Bukkit.getPlayer(target) == null) { gui.sendKey(viewer, "errors.player-offline", "error"); return; }
                f = svc.kick(target, targetName, reason, operator(), PunishmentSource.PLAYER, location());
            }
            case FREEZE -> {
                Player online = Bukkit.getPlayer(target);
                if (online != null && online.hasPermission("ultrasbans.bypass.freeze")) {
                    gui.sendKey(viewer, "punishments.op-protected", "error"); return;
                }
                f = svc.freeze(target, targetName, operator(), PunishmentSource.PLAYER, location());
            }
            case JAIL -> {
                var jail = gui.plugin().jail().findJail(null);
                if (jail == null) { gui.sendKey(viewer, "jail.no-jail-set", "error"); return; }
                f = svc.jail(target, targetName, jail.name(), operator(), PunishmentSource.PLAYER, location());
            }
            default -> { return; }
        }
        f.thenAccept(r -> finish(r));
    }

    private void lift(PunishmentType type) {
        var svc = gui.plugin().punishmentService();
        CompletableFuture<PunishmentResult> f;
        String reason = "Lifted via GUI";
        switch (type) {
            case BAN -> f = svc.unban(target, operator(), PunishmentSource.PLAYER, reason);
            case MUTE -> f = svc.unmute(target, operator(), PunishmentSource.PLAYER, reason);
            case FREEZE -> f = svc.unfreeze(target, operator(), PunishmentSource.PLAYER, reason);
            case JAIL -> f = svc.unjail(target, operator(), PunishmentSource.PLAYER, reason);
            case IP_BAN -> {
                String ip = targetIp();
                if (ip == null) { gui.sendKey(viewer, "errors.player-not-found", "error", targetName); return; }
                f = svc.unIpBan(ip, operator(), PunishmentSource.PLAYER, reason);
            }
            default -> { return; }
        }
        f.thenAccept(r -> finish(r));
    }

    private void clearAll() {
        gui.plugin().punishmentService().clearAll(target, operator(), PunishmentSource.PLAYER).thenAccept(count ->
                Bukkit.getScheduler().runTask(gui.plugin(), () -> {
                    viewer.sendMessage(gui.plugin().lang().get("admin.clear-all-success", count, targetName));
                    gui.play(viewer, count > 0 ? "success" : "error");
                    if (viewer.getOpenInventory().getTopInventory().getHolder() == this) refresh();
                }));
    }

    private void finish(PunishmentResult r) {
        Bukkit.getScheduler().runTask(gui.plugin(), () -> {
            viewer.sendMessage(gui.plugin().lang().get(r.messageKey(), r.placeholders()));
            gui.play(viewer, r.isSuccess() ? "punishment" : "error");
            if (viewer.getOpenInventory().getTopInventory().getHolder() == this) refresh();
        });
    }
}
