package com.ultras.bans.punishment;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Persistence contract for punishment records. Every method returns a
 * {@link CompletableFuture} and MUST NOT block the calling thread - the
 * implementation (database/PunishmentRepositoryJdbc) runs all JDBC work on the
 * database executor (see database/DatabaseExecutor).
 */
public interface PunishmentRepository {

    CompletableFuture<PunishmentRecord> insert(PunishmentRecord record);

    CompletableFuture<Void> update(PunishmentRecord record);

    CompletableFuture<PunishmentRecord> findById(long id);

    /** All records for a player, newest first. */
    CompletableFuture<List<PunishmentRecord>> findByPlayer(UUID playerUuid);

    /** All records for a raw IP, newest first. */
    CompletableFuture<List<PunishmentRecord>> findByIp(String ip);

    /** Currently ACTIVE records of a given type for a player (0 or 1 for single-slot types). */
    CompletableFuture<List<PunishmentRecord>> findActiveByPlayerAndType(UUID playerUuid, PunishmentType type);

    CompletableFuture<List<PunishmentRecord>> findActiveByIpAndType(String ip, PunishmentType type);

    /** Every ACTIVE record (permanent and temporary) to warm the cache on startup. WARN is excluded (multi-slot). */
    CompletableFuture<List<PunishmentRecord>> findAllActive();

    /** All records currently ACTIVE and temporary, used to seed the expiration scheduler on startup. */
    CompletableFuture<List<PunishmentRecord>> findAllActiveTemporary();

    /** All records of a type for the "/x list" GUI/command, newest first, capped by limit. */
    CompletableFuture<List<PunishmentRecord>> findRecentByType(PunishmentType type, int limit);

    /** Logs for a specific player across all types (or a single type if provided). */
    CompletableFuture<List<PunishmentRecord>> findLogs(UUID playerUuid, PunishmentType typeOrNull, int limit);
}
