package com.ultras.bans.gui;

import com.ultras.bans.model.OperatorInfo;
import com.ultras.bans.punishment.PunishmentRecord;
import com.ultras.bans.punishment.PunishmentSource;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Small confirmation GUI for one warning: Remove Warning / Back. */
public final class WarningActionScreen extends Screen {

    private final UUID target;
    private final String targetName;
    private final PunishmentRecord warning;

    public WarningActionScreen(GuiManager gui, Player viewer, UUID target, String targetName, PunishmentRecord warning) {
        super(gui, viewer, "warning_action");
        this.target = target;
        this.targetName = targetName;
        this.warning = warning;
        placeholders.putAll(HistoryScreen.recordPlaceholders(warning));
        placeholders.put("name", targetName);
    }

    @Override
    protected void render() {
        place("info");
        place("remove");
        bind("remove", () -> gui.plugin().punishmentService()
                .removeWarning(warning.id(), new OperatorInfo(viewer.getUniqueId(), viewer.getName()), PunishmentSource.PLAYER)
                .thenAccept(r -> Bukkit.getScheduler().runTask(gui.plugin(), () -> {
                    viewer.sendMessage(gui.plugin().lang().get(r.isSuccess() ? "warnings.warning-removed" : r.messageKey(), r.placeholders()));
                    gui.play(viewer, r.isSuccess() ? "success" : "error");
                    WarningsScreen.show(gui, viewer, target, targetName);
                })));
        back(() -> WarningsScreen.show(gui, viewer, target, targetName));
    }
}
