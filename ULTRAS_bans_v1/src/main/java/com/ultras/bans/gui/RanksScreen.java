package com.ultras.bans.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Lists configured ranks; clicking one opens its command-permission editor. */
public final class RanksScreen extends Screen {

    private final List<String> ranks;
    private final List<Integer> slots;

    public RanksScreen(GuiManager gui, Player viewer) {
        super(gui, viewer, "ranks");
        this.ranks = new ArrayList<>(gui.plugin().permissions().ranks());
        ItemSpec e = layout.item("entry");
        this.slots = e == null ? List.of() : e.slots;
    }

    @Override
    protected void render() {
        ItemSpec entry = layout.item("entry");
        for (int i = 0; i < ranks.size() && i < slots.size(); i++) {
            ItemStack item = entry.render(Map.of("rank", ranks.get(i)));
            inv.setItem(slots.get(i), item);
        }
        back(viewer::closeInventory);
    }

    @Override
    protected boolean onDynamicClick(int slot, org.bukkit.event.inventory.InventoryClickEvent e) {
        int idx = slots.indexOf(slot);
        if (idx < 0 || idx >= ranks.size()) return false;
        gui.play(viewer, "click");
        new RankPermissionsScreen(gui, viewer, ranks.get(idx)).open();
        return true;
    }
}
