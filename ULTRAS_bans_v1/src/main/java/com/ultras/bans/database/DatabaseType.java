package com.ultras.bans.database;

/** Which JDBC backend is active. Determines DDL dialect and pool sizing defaults. */
public enum DatabaseType {
    SQLITE,
    MYSQL // also used for MariaDB - wire protocol and SQL dialect are compatible for our usage
}
