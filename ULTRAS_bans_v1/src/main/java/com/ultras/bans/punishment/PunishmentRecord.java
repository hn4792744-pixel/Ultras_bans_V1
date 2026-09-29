package com.ultras.bans.punishment;

import java.util.UUID;

/**
 * A single punishment entry. Immutable once created; state transitions produce
 * a persisted UPDATE (status/expiresAt/revokedBy etc.) rather than a new row,
 * so punishment IDs remain stable references usable in commands, GUIs and
 * Discord embeds.
 */
public final class PunishmentRecord {

    private final long id;
    private final UUID playerUuid;
    private final String playerName;
    private final String ip;                 // null unless type.isIpBased() or an IP-only ban target
    private final PunishmentType type;
    private final boolean permanent;
    private final long createdAt;             // epoch millis
    private final long expiresAt;             // epoch millis, -1 if permanent
    private String reason;
    private final UUID operatorUuid;          // null for CONSOLE/SYSTEM
    private final String operatorName;
    private final String server;
    private final String world;
    private final double x, y, z;
    private final PunishmentSource source;

    private PunishmentStatus status;
    private long resolvedAt = -1;             // when it became EXPIRED/REMOVED/REVOKED
    private UUID resolvedByUuid;
    private String resolvedByName;
    private String resolvedReason;

    private PunishmentRecord(Builder b) {
        this.id = b.id;
        this.playerUuid = b.playerUuid;
        this.playerName = b.playerName;
        this.ip = b.ip;
        this.type = b.type;
        this.permanent = b.permanent;
        this.createdAt = b.createdAt;
        this.expiresAt = b.expiresAt;
        this.reason = b.reason;
        this.operatorUuid = b.operatorUuid;
        this.operatorName = b.operatorName;
        this.server = b.server;
        this.world = b.world;
        this.x = b.x;
        this.y = b.y;
        this.z = b.z;
        this.source = b.source;
        this.status = b.status;
        this.resolvedAt = b.resolvedAt;
        this.resolvedByUuid = b.resolvedByUuid;
        this.resolvedByName = b.resolvedByName;
        this.resolvedReason = b.resolvedReason;
    }

    public long id() { return id; }
    public UUID playerUuid() { return playerUuid; }
    public String playerName() { return playerName; }
    public String ip() { return ip; }
    public PunishmentType type() { return type; }
    public boolean permanent() { return permanent; }
    public long createdAt() { return createdAt; }
    public long expiresAt() { return expiresAt; }
    public String reason() { return reason; }
    public UUID operatorUuid() { return operatorUuid; }
    public String operatorName() { return operatorName; }
    public String server() { return server; }
    public String world() { return world; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public PunishmentSource source() { return source; }
    public PunishmentStatus status() { return status; }
    public long resolvedAt() { return resolvedAt; }
    public UUID resolvedByUuid() { return resolvedByUuid; }
    public String resolvedByName() { return resolvedByName; }
    public String resolvedReason() { return resolvedReason; }

    public boolean isExpiredByTime(long now) {
        return !permanent && expiresAt > 0 && now >= expiresAt;
    }

    public long remainingMillis(long now) {
        if (permanent) return -1;
        return Math.max(0, expiresAt - now);
    }

    /** Mutates local in-memory state to reflect a resolution; caller is responsible for persisting via the repository. */
    public void resolve(PunishmentStatus newStatus, long when, UUID byUuid, String byName, String reason) {
        this.status = newStatus;
        this.resolvedAt = when;
        this.resolvedByUuid = byUuid;
        this.resolvedByName = byName;
        this.resolvedReason = reason;
    }

    public void updateReason(String newReason) {
        this.reason = newReason;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long id = -1;
        private UUID playerUuid;
        private String playerName;
        private String ip;
        private PunishmentType type;
        private boolean permanent = true;
        private long createdAt = System.currentTimeMillis();
        private long expiresAt = -1;
        private String reason = "No reason specified";
        private UUID operatorUuid;
        private String operatorName = "CONSOLE";
        private String server = "unknown";
        private String world = "unknown";
        private double x, y, z;
        private PunishmentSource source = PunishmentSource.CONSOLE;
        private PunishmentStatus status = PunishmentStatus.ACTIVE;
        private long resolvedAt = -1;
        private UUID resolvedByUuid;
        private String resolvedByName;
        private String resolvedReason;

        public Builder id(long id) { this.id = id; return this; }
        public Builder playerUuid(UUID u) { this.playerUuid = u; return this; }
        public Builder playerName(String n) { this.playerName = n; return this; }
        public Builder ip(String ip) { this.ip = ip; return this; }
        public Builder type(PunishmentType t) { this.type = t; return this; }
        public Builder permanent(boolean p) { this.permanent = p; return this; }
        public Builder createdAt(long c) { this.createdAt = c; return this; }
        public Builder expiresAt(long e) { this.expiresAt = e; return this; }
        public Builder reason(String r) { this.reason = r; return this; }
        public Builder operatorUuid(UUID u) { this.operatorUuid = u; return this; }
        public Builder operatorName(String n) { this.operatorName = n; return this; }
        public Builder server(String s) { this.server = s; return this; }
        public Builder world(String w) { this.world = w; return this; }
        public Builder location(double x, double y, double z) { this.x = x; this.y = y; this.z = z; return this; }
        public Builder source(PunishmentSource s) { this.source = s; return this; }
        public Builder status(PunishmentStatus s) { this.status = s; return this; }
        public Builder resolvedAt(long r) { this.resolvedAt = r; return this; }
        public Builder resolvedByUuid(UUID u) { this.resolvedByUuid = u; return this; }
        public Builder resolvedByName(String n) { this.resolvedByName = n; return this; }
        public Builder resolvedReason(String r) { this.resolvedReason = r; return this; }

        public PunishmentRecord build() {
            if (playerUuid == null && ip == null) {
                throw new IllegalStateException("PunishmentRecord requires playerUuid and/or ip");
            }
            if (type == null) throw new IllegalStateException("PunishmentRecord requires a type");
            return new PunishmentRecord(this);
        }
    }
}
