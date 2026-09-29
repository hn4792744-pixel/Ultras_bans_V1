package com.ultras.bans.gui;

import com.ultras.bans.punishment.PunishmentRecord;
import com.ultras.bans.punishment.PunishmentType;
import com.ultras.bans.util.TimeUtil;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Punishment History (all types) and per-type Logs share this screen. Each punishment is an item whose material
 * reflects its type; hover shows the summary, clicking opens a Book with full details (native next-page arrows,
 * plus a clickable [Back] on the last page).
 */
public final class HistoryScreen extends Screen {

    private final UUID target;
    private final String targetName;
    private final PunishmentType type;
    private final List<PunishmentRecord> records;
    private final int page;
    private final List<Integer> slots;

    private HistoryScreen(GuiManager gui, Player viewer, UUID target, String targetName, PunishmentType type,
                          List<PunishmentRecord> records, int page) {
        super(gui, viewer, type == null ? "history" : "logs");
        this.target = target;
        this.targetName = targetName;
        this.type = type;
        this.records = records;
        this.page = page;
        ItemSpec e = layout.item("entry");
        this.slots = e == null ? List.of() : e.slots;
        placeholders.put("name", targetName);
        placeholders.put("type", type == null ? "ALL" : type.name());
        placeholders.put("page", String.valueOf(page + 1));
        placeholders.put("pages", String.valueOf(Math.max(1, (int) Math.ceil(records.size() / (double) Math.max(1, slots.size())))));
    }

    public static void show(GuiManager gui, Player viewer, UUID target, String targetName, PunishmentType type, int page) {
        CompletableFuture<List<PunishmentRecord>> f = type == null
                ? gui.plugin().punishmentService().historyOf(target)
                : gui.plugin().punishmentService().logsOf(target, type, 500);
        f.thenAccept(list -> Bukkit.getScheduler().runTask(gui.plugin(), () -> {
            if (viewer.isOnline()) new HistoryScreen(gui, viewer, target, targetName, type, list, page).open();
        }));
    }

    @Override
    protected void render() {
        ItemSpec entry = layout.item("entry");
        int per = Math.max(1, slots.size());
        int from = page * per;
        for (int i = 0; i < per && from + i < records.size(); i++) {
            PunishmentRecord r = records.get(from + i);
            Map<String, String> ph = recordPlaceholders(r);
            Material m = materialFor(r.type());
            inv.setItem(slots.get(i), entry.render(ph, m));
        }
        if (records.isEmpty()) place("empty");
        if (page > 0) { place("previous"); bind("previous", () -> show(gui, viewer, target, targetName, type, page - 1)); }
        if ((page + 1) * per < records.size()) { place("next"); bind("next", () -> show(gui, viewer, target, targetName, type, page + 1)); }
        place("page_info");
        back(() -> gui.openPunishmentMenu(viewer, target, targetName));
    }

    private Material materialFor(PunishmentType t) {
        String name = layout.raw.getString("type-materials." + t.name(), "PAPER");
        Material m = Material.matchMaterial(name);
        return m == null ? Material.PAPER : m;
    }

    static Map<String, String> recordPlaceholders(PunishmentRecord r) {
        Map<String, String> ph = new HashMap<>();
        ph.put("id", String.valueOf(r.id()));
        ph.put("type", r.type().name());
        ph.put("reason", r.reason() == null ? "-" : r.reason());
        ph.put("duration", r.permanent() ? "Permanent" : TimeUtil.formatDuration(Math.max(0, r.expiresAt() - r.createdAt())));
        ph.put("status", r.status().name());
        ph.put("operator", r.operatorName() == null ? "-" : r.operatorName());
        ph.put("date", TimeUtil.formatAbsolute(r.createdAt()));
        ph.put("expires", r.permanent() ? "Never" : TimeUtil.formatAbsolute(r.expiresAt()));
        ph.put("server", r.server() == null ? "-" : r.server());
        ph.put("world", r.world() == null ? "-" : r.world());
        ph.put("location", String.format("%.0f / %.0f / %.0f", r.x(), r.y(), r.z()));
        ph.put("source", r.source() == null ? "-" : r.source().name());
        ph.put("ip", r.ip() == null ? "-" : r.ip());
        ph.put("uuid", r.playerUuid() == null ? "-" : r.playerUuid().toString());
        ph.put("operator_uuid", r.operatorUuid() == null ? "-" : r.operatorUuid().toString());
        ph.put("resolved", r.resolvedAt() > 0 ? TimeUtil.formatAbsolute(r.resolvedAt()) : "-");
        ph.put("resolved_by", r.resolvedByName() == null ? "-" : r.resolvedByName());
        return ph;
    }

    @Override
    protected boolean onDynamicClick(int slot, InventoryClickEvent e) {
        int idx = slots.indexOf(slot);
        if (idx < 0) return false;
        int abs = page * Math.max(1, slots.size()) + idx;
        if (abs >= records.size()) return true;
        gui.play(viewer, "click");
        openBook(records.get(abs));
        return true;
    }

    private void openBook(PunishmentRecord r) {
        Map<String, String> ph = recordPlaceholders(r);
        List<String> lines = new ArrayList<>();
        for (String l : layout.raw.getStringList("book.lines")) lines.add(ItemSpec.fill(l, ph));
        int perPage = Math.max(4, layout.raw.getInt("book.lines-per-page", 12));
        LegacyComponentSerializer ser = LegacyComponentSerializer.legacyAmpersand();
        List<Component> pages = new ArrayList<>();
        for (int i = 0; i < lines.size(); i += perPage) {
            String text = String.join("\n", lines.subList(i, Math.min(lines.size(), i + perPage)));
            pages.add(ser.deserialize(text));
        }
        if (pages.isEmpty()) pages.add(Component.text("-"));
        String cmd = "/ultrasbans gui " + (type == null ? "history" : "logs") + " " + target + " " + (type == null ? "ALL" : type.name());
        Component back = ser.deserialize(layout.raw.getString("book.back", "&c[Back]")).clickEvent(ClickEvent.runCommand(cmd));
        int last = pages.size() - 1;
        pages.set(last, pages.get(last).append(Component.newline()).append(Component.newline()).append(back));
        viewer.closeInventory();
        viewer.openBook(Book.book(Component.text("Punishment #" + r.id()), Component.text("ULTRAS"), pages));
    }
}
