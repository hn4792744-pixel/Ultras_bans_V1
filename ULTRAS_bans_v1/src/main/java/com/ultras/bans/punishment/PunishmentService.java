package com.ultras.bans.punishment;

import com.ultras.bans.model.LocationSnapshot;
import com.ultras.bans.model.OperatorInfo;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Central punishment engine (spec section 59). Every punishment type flows
 * through the same apply/lift/query methods so conflict rules (section 6) and
 * logging/Discord/security hooks are enforced exactly once, in one place,
 * instead of being duplicated per command.
 * <p>
 * durationMillis == null means permanent. All methods are async; nothing here
 * touches the database or network on the calling thread.
 */
public interface PunishmentService {

    // ---- slot punishments (ban / ip_ban / mute / jail / freeze) ----

    CompletableFuture<PunishmentResult> ban(UUID targetUuid, String targetName, Long durationMillis,
                                             String reason, OperatorInfo operator, PunishmentSource source,
                                             LocationSnapshot location);

    CompletableFuture<PunishmentResult> unban(UUID targetUuid, OperatorInfo operator, PunishmentSource source, String reason);

    CompletableFuture<PunishmentResult> ipBan(String ip, UUID targetUuidOrNull, String targetNameOrNull,
                                               Long durationMillis, String reason, OperatorInfo operator,
                                               PunishmentSource source, LocationSnapshot location);

    CompletableFuture<PunishmentResult> unIpBan(String ip, OperatorInfo operator, PunishmentSource source, String reason);

    CompletableFuture<PunishmentResult> mute(UUID targetUuid, String targetName, Long durationMillis,
                                              String reason, OperatorInfo operator, PunishmentSource source,
                                              LocationSnapshot location);

    CompletableFuture<PunishmentResult> unmute(UUID targetUuid, OperatorInfo operator, PunishmentSource source, String reason);

    CompletableFuture<PunishmentResult> jail(UUID targetUuid, String targetName, String jailName,
                                              OperatorInfo operator, PunishmentSource source, LocationSnapshot location);

    CompletableFuture<PunishmentResult> unjail(UUID targetUuid, OperatorInfo operator, PunishmentSource source, String reason);

    CompletableFuture<PunishmentResult> freeze(UUID targetUuid, String targetName, OperatorInfo operator,
                                                PunishmentSource source, LocationSnapshot location);

    CompletableFuture<PunishmentResult> unfreeze(UUID targetUuid, OperatorInfo operator, PunishmentSource source, String reason);

    // ---- non-slot / momentary actions ----

    CompletableFuture<PunishmentResult> kick(UUID targetUuid, String targetName, String reason,
                                              OperatorInfo operator, PunishmentSource source, LocationSnapshot location);

    CompletableFuture<PunishmentResult> warn(UUID targetUuid, String targetName, Long durationMillisOrNull,
                                              String reason, OperatorInfo operator, PunishmentSource source,
                                              LocationSnapshot location);

    CompletableFuture<PunishmentResult> removeWarning(long warningId, OperatorInfo operator, PunishmentSource source);

    CompletableFuture<Integer> removeAllWarnings(UUID targetUuid, OperatorInfo operator, PunishmentSource source);

    CompletableFuture<PunishmentResult> setVanish(UUID targetUuid, String targetName, boolean vanished,
                                                   OperatorInfo operator, PunishmentSource source, String reasonOrNull);

    // ---- bulk / queries ----

    CompletableFuture<Integer> clearAll(UUID targetUuid, OperatorInfo operator, PunishmentSource source);

    CompletableFuture<List<PunishmentRecord>> activeOf(UUID targetUuid, PunishmentType type);

    CompletableFuture<List<PunishmentRecord>> historyOf(UUID targetUuid);

    CompletableFuture<List<PunishmentRecord>> logsOf(UUID targetUuid, PunishmentType typeOrNull, int limit);

    CompletableFuture<List<PunishmentRecord>> recent(PunishmentType type, int limit);

    /** Fast in-memory check usable on the main thread (e.g. login/chat/move listeners). Backed by cache, not a DB call. */
    boolean isCachedActive(UUID playerUuid, PunishmentType type);

    /** Cached active mute/ban lookup by IP for join-time checks before a UUID cache entry may exist. */
    boolean isIpCachedActive(String ip, PunishmentType type);
}
