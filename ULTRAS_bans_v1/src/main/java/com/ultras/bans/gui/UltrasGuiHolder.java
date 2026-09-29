package com.ultras.bans.gui;

import org.bukkit.inventory.InventoryHolder;

/** Marker for every inventory this plugin creates; GuiManager (GUI phase) implements it per screen. */
public interface UltrasGuiHolder extends InventoryHolder {
    String guiId();
}
