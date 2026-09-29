package com.ultras.bans.gui;

import org.bukkit.entity.Player;

/** /admin with no arguments: quick-launch menu for every top-level GUI area. */
public final class MainMenuScreen extends Screen {

    public MainMenuScreen(GuiManager gui, Player viewer) {
        super(gui, viewer, "main");
    }

    @Override
    protected void render() {
        place("players");    bind("players", () -> gui.openPlayerSelect(viewer, PlayerSelectAction.PUNISH_MENU));
        place("ranks");      bind("ranks", () -> gui.openRanksMenu(viewer));
        place("permissions");bind("permissions", () -> gui.openPlayerSelect(viewer, PlayerSelectAction.PERMISSIONS));
        place("gamemode");   bind("gamemode", () -> gui.openPlayerSelect(viewer, PlayerSelectAction.GAMEMODE));
        place("teleport");   bind("teleport", () -> gui.openPlayerSelect(viewer, PlayerSelectAction.TELEPORT));
        place("jail_settings"); bind("jail_settings", () -> gui.openPlayerSelect(viewer, PlayerSelectAction.JAIL_SETTINGS));
        place("close");      bind("close", viewer::closeInventory);
    }
}
