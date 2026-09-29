package com.ultras.bans.database;

import com.ultras.bans.model.PlayerProfile;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerRepository {

    /** Insert-or-update the full profile row. */
    CompletableFuture<Void> upsert(PlayerProfile profile);

    CompletableFuture<PlayerProfile> findByUuid(UUID uuid);

    /** Case-insensitive lookup by most recently used username - used to resolve offline-player command arguments. */
    CompletableFuture<PlayerProfile> findByName(String username);

    /** Every profile whose last known IP matches - used by /ip_list and IP-ban target resolution. */
    CompletableFuture<List<PlayerProfile>> findByIp(String ip);

    /** All known profiles, for the Player Selection GUI's "every player who has ever joined" listing. Paginated by caller. */
    CompletableFuture<List<PlayerProfile>> findAll(int limit, int offset);

    CompletableFuture<Integer> countAll();

    /** Name-contains search (case-insensitive) for the GUI search feature. */
    CompletableFuture<List<PlayerProfile>> search(String nameContains, int limit, int offset);

    CompletableFuture<Integer> countSearch(String nameContains);
}
