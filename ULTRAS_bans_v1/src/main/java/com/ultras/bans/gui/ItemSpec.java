package com.ultras.bans.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One configurable GUI button, parsed from a gui/*.yml "items.<id>" section.
 * Supported keys: enabled, slot / slots, material, name, lore, sound, command, permission, action, custom-model-data, amount, glow.
 */
public final class ItemSpec {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public final String id;
    public final boolean enabled;
    public final List<Integer> slots;
    public final Material material;
    public final String name;
    public final List<String> lore;
    public final String sound;
    public final String command;
    public final String permission;
    public final String action;
    public final int customModelData;
    public final int amount;
    public final boolean glow;

    public ItemSpec(String id, ConfigurationSection s) {
        this.id = id;
        this.enabled = s.getBoolean("enabled", true);
        List<Integer> sl = new ArrayList<>();
        if (s.isList("slots")) sl.addAll(s.getIntegerList("slots"));
        else if (s.contains("slot")) sl.add(s.getInt("slot"));
        this.slots = sl;
        Material m = Material.matchMaterial(s.getString("material", "STONE"));
        this.material = m == null || m.isAir() ? Material.STONE : m;
        this.name = s.getString("name", " ");
        this.lore = s.getStringList("lore");
        this.sound = s.getString("sound", "");
        this.command = s.getString("command", "");
        this.permission = s.getString("permission", "");
        this.action = s.getString("action", "");
        this.customModelData = s.getInt("custom-model-data", 0);
        this.amount = Math.max(1, Math.min(64, s.getInt("amount", 1)));
        this.glow = s.getBoolean("glow", false);
    }

    public ItemStack render(Map<String, String> ph) {
        return render(ph, material);
    }

    public ItemStack render(Map<String, String> ph, Material override) {
        ItemStack item = new ItemStack(override, amount);
        decorate(item, ph);
        return item;
    }

    public void decorate(ItemStack item, Map<String, String> ph) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.displayName(component(fill(name, ph)));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) {
            for (String part : fill(l, ph).split("\n", -1)) lines.add(component(part));
        }
        if (!lines.isEmpty()) meta.lore(lines);
        if (customModelData > 0) meta.setCustomModelData(customModelData);
        meta.addItemFlags(ItemFlag.values());
        if (glow) meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        item.setItemMeta(meta);
    }

    public static Component component(String legacy) {
        return LEGACY.deserialize(legacy).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String fill(String text, Map<String, String> ph) {
        if (text == null) return "";
        if (ph != null) for (Map.Entry<String, String> e : ph.entrySet()) text = text.replace("{" + e.getKey() + "}", e.getValue() == null ? "-" : e.getValue());
        return text;
    }
}
