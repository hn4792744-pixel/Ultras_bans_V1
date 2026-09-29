package com.ultras.bans.gui;

import com.ultras.bans.model.OperatorInfo;
import com.ultras.bans.punishment.PunishmentRecord;
import com.ultras.bans.punishment.PunishmentSource;
import com.ultras.bans.punishment.PunishmentStatus;
import com.ultras.bans.punishment.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Warnings of one player only: Active Warnings section, Expired Warnings section, Remove All at the bottom. */
public final class WarningsScreen extends Screen {

    private final UUID target;
    private final String targetName;
    private final List<PunishmentRecord> active = new ArrayList<>();
    private final List<PunishmentRecord> expired = new ArrayList<>();
    private final List<Integer> activeSlots;
    private final List<Integer> expiredSlots;

    private WarningsScreen(GuiManager gui, Player viewer, UUID target, String targetName, List<PunishmentRecord> all) {
        super(gui, viewer, "warnings");
        this.target = target;
        this.targetName = targetName;
        long now = System.currentTimeMillis();
        for (PunishmentRecord r : all) {
            if (r.type() != PunishmentType.WARN) continue;
            if (r.status() == PunishmentStatus.ACTIVE && !r.isExpiredByTime(now)) active.add(r); else expired.add(r);
        }
        ItemSpec a = layout.item("active_entry");
        ItemSpec x = layout.item("expired_entry");
        this.activeSlots = a == null ? List.of() : a.slots;
        this.expiredSlots = x == null ? List.of() : x.slots;
        placeholders.put("name", targetName);
        placeholders.put("active_count", String.valueOf(active.size()));
        placeholders.put("expired_count", String.valueOf(expired.size()));
    }

    public static void show(GuiManager gui, Player viewer, UUID target, String targetName) {
        gui.plugin().punishmentService().logsOf(target, PunishmentType.WARN, 500).thenAccept(list ->
                Bukkit.getScheduler().runTask(gui.plugin(), () -> {
                    if (viewer.isOnline()) new WarningsScreen(gui, viewer, target, targetName, list).open();
                }));
    }

    @Override
    protected void render() {
        place("active_header");
        place("expired_header");
        ItemSpec a = layout.item("active_entry");
        ItemSpec x = layout.item("expired_entry");
        for (int i = 0; i < activeSlots.size() && i < active.size(); i++) {
            inv.setItem(activeSlots.get(i), a.render(HistoryScreen.recordPlaceholders(active.get(i))));
        }
        for (int i = 0; i < expiredSlots.size() && i < expired.size(); i++) {
            inv.setItem(expiredSlots.get(i), x.render(HistoryScreen.recordPlaceholders(expired.get(i))));
        }
        if (active.isEmpty()) place("no_active");
        place("remove_all");
        bind("remove_all", () -> gui.plugin().punishmentService()
                .removeAllWarnings(target, new OperatorInfo(viewer.getUniqueId(), viewer.getName()), PunishmentSource.PLAYER)
                .thenAccept(count -> Bukkit.getScheduler().runTask(gui.plugin(), () -> {
                    viewer.sendMessage(gui.plugin().lang().get("warnings.all-warnings-removed", count));
                    gui.play(viewer, count > 0 ? "success" : "error");
                    WarningsScreen.show(gui, viewer, target, targetName);
                })));
        back(() -> gui.openPunishmentMenu(viewer, target, targetName));
    }

    @Override
    protected boolean onDynamicClick(int slot, InventoryClickEvent e) {
        int idx = activeSlots.indexOf(slot);
        if (idx >= 0) {
            if (idx < active.size()) {
                gui.play(viewer, "click");
                new WarningActionScreen(gui, viewer, target, targetName, active.get(idx)).open();
            }
            return true;
        }
        return expiredSlots.contains(slot); // expired warnings are read-only
    }
}
