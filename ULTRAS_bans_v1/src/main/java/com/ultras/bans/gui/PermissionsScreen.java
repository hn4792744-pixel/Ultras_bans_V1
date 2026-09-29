package com.ultras.bans.gui;

import com.ultras.bans.manager.PermissionService;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Per-player command permission toggles: each command is a button, click cycles its override TRUE/FALSE/(unset). */
public final class PermissionsScreen extends Screen {

    private final UUID target;
    private final String targetName;
    private final List<PermissionService.Entry> entries;
    private final List<Integer> slots;

    public PermissionsScreen(GuiManager gui, Player viewer, UUID target, String targetName) {
        super(gui, viewer, "permissions");
        this.target = target;
        this.targetName = targetName;
        this.entries = new ArrayList<>(gui.plugin().permissions().commands());
        ItemSpec e = layout.item("entry");
        this.slots = e == null ? List.of() : e.slots;
        placeholders.put("name", targetName);
    }

    @Override
    protected void render() {
        place("info");
        ItemSpec entry = layout.item("entry");
        PermissionService svc = gui.plugin().permissions();
        for (int i = 0; i < entries.size() && i < slots.size(); i++) {
            PermissionService.Entry e = entries.get(i);
            Boolean override = svc.playerOverride(target, e.key());
            String state = override == null ? "&7—" : override ? "&aᴏɴ" : "&cᴏғғ";
            Map<String, String> ph = Map.of("command", e.key(), "description", e.description(), "state", state);
            inv.setItem(slots.get(i), entry.render(ph, e.material()));
        }
        back(() -> gui.openPlayerSelect(viewer, PlayerSelectAction.PERMISSIONS));
    }

    @Override
    protected boolean onDynamicClick(int slot, org.bukkit.event.inventory.InventoryClickEvent e) {
        int idx = slots.indexOf(slot);
        if (idx < 0 || idx >= entries.size()) return false;
        PermissionService.Entry entry = entries.get(idx);
        PermissionService svc = gui.plugin().permissions();
        Boolean cur = svc.playerOverride(target, entry.key());
        Boolean next = cur == null ? Boolean.TRUE : cur ? Boolean.FALSE : null;
        svc.setPlayer(target, entry.key(), next);
        viewer.sendMessage(gui.plugin().lang().get("admin.permission-updated", entry.key(),
                next == null ? "default" : next ? "TRUE" : "FALSE", targetName));
        gui.play(viewer, "click");
        refresh();
        return true;
    }
}
