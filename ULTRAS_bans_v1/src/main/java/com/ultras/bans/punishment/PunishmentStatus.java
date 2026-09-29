package com.ultras.bans.punishment;

/**
 * Explicit lifecycle state for every {@link PunishmentRecord}. Never inferred
 * implicitly from timestamps alone in business logic - the scheduler
 * (manager/ExpirationScheduler.java) is the only thing allowed to transition
 * ACTIVE -> EXPIRED, and PunishmentService is the only thing allowed to
 * transition ACTIVE -> REMOVED / REVOKED. This keeps temporary and permanent
 * punishments from ever being ambiguous.
 */
public enum PunishmentStatus {
    /** Currently in effect. */
    ACTIVE,
    /** Was temporary and its duration elapsed naturally. */
    EXPIRED,
    /** Manually lifted by staff/console/API before expiry (e.g. /unban). */
    REMOVED,
    /** Invalidated administratively without being a normal "unban" action,
     *  e.g. cleared via "Clear All Punishments" or overturned on appeal. */
    REVOKED;

    public boolean isActive() {
        return this == ACTIVE;
    }
}
