package com.ultras.bans.manager;

import com.ultras.bans.punishment.PunishmentHook;
import com.ultras.bans.punishment.PunishmentRecord;
import com.ultras.bans.punishment.PunishmentServiceImpl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Event-driven expiry: one delayed task per active temporary record on a single daemon thread
 * (no per-tick or polling loops). Registered as a PunishmentHook so new temp punishments schedule
 * themselves and lifted ones cancel. The only place ACTIVE -> EXPIRED is triggered.
 */
public final class ExpirationScheduler implements PunishmentHook {

    private final PunishmentServiceImpl service;
    private final Logger logger;
    private final ScheduledThreadPoolExecutor executor;
    private final Map<Long, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    public ExpirationScheduler(PunishmentServiceImpl service, Logger logger) {
        this.service = service;
        this.logger = logger;
        this.executor = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "ULTRASbans-Expiry");
            t.setDaemon(true);
            return t;
        });
        this.executor.setRemoveOnCancelPolicy(true);
    }

    public void scheduleAll(List<PunishmentRecord> records) {
        for (PunishmentRecord r : records) schedule(r);
    }

    private void schedule(PunishmentRecord r) {
        if (r.permanent() || r.expiresAt() <= 0 || r.id() < 0) return;
        long delay = Math.max(0, r.expiresAt() - System.currentTimeMillis());
        ScheduledFuture<?> old = tasks.remove(r.id());
        if (old != null) old.cancel(false);
        long id = r.id();
        tasks.put(id, executor.schedule(() -> {
            tasks.remove(id);
            service.expire(id);
        }, delay, TimeUnit.MILLISECONDS));
    }

    @Override
    public void onApplied(PunishmentRecord record) {
        schedule(record);
    }

    @Override
    public void onLifted(PunishmentRecord record) {
        ScheduledFuture<?> f = tasks.remove(record.id());
        if (f != null) f.cancel(false);
    }

    public void shutdown() {
        executor.shutdownNow();
        tasks.clear();
    }
}
