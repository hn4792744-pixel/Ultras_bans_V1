package com.ultras.bans.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Provides a stable {@link ReentrantLock} per string key so that, e.g., two
 * staff members clicking "Ban" on the same player at the same millisecond
 * cannot both pass the conflict check before either has persisted their
 * record. Locks are never removed (punishment keys are low-cardinality:
 * per-player-per-type), so memory growth is bounded by the player base size.
 */
public final class KeyedLockRegistry {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ReentrantLock lockFor(String key) {
        return locks.computeIfAbsent(key, k -> new ReentrantLock());
    }
}
