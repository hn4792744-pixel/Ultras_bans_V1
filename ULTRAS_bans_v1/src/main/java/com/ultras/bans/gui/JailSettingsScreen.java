package com.ultras.bans.gui;

import com.ultras.bans.manager.JailService;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/** Per-player jail restriction toggles. Only ever opened for a currently jailed player. */
public final class JailSettingsScreen extends Screen {

    private final UUID target;
    private final String targetName;

    public JailSettingsScreen(GuiManager gui, Player viewer, UUID target, String targetName) {
        super(gui, viewer, "jail_settings");
        this.target = target;
        this.targetName = targetName;
        placeholders.put("name", targetName);
    }

    @Override
    protected void render() {
        JailService jail = gui.plugin().jail();
        if (!jail.isJailed(target) || jail.session(target) == null) {
            viewer.closeInventory();
            viewer.sendMessage(gui.plugin().lang().get("jail.not-in-jail-settings"));
            return;
        }
        for (JailService.Flag flag : JailService.Flag.values()) {
            String id = flag.name().toLowerCase();
            boolean on = jail.get(target, flag);
            place(id, Map.of("state", on ? "&aᴏɴ" : "&cᴏғғ"));
            bind(id, () -> {
                jail.toggle(target, flag);
                boolean now = jail.get(target, flag);
                viewer.sendMessage(gui.plugin().lang().get("jail.settings-updated", flag.name(), now ? "ON" : "OFF"));
                refresh();
            });
        }
        place("unjail");
        bind("unjail", () -> {
            viewer.closeInventory();
            viewer.performCommand("unjail " + targetName);
        });
        back(() -> gui.openPlayerSelect(viewer, PlayerSelectAction.JAIL_SETTINGS));
    }
}
