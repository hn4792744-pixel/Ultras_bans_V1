package com.ultras.bans.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/** A parsed gui/&lt;name&gt;.yml: title, size, filler and every configurable item. */
public final class GuiLayout {

    public final String name;
    public final String title;
    public final int size;
    public final boolean fillerEnabled;
    public final boolean borderOnly;
    public final ItemSpec filler;
    public final Map<String, ItemSpec> items = new LinkedHashMap<>();
    public final YamlConfiguration raw;

    public GuiLayout(String name, File file) {
        this.name = name;
        this.raw = YamlConfiguration.loadConfiguration(file);
        this.title = raw.getString("title", "&cᴜʟᴛʀᴀs");
        int sz = raw.getInt("size", 54);
        this.size = Math.max(9, Math.min(54, (sz / 9) * 9));
        ConfigurationSection f = raw.getConfigurationSection("filler");
        this.fillerEnabled = f != null && f.getBoolean("enabled", true);
        this.borderOnly = f != null && f.getBoolean("border-only", true);
        ConfigurationSection fs = f != null ? f : new YamlConfiguration();
        YamlConfiguration tmp = new YamlConfiguration();
        tmp.set("material", fs.getString("material", "BLACK_STAINED_GLASS_PANE"));
        tmp.set("name", fs.getString("name", " "));
        this.filler = new ItemSpec("filler", tmp);
        ConfigurationSection is = raw.getConfigurationSection("items");
        if (is != null) {
            for (String id : is.getKeys(false)) {
                ConfigurationSection sec = is.getConfigurationSection(id);
                if (sec != null) items.put(id, new ItemSpec(id, sec));
            }
        }
    }

    public ItemSpec item(String id) { return items.get(id); }

    public Component titleComponent(Map<String, String> ph) {
        return ItemSpec.component(ItemSpec.fill(title, ph));
    }

    /** Fills empty slots with the filler pane (whole inventory, or only the outer border rows/columns). */
    public void applyFiller(Inventory inv) {
        if (!fillerEnabled) return;
        ItemStack pane = filler.render(Map.of());
        if (pane.getType() == Material.AIR) return;
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) != null) continue;
            boolean edge = i < 9 || i >= inv.getSize() - 9 || i % 9 == 0 || i % 9 == 8;
            if (!borderOnly || edge) inv.setItem(i, pane);
        }
    }

    /** Places a spec's item in all its configured slots (skips disabled items). */
    public void place(Inventory inv, ItemSpec spec, Map<String, String> ph, Material override) {
        if (spec == null || !spec.enabled) return;
        for (int slot : spec.slots) {
            if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, override == null ? spec.render(ph) : spec.render(ph, override));
        }
    }

    public void place(Inventory inv, String id, Map<String, String> ph) {
        place(inv, item(id), ph, null);
    }

    /** Returns the spec whose slot list contains the clicked slot (or null). */
    public ItemSpec at(int slot) {
        for (ItemSpec s : items.values()) if (s.enabled && s.slots.contains(slot)) return s;
        return null;
    }
}
