package com.ultras.bans.punishment;

/**
 * Outcome of an engine call (apply/lift). Never a boolean - callers (commands,
 * GUI click handlers, the proxy bridge) always need a lang key plus optional
 * placeholders to show the operator exactly why an action did or didn't happen,
 * per spec section 6 (conflict prevention messages).
 */
public final class PunishmentResult {

    public enum Outcome {
        SUCCESS,
        ALREADY_PERMANENT,     // e.g. "Player is already permanently muted."
        ALREADY_TEMPORARY,     // e.g. "Player is already muted until: <time>"
        NOT_PUNISHED,          // unmute/unban/etc. attempted on a clean player
        TARGET_NOT_FOUND,
        INVALID_DURATION,
        OP_PROTECTED,
        SELF_ACTION_BLOCKED,
        NO_PERMISSION_LEVEL,   // e.g. staff trying to punish a higher-ranked staff member
        SECURITY_BLOCKED,      // mass-punishment protection tripped
        FAILURE
    }

    private final Outcome outcome;
    private final String messageKey;
    private final Object[] placeholders;
    private final PunishmentRecord record; // set on SUCCESS or when a conflicting record is the reason

    private PunishmentResult(Outcome outcome, String messageKey, PunishmentRecord record, Object... placeholders) {
        this.outcome = outcome;
        this.messageKey = messageKey;
        this.record = record;
        this.placeholders = placeholders;
    }

    public static PunishmentResult success(PunishmentRecord record, String messageKey, Object... placeholders) {
        return new PunishmentResult(Outcome.SUCCESS, messageKey, record, placeholders);
    }

    public static PunishmentResult of(Outcome outcome, String messageKey, Object... placeholders) {
        return new PunishmentResult(outcome, messageKey, null, placeholders);
    }

    public static PunishmentResult conflict(Outcome outcome, String messageKey, PunishmentRecord conflicting, Object... placeholders) {
        return new PunishmentResult(outcome, messageKey, conflicting, placeholders);
    }

    public boolean isSuccess() {
        return outcome == Outcome.SUCCESS;
    }

    public Outcome outcome() { return outcome; }
    public String messageKey() { return messageKey; }
    public Object[] placeholders() { return placeholders; }
    public PunishmentRecord record() { return record; }
}
