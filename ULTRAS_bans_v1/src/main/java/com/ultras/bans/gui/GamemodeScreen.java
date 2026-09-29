package com.ultras.bans.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/** /games GUI: Survival / Creative / Adventure / Spectator / Vanish for a target (delegates to the commands so permissions and rules match). */
public final class GamemodeScreen extends Screen {

    private final UUID target;
    private final String targetName;

    public GamemodeScreen(GuiManager gui, Player viewer, UUID target, String targetName) {
        super(gui, viewer, "gamemode");
        this.target = target;
        this.targetName = targetName;
        placeholders.put("name", targetName);
    }

    private void run(String cmd) {
        viewer.closeInventory();
        viewer.performCommand(cmd + " " + targetName);
    }

    @Override
    protected void render() {
        Player online = Bukkit.getPlayer(target);
        placeholders.put("mode", online == null ? "-" : online.getGameMode().name());
        placeholders.put("vanish", gui.plugin().punishmentService().isCachedActive(target, com.ultras.bans.punishment.PunishmentType.VANISH) ? "&aᴏɴ" : "&cᴏғғ");
        place("info");
        place("survival");  bind("survival", () -> run("game0"));
        place("creative");  bind("creative", () -> run("game1"));
        place("adventure"); bind("adventure", () -> run("game2"));
        place("spectator"); bind("spectator", () -> run("game3"));
        place("vanish");    bind("vanish", () -> run("vanish"));
        back(() -> gui.openPlayerSelect(viewer, PlayerSelectAction.GAMEMODE));
    }
}
