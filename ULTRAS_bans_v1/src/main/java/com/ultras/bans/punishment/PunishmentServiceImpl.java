package com.ultras.bans.punishment;

import com.ultras.bans.manager.PunishmentCache;
import com.ultras.bans.model.LocationSnapshot;
import com.ultras.bans.model.OperatorInfo;
import com.ultras.bans.util.KeyedLockRegistry;
import com.ultras.bans.util.TimeUtil;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default {@link PunishmentService} implementation. Every apply/lift operation:
 * 1. Acquires a per-(key,type) lock so concurrent requests can't race past the conflict check.
 * 2. Checks the in-memory cache for an existing ACTIVE record of that (conflicting) type.
 * 3. If one exists and this is an "apply" call -> returns ALREADY_PERMANENT / ALREADY_TEMPORARY,
 *    the record is NEVER overwritten or upgraded/downgraded (spec section 6).
 * 4. Otherwise persists the change, updates the cache, and fires {@link PunishmentHook}s.
 */
public final class PunishmentServiceImpl implements PunishmentService {

    private final PunishmentRepository repository;
    private final PunishmentCache cache;
    private final Executor asyncExecutor;
    private final KeyedLockRegistry locks = new KeyedLockRegistry();
    private final List<PunishmentHook> hooks = new CopyOnWriteArrayList<>();
    private final Logger logger;

    public PunishmentServiceImpl(PunishmentRepository repository, PunishmentCache cache,
                                  Executor asyncExecutor, Logger logger) {
        this.repository = repository;
        this.cache = cache;
        this.asyncExecutor = asyncExecutor;
        this.logger = logger;
    }

    public void registerHook(PunishmentHook hook) {
        hooks.add(hook);
    }

    // -------------------------------------------------------------------------------------
    // Generic slot-punishment apply/lift used by ban/mute/jail/freeze/ip-ban/vanish
    // -------------------------------------------------------------------------------------

    private CompletableFuture<PunishmentResult> applySlot(UUID playerUuid, String ip, PunishmentType type,
                                                            Long durationMillis, String reason, OperatorInfo operator,
                                                            PunishmentSource source, LocationSnapshot location,
                                                            String targetNameForRecord) {
        return applySlot(playerUuid, playerUuid, ip, type, durationMillis, reason, operator, source, location, targetNameForRecord);
    }

    /** keyUuid decides the conflict slot; recordUuid is stored on the record (IP bans keep the target's UUID for history). */
    private CompletableFuture<PunishmentResult> applySlot(UUID keyUuid, UUID recordUuid, String ip, PunishmentType type,
                                                            Long durationMillis, String reason, OperatorInfo operator,
                                                            PunishmentSource source, LocationSnapshot location,
                                                            String targetNameForRecord) {
        UUID playerUuid = keyUuid;
        String lockKey = lockKey(playerUuid, ip, type);
        return CompletableFuture.supplyAsync(() -> {
            ReentrantLock lock = locks.lockFor(lockKey);
            lock.lock();
            try {
                PunishmentRecord existing = playerUuid != null
                        ? cache.getActive(playerUuid, type)
                        : cache.getActiveByIp(ip, type);

                if (existing != null) {
                    if (existing.permanent()) {
                        return PunishmentResult.conflict(PunishmentResult.Outcome.ALREADY_PERMANENT,
                                "punishments.already-permanent", existing, type.name());
                    } else {
                        return PunishmentResult.conflict(PunishmentResult.Outcome.ALREADY_TEMPORARY,
                                "punishments.already-temporary", existing, type.name(),
                                TimeUtil.formatAbsolute(existing.expiresAt()));
                    }
                }

                boolean permanent = durationMillis == null;
                long now = System.currentTimeMillis();
                long expiresAt = permanent ? -1 : now + durationMillis;

                PunishmentRecord.Builder builder = PunishmentRecord.builder()
                        .playerUuid(recordUuid)
                        .playerName(targetNameForRecord)
                        .ip(ip)
                        .type(type)
                        .permanent(permanent)
                        .createdAt(now)
                        .expiresAt(expiresAt)
                        .reason(reason)
                        .operatorUuid(operator.uuid())
                        .operatorName(operator.name())
                        .server(location.server())
                        .world(location.world())
                        .location(location.x(), location.y(), location.z())
                        .source(source)
                        .status(PunishmentStatus.ACTIVE);

                PunishmentRecord record = repository.insert(builder.build()).join();
                cache.putActive(record);
                fireApplied(record);
                return PunishmentResult.success(record, "punishments.applied", type.name());
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Failed to apply " + type + " for " + targetNameForRecord, ex);
                return PunishmentResult.of(PunishmentResult.Outcome.FAILURE, "punishments.error");
            } finally {
                lock.unlock();
            }
        }, asyncExecutor);
    }

    private CompletableFuture<PunishmentResult> liftSlot(UUID playerUuid, String ip, PunishmentType type,
                                                           OperatorInfo operator, PunishmentSource source, String liftReason) {
        String lockKey = lockKey(playerUuid, ip, type);
        return CompletableFuture.supplyAsync(() -> {
            ReentrantLock lock = locks.lockFor(lockKey);
            lock.lock();
            try {
                PunishmentRecord existing = playerUuid != null
                        ? cache.getActive(playerUuid, type)
                        : cache.getActiveByIp(ip, type);

                if (existing == null) {
                    return PunishmentResult.of(PunishmentResult.Outcome.NOT_PUNISHED, "punishments.not-punished", type.name());
                }

                existing.resolve(PunishmentStatus.REMOVED, System.currentTimeMillis(), operator.uuid(), operator.name(), liftReason);
                repository.update(existing).join();
                cache.removeActive(existing);
                fireLifted(existing);
                return PunishmentResult.success(existing, "punishments.lifted", type.name());
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Failed to lift " + type, ex);
                return PunishmentResult.of(PunishmentResult.Outcome.FAILURE, "punishments.error");
            } finally {
                lock.unlock();
            }
        }, asyncExecutor);
    }

    /** Called ONLY by ExpirationScheduler: ACTIVE -> EXPIRED for a temporary record whose time elapsed. */
    public void expire(long recordId) {
        CompletableFuture.runAsync(() -> {
            try {
                PunishmentRecord r = repository.findById(recordId).join();
                if (r == null || r.status() != PunishmentStatus.ACTIVE || r.permanent()) return;
                if (!r.isExpiredByTime(System.currentTimeMillis())) return;
                ReentrantLock lock = locks.lockFor(lockKey(r.playerUuid(), r.ip(), r.type()));
                lock.lock();
                try {
                    r.resolve(PunishmentStatus.EXPIRED, System.currentTimeMillis(), null, "SYSTEM", "Expired");
                    repository.update(r).join();
                    cache.removeActive(r);
                    fireLifted(r);
                } finally {
                    lock.unlock();
                }
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Failed to expire punishment #" + recordId, ex);
            }
        }, asyncExecutor);
    }

    private static String lockKey(UUID playerUuid, String ip, PunishmentType type) {
        if (playerUuid != null) return "p:" + playerUuid + ":" + type;
        return "ip:" + ip + ":" + type;
    }

    // ---- Ban ----

    @Override
    public CompletableFuture<PunishmentResult> ban(UUID targetUuid, String targetName, Long durationMillis,
                                                     String reason, OperatorInfo operator, PunishmentSource source,
                                                     LocationSnapshot location) {
        return applySlot(targetUuid, null, PunishmentType.BAN, durationMillis, reason, operator, source, location, targetName);
    }

    @Override
    public CompletableFuture<PunishmentResult> unban(UUID targetUuid, OperatorInfo operator, PunishmentSource source, String reason) {
        return liftSlot(targetUuid, null, PunishmentType.BAN, operator, source, reason);
    }

    // ---- IP Ban ----

    @Override
    public CompletableFuture<PunishmentResult> ipBan(String ip, UUID targetUuidOrNull, String targetNameOrNull,
                                                       Long durationMillis, String reason, OperatorInfo operator,
                                                       PunishmentSource source, LocationSnapshot location) {
        return applySlot(null, targetUuidOrNull, ip, PunishmentType.IP_BAN, durationMillis, reason, operator, source, location,
                targetNameOrNull != null ? targetNameOrNull : ip);
    }

    @Override
    public CompletableFuture<PunishmentResult> unIpBan(String ip, OperatorInfo operator, PunishmentSource source, String reason) {
        return liftSlot(null, ip, PunishmentType.IP_BAN, operator, source, reason);
    }

    // ---- Mute ----

    @Override
    public CompletableFuture<PunishmentResult> mute(UUID targetUuid, String targetName, Long durationMillis,
                                                      String reason, OperatorInfo operator, PunishmentSource source,
                                                      LocationSnapshot location) {
        return applySlot(targetUuid, null, PunishmentType.MUTE, durationMillis, reason, operator, source, location, targetName);
    }

    @Override
    public CompletableFuture<PunishmentResult> unmute(UUID targetUuid, OperatorInfo operator, PunishmentSource source, String reason) {
        return liftSlot(targetUuid, null, PunishmentType.MUTE, operator, source, reason);
    }

    // ---- Jail ----

    @Override
    public CompletableFuture<PunishmentResult> jail(UUID targetUuid, String targetName, String jailName,
                                                      OperatorInfo operator, PunishmentSource source, LocationSnapshot location) {
        return applySlot(targetUuid, null, PunishmentType.JAIL, null, "Jailed: " + jailName, operator, source, location, targetName);
    }

    @Override
    public CompletableFuture<PunishmentResult> unjail(UUID targetUuid, OperatorInfo operator, PunishmentSource source, String reason) {
        return liftSlot(targetUuid, null, PunishmentType.JAIL, operator, source, reason);
    }

    // ---- Freeze ----

    @Override
    public CompletableFuture<PunishmentResult> freeze(UUID targetUuid, String targetName, OperatorInfo operator,
                                                        PunishmentSource source, LocationSnapshot location) {
        return applySlot(targetUuid, null, PunishmentType.FREEZE, null, "Frozen by staff", operator, source, location, targetName);
    }

    @Override
    public CompletableFuture<PunishmentResult> unfreeze(UUID targetUuid, OperatorInfo operator, PunishmentSource source, String reason) {
        return liftSlot(targetUuid, null, PunishmentType.FREEZE, operator, source, reason);
    }

    // ---- Kick (momentary, still logged for history/Discord) ----

    @Override
    public CompletableFuture<PunishmentResult> kick(UUID targetUuid, String targetName, String reason,
                                                      OperatorInfo operator, PunishmentSource source, LocationSnapshot location) {
        return CompletableFuture.supplyAsync(() -> {
            long now = System.currentTimeMillis();
            PunishmentRecord record = PunishmentRecord.builder()
                    .playerUuid(targetUuid)
                    .playerName(targetName)
                    .type(PunishmentType.KICK)
                    .permanent(true)
                    .createdAt(now)
                    .expiresAt(-1)
                    .reason(reason)
                    .operatorUuid(operator.uuid())
                    .operatorName(operator.name())
                    .server(location.server())
                    .world(location.world())
                    .location(location.x(), location.y(), location.z())
                    .source(source)
                    .status(PunishmentStatus.REMOVED) // momentary: resolved the instant it's created
                    .resolvedAt(now)
                    .build();
            PunishmentRecord inserted = repository.insert(record).join();
            fireApplied(inserted);
            return PunishmentResult.success(inserted, "punishments.applied", "KICK");
        }, asyncExecutor);
    }

    // ---- Warn (multi-slot: many warnings can coexist) ----

    @Override
    public CompletableFuture<PunishmentResult> warn(UUID targetUuid, String targetName, Long durationMillisOrNull,
                                                      String reason, OperatorInfo operator, PunishmentSource source,
                                                      LocationSnapshot location) {
        return CompletableFuture.supplyAsync(() -> {
            long now = System.currentTimeMillis();
            boolean permanent = durationMillisOrNull == null;
            PunishmentRecord record = PunishmentRecord.builder()
                    .playerUuid(targetUuid)
                    .playerName(targetName)
                    .type(PunishmentType.WARN)
                    .permanent(permanent)
                    .createdAt(now)
                    .expiresAt(permanent ? -1 : now + durationMillisOrNull)
                    .reason(reason)
                    .operatorUuid(operator.uuid())
                    .operatorName(operator.name())
                    .server(location.server())
                    .world(location.world())
                    .location(location.x(), location.y(), location.z())
                    .source(source)
                    .status(PunishmentStatus.ACTIVE)
                    .build();
            PunishmentRecord inserted = repository.insert(record).join();
            fireApplied(inserted);
            return PunishmentResult.success(inserted, "punishments.applied", "WARN");
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<PunishmentResult> removeWarning(long warningId, OperatorInfo operator, PunishmentSource source) {
        return CompletableFuture.supplyAsync(() -> {
            PunishmentRecord record = repository.findById(warningId).join();
            if (record == null || record.type() != PunishmentType.WARN || record.status() != PunishmentStatus.ACTIVE) {
                return PunishmentResult.of(PunishmentResult.Outcome.NOT_PUNISHED, "punishments.not-punished", "WARN");
            }
            record.resolve(PunishmentStatus.REMOVED, System.currentTimeMillis(), operator.uuid(), operator.name(), "Removed by staff");
            repository.update(record).join();
            fireLifted(record);
            return PunishmentResult.success(record, "punishments.lifted", "WARN");
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Integer> removeAllWarnings(UUID targetUuid, OperatorInfo operator, PunishmentSource source) {
        return CompletableFuture.supplyAsync(() -> {
            List<PunishmentRecord> all = repository.findActiveByPlayerAndType(targetUuid, PunishmentType.WARN).join();
            int count = 0;
            for (PunishmentRecord r : all) {
                r.resolve(PunishmentStatus.REMOVED, System.currentTimeMillis(), operator.uuid(), operator.name(), "Cleared (remove all)");
                repository.update(r).join();
                fireLifted(r);
                count++;
            }
            return count;
        }, asyncExecutor);
    }

    // ---- Vanish (slot-like toggle, not persisted as "permanent") ----

    @Override
    public CompletableFuture<PunishmentResult> setVanish(UUID targetUuid, String targetName, boolean vanished,
                                                           OperatorInfo operator, PunishmentSource source, String reasonOrNull) {
        String lockKey = lockKey(targetUuid, null, PunishmentType.VANISH);
        return CompletableFuture.supplyAsync(() -> {
            ReentrantLock lock = locks.lockFor(lockKey);
            lock.lock();
            try {
                PunishmentRecord existing = cache.getActive(targetUuid, PunishmentType.VANISH);
                if (vanished) {
                    if (existing != null) {
                        return PunishmentResult.of(PunishmentResult.Outcome.ALREADY_PERMANENT, "vanish.already-on");
                    }
                    long now = System.currentTimeMillis();
                    PunishmentRecord record = PunishmentRecord.builder()
                            .playerUuid(targetUuid)
                            .playerName(targetName)
                            .type(PunishmentType.VANISH)
                            .permanent(true)
                            .createdAt(now)
                            .expiresAt(-1)
                            .reason(reasonOrNull == null ? "Vanish enabled" : reasonOrNull)
                            .operatorUuid(operator.uuid())
                            .operatorName(operator.name())
                            .source(source)
                            .status(PunishmentStatus.ACTIVE)
                            .build();
                    PunishmentRecord inserted = repository.insert(record).join();
                    cache.putActive(inserted);
                    fireApplied(inserted);
                    return PunishmentResult.success(inserted, "vanish.enabled");
                } else {
                    if (existing == null) {
                        return PunishmentResult.of(PunishmentResult.Outcome.NOT_PUNISHED, "vanish.already-off");
                    }
                    existing.resolve(PunishmentStatus.REMOVED, System.currentTimeMillis(), operator.uuid(), operator.name(),
                            reasonOrNull == null ? "Vanish disabled" : reasonOrNull);
                    repository.update(existing).join();
                    cache.removeActive(existing);
                    fireLifted(existing);
                    return PunishmentResult.success(existing, "vanish.disabled");
                }
            } finally {
                lock.unlock();
            }
        }, asyncExecutor);
    }

    // ---- Bulk / queries ----

    @Override
    public CompletableFuture<Integer> clearAll(UUID targetUuid, OperatorInfo operator, PunishmentSource source) {
        return CompletableFuture.supplyAsync(() -> {
            List<PunishmentRecord> history = repository.findByPlayer(targetUuid).join();
            int count = 0;
            for (PunishmentRecord r : history) {
                if (r.status() == PunishmentStatus.ACTIVE) {
                    r.resolve(PunishmentStatus.REVOKED, System.currentTimeMillis(), operator.uuid(), operator.name(), "Clear all punishments");
                    repository.update(r).join();
                    cache.removeActive(r);
                    fireLifted(r);
                    count++;
                }
            }
            cache.clearAllFor(targetUuid);
            return count;
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<List<PunishmentRecord>> activeOf(UUID targetUuid, PunishmentType type) {
        return repository.findActiveByPlayerAndType(targetUuid, type);
    }

    @Override
    public CompletableFuture<List<PunishmentRecord>> historyOf(UUID targetUuid) {
        return repository.findByPlayer(targetUuid);
    }

    @Override
    public CompletableFuture<List<PunishmentRecord>> logsOf(UUID targetUuid, PunishmentType typeOrNull, int limit) {
        return repository.findLogs(targetUuid, typeOrNull, limit);
    }

    @Override
    public CompletableFuture<List<PunishmentRecord>> recent(PunishmentType type, int limit) {
        return repository.findRecentByType(type, limit);
    }

    @Override
    public boolean isCachedActive(UUID playerUuid, PunishmentType type) {
        return cache.isActive(playerUuid, type);
    }

    @Override
    public boolean isIpCachedActive(String ip, PunishmentType type) {
        return cache.isIpActive(ip, type);
    }

    private void fireApplied(PunishmentRecord record) {
        for (PunishmentHook hook : hooks) {
            try {
                hook.onApplied(record);
            } catch (Exception ex) {
                logger.log(Level.WARNING, "PunishmentHook#onApplied threw", ex);
            }
        }
    }

    private void fireLifted(PunishmentRecord record) {
        for (PunishmentHook hook : hooks) {
            try {
                hook.onLifted(record);
            } catch (Exception ex) {
                logger.log(Level.WARNING, "PunishmentHook#onLifted threw", ex);
            }
        }
    }
}
