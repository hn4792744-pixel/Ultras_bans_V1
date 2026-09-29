package com.ultras.bans.gui;

/** What should happen when staff click a player in the Player Selection GUI. */
public enum PlayerSelectAction {
    PUNISH_MENU,
    BAN, IP_BAN, MUTE, WARN, KICK, FREEZE, JAIL,
    WARN_LIST,
    LOGS_BAN, LOGS_IP_BAN, LOGS_MUTE, LOGS_WARN, LOGS_JAIL, LOGS_FREEZE, LOGS_VANISH,
    JAIL_SETTINGS,
    TELEPORT,
    PERMISSIONS,
    GAMEMODE
}
