package com.ultras.bans.gui;

import com.ultras.bans.model.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Paginated all-players selector (online + offline, everyone who ever joined) with player heads, hover info,
 * search and back. What a click does depends on the {@link PlayerSelectAction} the screen was opened for.
 */
public final class PlayersScreen extends Screen {

    record Page(List<PlayerProfile> profiles, int total) { }

    private final PlayerSelectAction action;
    private final int page;
    private final String search;
    private final Page data;
    private final List<Integer> headSlots;

    private PlayersScreen(GuiManager gui, Player viewer, PlayerSelectAction action, int page, String search, Page data) {
        super(gui, viewer, "players");
        this.action = action;
        this.page = page;
        this.search = search;
        this.data = data;
        ItemSpec head = layout.item("head");
        this.headSlots = head == null ? List.of() : head.slots;
        placeholders.put("page", String.valueOf(page + 1));
        placeholders.put("pages", String.valueOf(Math.max(1, (int) Math.ceil(data.total() / (double) Math.max(1, headSlots.size())))));
        placeholders.put("total", String.valueOf(data.total()));
        placeholders.put("search", search == null ? "-" : search);
    }

    /** Async-loads one page then opens the screen on the main thread. */
    public static void show(GuiManager gui, Player viewer, PlayerSelectAction action, int page, String search) {
        GuiLayout l = gui.layout("players");
        ItemSpec head = l.item("head");
        int size = Math.max(1, head == null ? 28 : head.slots.size());
        int offset = page * size;
        CompletableFuture<Page> f = load(gui, action, offset, size, search);
        f.thenAccept(p -> Bukkit.getScheduler().runTask(gui.plugin(), () -> {
            if (!viewer.isOnline()) return;
            new PlayersScreen(gui, viewer, action, page, search, p).open();
        })).exceptionally(ex -> {
            gui.plugin().getLogger().warning("Failed to load player list: " + ex);
            return null;
        });
    }

    private static CompletableFuture<Page> load(GuiManager gui, PlayerSelectAction action, int offset, int size, String search) {
        var repo = gui.plugin().playerRepository();
        boolean hasSearch = search != null && !search.isBlank();

        if (action == PlayerSelectAction.TELEPORT || action == PlayerSelectAction.GAMEMODE) {
            List<PlayerProfile> all = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (hasSearch && !p.getName().toLowerCase().contains(search.toLowerCase())) continue;
                all.add(new PlayerProfile(p.getUniqueId(), p.getName()));
            }
            return CompletableFuture.completedFuture(slice(all, offset, size));
        }
        if (action == PlayerSelectAction.JAIL_SETTINGS) {
            List<UUID> ids = gui.plugin().jail().jailedUuids();
            List<CompletableFuture<PlayerProfile>> futures = new ArrayList<>();
            for (UUID id : ids) futures.add(repo.findByUuid(id));
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(v -> {
                List<PlayerProfile> all = new ArrayList<>();
                for (int i = 0; i < futures.size(); i++) {
                    PlayerProfile p = futures.get(i).join();
                    if (p == null) p = new PlayerProfile(ids.get(i), ids.get(i).toString().substring(0, 8));
                    if (hasSearch && !p.username().toLowerCase().contains(search.toLowerCase())) continue;
                    all.add(p);
                }
                return slice(all, offset, size);
            });
        }
        if (hasSearch) {
            return repo.search(search, size, offset).thenCombine(repo.countSearch(search), Page::new);
        }
        return repo.findAll(size, offset).thenCombine(repo.countAll(), Page::new);
    }

    private static Page slice(List<PlayerProfile> all, int offset, int size) {
        int from = Math.min(offset, all.size());
        int to = Math.min(all.size(), from + size);
        return new Page(new ArrayList<>(all.subList(from, to)), all.size());
    }

    @Override
    protected void render() {
        ItemSpec head = layout.item("head");
        for (int i = 0; i < data.profiles().size() && i < headSlots.size(); i++) {
            PlayerProfile p = data.profiles().get(i);
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            if (skull.getItemMeta() instanceof SkullMeta meta) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(p.uuid()));
                skull.setItemMeta(meta);
            }
            head.decorate(skull, PlayerInfo.placeholders(gui.plugin(), p, p.uuid(), p.username()));
            inv.setItem(headSlots.get(i), skull);
        }
        if (page > 0) { place("previous"); bind("previous", () -> show(gui, viewer, action, page - 1, search)); }
        boolean hasNext = (page + 1) * Math.max(1, headSlots.size()) < data.total();
        if (hasNext) { place("next"); bind("next", () -> show(gui, viewer, action, page + 1, search)); }
        place("search");
        bind("search", () -> gui.beginSearch(viewer, action));
        if (search != null) {
            place("clear_search");
            bind("clear_search", () -> show(gui, viewer, action, 0, null));
        }
        place("page_info");
        back(viewer::closeInventory);
    }

    @Override
    protected boolean onDynamicClick(int slot, InventoryClickEvent e) {
        int idx = headSlots.indexOf(slot);
        if (idx < 0) return false;
        if (idx >= data.profiles().size()) return true;
        PlayerProfile p = data.profiles().get(idx);
        gui.play(viewer, "click");
        gui.dispatchSelect(viewer, action, p.uuid(), p.username());
        return true;
    }
}
