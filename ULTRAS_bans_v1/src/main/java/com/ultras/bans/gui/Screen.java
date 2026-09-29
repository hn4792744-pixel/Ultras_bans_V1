package com.ultras.bans.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;

/**
 * Base for every GUI screen. A screen owns its inventory, renders items from its {@link GuiLayout}, and routes
 * clicks by item id. Common per-item config (permission, command, sound, action=close) is handled here so every
 * GUI is customisable from YAML without code.
 */
public abstract class Screen implements UltrasGuiHolder {

    protected final GuiManager gui;
    protected final Player viewer;

    public Player viewerOf() { return viewer; }
    protected final GuiLayout layout;
    protected Inventory inv;
    private final Map<String, Runnable> handlers = new HashMap<>();
    protected final Map<String, String> placeholders = new HashMap<>();

    protected Screen(GuiManager gui, Player viewer, String layoutName) {
        this.gui = gui;
        this.viewer = viewer;
        this.layout = gui.layout(layoutName);
    }

    /** Builds and shows the inventory. Must be called on the main thread. */
    public final void open() {
        inv = Bukkit.createInventory(this, layout.size, layout.titleComponent(placeholders));
        render();
        layout.applyFiller(inv);
        viewer.openInventory(inv);
        gui.play(viewer, "open");
    }

    /** Re-renders in place (keeps the same inventory, so no flicker or cursor reset). */
    protected final void refresh() {
        inv.clear();
        handlers.clear();
        render();
        layout.applyFiller(inv);
        viewer.updateInventory();
    }

    protected abstract void render();

    protected final void bind(String id, Runnable r) {
        handlers.put(id, r);
    }

    protected final void place(String id) {
        layout.place(inv, id, placeholders);
    }

    protected final void place(String id, Map<String, String> extra) {
        Map<String, String> merged = new HashMap<>(placeholders);
        merged.putAll(extra);
        layout.place(inv, id, merged);
    }

    /** Standard back button behaviour: shows the item and binds the action. */
    protected final void back(Runnable action) {
        place("back");
        bind("back", () -> { gui.play(viewer, "back"); action.run(); });
    }

    public final void handleClick(InventoryClickEvent e) {
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= inv.getSize()) return;
        if (onDynamicClick(slot, e)) return;
        ItemSpec spec = layout.at(slot);
        if (spec == null) return;
        if (!spec.permission.isBlank() && !viewer.hasPermission(spec.permission)) {
            gui.play(viewer, "error");
            return;
        }
        if (!spec.sound.isBlank()) gui.playRaw(viewer, spec.sound);
        else if (!handlers.containsKey(spec.id)) gui.play(viewer, "click");
        Runnable h = handlers.get(spec.id);
        if (h != null) { h.run(); return; }
        onClick(spec, slot, e);
        if (!spec.command.isBlank()) {
            viewer.closeInventory();
            viewer.performCommand(ItemSpec.fill(spec.command, placeholders).replaceFirst("^/", ""));
        } else if (spec.action.equalsIgnoreCase("close")) {
            viewer.closeInventory();
        }
    }

    /** Hook for screens with dynamic (non-id-bound) slots such as player lists. */
    protected void onClick(ItemSpec spec, int slot, InventoryClickEvent e) { }

    /** Dynamic-slot hook used before spec lookup (player heads, history entries). Return true if handled. */
    protected boolean onDynamicClick(int slot, InventoryClickEvent e) { return false; }

    @Override
    public Inventory getInventory() { return inv; }

    @Override
    public String guiId() { return layout.name; }

    /** Called when the viewer closes the screen. */
    public void onClose() { }
}
