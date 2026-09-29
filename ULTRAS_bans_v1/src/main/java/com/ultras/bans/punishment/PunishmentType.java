package com.ultras.bans.punishment;

import java.util.EnumSet;
import java.util.Set;

/**
 * Every punishment kind the engine understands. Ban/IPBan/Mute/Jail all support
 * a temporary variant; Warn is always instantaneous but can expire; Kick,
 * Freeze and Vanish are momentary/toggle states rather than timed sentences,
 * but they still flow through {@link PunishmentService} so history, logging,
 * Discord and security detection stay unified.
 * <p>
 * {@link #conflictsWith()} defines which other types occupy the same "slot" on
 * a player, so the GUI and command layer can refuse to double-apply or silently
 * downgrade/upgrade a punishment (see PunishmentService#tryApply).
 */
public enum PunishmentType {

    BAN(true, false),
    IP_BAN(true, true),
    MUTE(true, false),
    WARN(true, false),
    KICK(false, false),
    FREEZE(false, false),
    JAIL(false, false),
    VANISH(false, false);

    private final boolean supportsDuration;
    private final boolean ipBased;

    PunishmentType(boolean supportsDuration, boolean ipBased) {
        this.supportsDuration = supportsDuration;
        this.ipBased = ipBased;
    }

    public boolean supportsDuration() {
        return supportsDuration;
    }

    public boolean isIpBased() {
        return ipBased;
    }

    /**
     * Types that share the same "active slot" as this one. A player may only have
     * ONE active record among a mutually-conflicting group at a time (e.g. BAN and
     * IP_BAN are tracked separately because a player can be name-banned and IP-banned
     * independently, but two BAN records for the same player can never both be ACTIVE).
     */
    public Set<PunishmentType> conflictsWith() {
        return EnumSet.of(this);
    }

    /** Whether this type is a single ongoing state (only one ACTIVE record ever) vs. a log of many (WARN). */
    public boolean isSingleSlot() {
        return this != WARN;
    }
}
