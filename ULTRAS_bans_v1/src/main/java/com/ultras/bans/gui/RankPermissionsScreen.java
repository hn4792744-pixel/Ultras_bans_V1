package com.ultras.bans.gui;

import com.ultras.bans.manager.PermissionService;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Command permission editor for one rank (spec section 23). */
public final class RankPermissionsScreen extends Screen {

    private final String rank;
    private final List<PermissionService.Entry> entries;
    private final List<Integer> slots;

    public RankPermissionsScreen(GuiManager gui, Player viewer, String rank) {
        super(gui, viewer, "permissions");
        this.rank = rank;
        this.entries = new ArrayList<>(gui.plugin().permissions().commands());
        ItemSpec e = layout.item("entry");
        this.slots = e == null ? List.of() : e.slots;
        placeholders.put("name", "&f" + rank.toUpperCase());
    }

    @Override
    protected void render() {
        place("info");
        ItemSpec entry = layout.item("entry");
        PermissionService svc = gui.plugin().permissions();
        for (int i = 0; i < entries.size() && i < slots.size(); i++) {
            PermissionService.Entry e = entries.get(i);
            Boolean v = svc.rankSetting(rank, e.key());
            String state = v == null ? "&7—" : v ? "&aᴏɴ" : "&cᴏғғ";
            inv.setItem(slots.get(i), entry.render(Map.of("command", e.key(), "description", e.description(), "state", state), e.material()));
        }
        back(() -> new RanksScreen(gui, viewer).open());
    }

    @Override
    protected boolean onDynamicClick(int slot, org.bukkit.event.inventory.InventoryClickEvent e) {
        int idx = slots.indexOf(slot);
        if (idx < 0 || idx >= entries.size()) return false;
        PermissionService.Entry entry = entries.get(idx);
        PermissionService svc = gui.plugin().permissions();
        Boolean cur = svc.rankSetting(rank, entry.key());
        Boolean next = cur == null ? Boolean.TRUE : cur ? Boolean.FALSE : null;
        svc.setRank(rank, entry.key(), next);
        viewer.sendMessage(gui.plugin().lang().get("admin.rank-permission-updated", rank, entry.key(),
                next == null ? "default" : next ? "TRUE" : "FALSE"));
        gui.play(viewer, "click");
        refresh();
        return true;
    }
}
