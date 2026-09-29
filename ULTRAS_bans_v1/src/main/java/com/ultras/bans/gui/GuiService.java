package com.ultras.bans.gui;

import com.ultras.bans.punishment.PunishmentType;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Entry point used by commands to open any GUI. The concrete implementation
 * (gui/GuiManager) is built in the GUI phase; commands only depend on this interface.
 */
public interface GuiService {

    /** Opens the paginated all-players selector. */
    void openPlayerSelect(Player viewer, PlayerSelectAction action);

    /** Opens the main punishment control panel for a target. */
    void openPunishmentMenu(Player viewer, UUID target, String targetName);

    /** Opens the shared duration picker for a temporary punishment; on Execute the punishment is applied. */
    void openDurationPicker(Player viewer, UUID target, String targetName, PunishmentType type, String reasonOrNull);

    void openWarnings(Player viewer, UUID target, String targetName);

    void openHistory(Player viewer, UUID target, String targetName);

    void openLogs(Player viewer, UUID target, String targetName, PunishmentType typeOrNull);

    void openGamemode(Player viewer, UUID target, String targetName);

    void openTeleportMenu(Player viewer, UUID target, String targetName);

    void openPermissionsMenu(Player viewer, UUID target, String targetName);

    void openRanksMenu(Player viewer);

    void openJailSettings(Player viewer, UUID target, String targetName);

    /** Reload every gui/*.yml layout from disk. */
    void reload();
}
