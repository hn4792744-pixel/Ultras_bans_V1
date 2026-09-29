package com.ultras.bans.gui;

import com.ultras.bans.model.LocationSnapshot;
import com.ultras.bans.model.OperatorInfo;
import com.ultras.bans.punishment.PunishmentResult;
import com.ultras.bans.punishment.PunishmentSource;
import com.ultras.bans.punishment.PunishmentType;
import com.ultras.bans.util.DurationParser;
import com.ultras.bans.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Shared Temporary Punishment duration picker used for temp ban / temp IP ban / temp mute / temp warn.
 * Centre item shows the selected duration and updates on every click; right buttons add
 * (+1m +5m +10m +15m +30m +1h +6h +16h +1d +7d +1w +30d), left buttons subtract the same steps.
 * Capped at the configured maximum (30 days). Back returns without executing anything.
 */
public final class DurationScreen extends Screen {

    private static final String[] STEP_IDS = {"1m", "5m", "10m", "15m", "30m", "1h", "6h", "16h", "1d", "7d", "1w", "30d"};

    private final UUID target;
    private final String targetName;
    private final PunishmentType type;
    private final String reason;
    private long selected = 0;
    private final long max;

    public DurationScreen(GuiManager gui, Player viewer, UUID target, String targetName, PunishmentType type, String reason) {
        super(gui, viewer, "duration");
        this.target = target;
        this.targetName = targetName;
        this.type = type;
        this.reason = reason == null || reason.isBlank() ? gui.plugin().lang().get("punishments.default-reason-gui") : reason;
        this.max = gui.plugin().mainConfig().maxTemporaryDurationDays() * DurationParser.DAY;
    }

    private void updatePlaceholders() {
        placeholders.put("name", targetName);
        placeholders.put("type", type.name());
        placeholders.put("duration", selected <= 0 ? "-" : TimeUtil.formatDuration(selected));
        placeholders.put("max", TimeUtil.formatDuration(max));
    }

    @Override
    protected void render() {
        updatePlaceholders();
        place("display");
        for (int i = 0; i < STEP_IDS.length; i++) {
            final long step = DurationParser.GUI_STEPS_MILLIS[i];
            String inc = "inc_" + STEP_IDS[i];
            String dec = "dec_" + STEP_IDS[i];
            place(inc);
            place(dec);
            bind(inc, () -> change(step));
            bind(dec, () -> change(-step));
        }
        place("execute");
        bind("execute", this::execute);
        back(() -> gui.openPunishmentMenu(viewer, target, targetName));
    }

    private void change(long delta) {
        long next = selected + delta;
        if (delta > 0 && selected >= max) {
            viewer.sendMessage(gui.plugin().lang().get("gui.duration-max-reached"));
            gui.play(viewer, "error");
            return;
        }
        selected = Math.max(0, Math.min(max, next));
        gui.play(viewer, "click");
        updatePlaceholders();
        // Re-render only the centre item so the picker feels instant.
        layout.place(inv, layout.item("display"), placeholders, null);
    }

    private void execute() {
        if (selected < DurationParser.MINUTE) {
            gui.sendKey(viewer, "punishments.invalid-duration", "error");
            return;
        }
        var svc = gui.plugin().punishmentService();
        OperatorInfo op = new OperatorInfo(viewer.getUniqueId(), viewer.getName());
        var l = viewer.getLocation();
        LocationSnapshot loc = new LocationSnapshot(gui.plugin().mainConfig().serverId(),
                l.getWorld() == null ? "unknown" : l.getWorld().getName(), l.getX(), l.getY(), l.getZ());
        long duration = selected;
        CompletableFuture<PunishmentResult> f;
        switch (type) {
            case BAN -> f = svc.ban(target, targetName, duration, reason, op, PunishmentSource.PLAYER, loc);
            case MUTE -> f = svc.mute(target, targetName, duration, reason, op, PunishmentSource.PLAYER, loc);
            case WARN -> f = svc.warn(target, targetName, duration, reason, op, PunishmentSource.PLAYER, loc);
            case IP_BAN -> {
                String ip = resolveIp();
                if (ip == null) { gui.sendKey(viewer, "errors.player-not-found", "error", targetName); return; }
                f = svc.ipBan(ip, target, targetName, duration, reason, op, PunishmentSource.PLAYER, loc);
            }
            default -> { return; }
        }
        f.thenAccept(r -> Bukkit.getScheduler().runTask(gui.plugin(), () -> {
            viewer.sendMessage(gui.plugin().lang().get(r.messageKey(), r.placeholders()));
            gui.play(viewer, r.isSuccess() ? "punishment" : "error");
            if (r.isSuccess() || r.outcome() != PunishmentResult.Outcome.FAILURE) gui.openPunishmentMenu(viewer, target, targetName);
        }));
    }

    private String resolveIp() {
        Player online = Bukkit.getPlayer(target);
        if (online != null && online.getAddress() != null && online.getAddress().getAddress() != null) {
            return online.getAddress().getAddress().getHostAddress();
        }
        return cachedIp;
    }

    /** Filled by GuiManager before opening (offline targets have no live profile). */
    String cachedIp;

    void cachedIp(String ip) { this.cachedIp = ip; }
}
