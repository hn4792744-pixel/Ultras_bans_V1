package com.ultras.bans.gui;

import org.bukkit.entity.Player;

import java.util.UUID;

/** Small teleport GUI: Teleport To / Bring Player / Back. Delegates to /tp and /tphere. */
public final class TeleportScreen extends Screen {

    private final UUID target;
    private final String targetName;

    public TeleportScreen(GuiManager gui, Player viewer, UUID target, String targetName) {
        super(gui, viewer, "teleport");
        this.target = target;
        this.targetName = targetName;
        placeholders.put("name", targetName);
    }

    @Override
    protected void render() {
        place("info");
        place("teleport_to");
        bind("teleport_to", () -> { viewer.closeInventory(); viewer.performCommand("tp " + targetName); });
        place("bring");
        bind("bring", () -> { viewer.closeInventory(); viewer.performCommand("tphere " + targetName); });
        back(() -> gui.openPlayerSelect(viewer, PlayerSelectAction.TELEPORT));
    }
}
