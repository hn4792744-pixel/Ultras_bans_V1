package com.ultras.bans.manager;

import com.ultras.bans.punishment.PunishmentRecord;
import com.ultras.bans.punishment.PunishmentType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache of currently-ACTIVE single-slot punishments, keyed by
 * player UUID and by raw IP. Populated on startup from
 * {@link com.ultras.bans.punishment.PunishmentRepository#findAllActiveTemporary()}
 * plus a full active-permanent scan, and kept in sync by
 * {@link com.ultras.bans.punishment.PunishmentServiceImpl} on every apply/lift.
 * <p>
 * This exists so hot paths (login, chat, move) never need to hit the database
 * synchronously. It intentionally only tracks single-slot types (see
 * {@link PunishmentType#isSingleSlot()}) - WARN history is queried on demand.
 */
public final class PunishmentCache {

    private final Map<UUID, Map<PunishmentType, PunishmentRecord>> byPlayer = new ConcurrentHashMap<>();
    private final Map<String, Map<PunishmentType, PunishmentRecord>> byIp = new ConcurrentHashMap<>();

    public PunishmentRecord getActive(UUID playerUuid, PunishmentType type) {
        Map<PunishmentType, PunishmentRecord> map = byPlayer.get(playerUuid);
        return map == null ? null : map.get(type);
    }

    public PunishmentRecord getActiveByIp(String ip, PunishmentType type) {
        Map<PunishmentType, PunishmentRecord> map = byIp.get(normalizeIp(ip));
        return map == null ? null : map.get(type);
    }

    public void putActive(PunishmentRecord record) {
        if (record.playerUuid() != null) {
            byPlayer.computeIfAbsent(record.playerUuid(), k -> new ConcurrentHashMap<>())
                    .put(record.type(), record);
        }
        if (record.ip() != null) {
            byIp.computeIfAbsent(normalizeIp(record.ip()), k -> new ConcurrentHashMap<>())
                    .put(record.type(), record);
        }
    }

    public void removeActive(PunishmentRecord record) {
        if (record.playerUuid() != null) {
            Map<PunishmentType, PunishmentRecord> map = byPlayer.get(record.playerUuid());
            if (map != null) map.remove(record.type(), record);
        }
        if (record.ip() != null) {
            Map<PunishmentType, PunishmentRecord> map = byIp.get(normalizeIp(record.ip()));
            if (map != null) map.remove(record.type(), record);
        }
    }

    public boolean isActive(UUID playerUuid, PunishmentType type) {
        return getActive(playerUuid, type) != null;
    }

    public boolean isIpActive(String ip, PunishmentType type) {
        return getActiveByIp(ip, type) != null;
    }

    public void clearAllFor(UUID playerUuid) {
        byPlayer.remove(playerUuid);
    }

    private static String normalizeIp(String ip) {
        return ip == null ? null : ip.trim();
    }
}
