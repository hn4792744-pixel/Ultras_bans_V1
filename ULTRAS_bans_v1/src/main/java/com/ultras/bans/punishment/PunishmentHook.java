package com.ultras.bans.punishment;

/**
 * Registered with {@link PunishmentServiceImpl#registerHook(PunishmentHook)}.
 * Called AFTER a state change is persisted, on the plugin's async executor
 * (never the main thread) - implementations that touch Bukkit API must hop
 * back to the main thread themselves.
 * <p>
 * Used by: DiscordService (embeds), SecurityService (mass-punishment detection),
 * ProxyBridge (cross-server sync broadcast), GuiRefreshService (live GUI updates).
 */
public interface PunishmentHook {

    /** A punishment was newly created (ban/mute/warn/jail/freeze/kick/ip-ban/vanish-on). */
    void onApplied(PunishmentRecord record);

    /** A punishment's status changed away from ACTIVE (unban/unmute/expire/revoke/clear). */
    void onLifted(PunishmentRecord record);
}
