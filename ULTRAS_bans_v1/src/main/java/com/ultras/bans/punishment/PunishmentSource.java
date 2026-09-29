package com.ultras.bans.punishment;

/** Where a punishment action originated, recorded on every {@link PunishmentRecord}. */
public enum PunishmentSource {
    PLAYER,
    CONSOLE,
    DASHBOARD,
    API,
    PROXY
}
