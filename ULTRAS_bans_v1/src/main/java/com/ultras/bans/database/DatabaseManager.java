package com.ultras.bans.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the JDBC {@link DataSource} and runs idempotent {@code CREATE TABLE IF
 * NOT EXISTS} migrations on startup. Each feature module (punishments,
 * players, jail, security) contributes its own DDL block via
 * {@link #migrate()} so schema ownership stays next to the repository that
 * uses it, per spec section 58's separation-of-services rule.
 */
public final class DatabaseManager {

    private final DatabaseConfig config;
    private final File dataFolder;
    private final Logger logger;
    private HikariDataSource dataSource;

    public DatabaseManager(DatabaseConfig config, File dataFolder, Logger logger) {
        this.config = config;
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    public void connect() {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("ULTRASbans-Pool");

        if (config.type() == DatabaseType.SQLITE) {
            File dbFile = new File(dataFolder, config.sqliteFileName());
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                logger.warning("Could not create plugin data folder for SQLite database file.");
            }
            hikari.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hikari.setMaximumPoolSize(1); // SQLite only safely supports a single writer connection
            hikari.setConnectionTestQuery("SELECT 1");
            hikari.addDataSourceProperty("journal_mode", "WAL");
            hikari.addDataSourceProperty("busy_timeout", "5000");
        } else {
            String url = String.format("jdbc:mariadb://%s:%d/%s?useSSL=%s&autoReconnect=true&characterEncoding=utf8",
                    config.host(), config.port(), config.database(), config.useSsl());
            hikari.setJdbcUrl(url);
            hikari.setUsername(config.username());
            hikari.setPassword(config.password());
            hikari.setMaximumPoolSize(Math.max(2, config.poolSize()));
            hikari.setConnectionTestQuery("SELECT 1");
        }

        this.dataSource = new HikariDataSource(hikari);
        migrate();
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public DatabaseConfig config() {
        return config;
    }

    public boolean isSqlite() {
        return config.type() == DatabaseType.SQLITE;
    }

    public String prefix() {
        return config.tablePrefix();
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private void migrate() {
        String p = prefix();
        boolean sqlite = isSqlite();

        String idType = sqlite ? "INTEGER PRIMARY KEY AUTOINCREMENT" : "BIGINT AUTO_INCREMENT PRIMARY KEY";
        String longText = "TEXT";

        String punishmentsDdl = "CREATE TABLE IF NOT EXISTS " + p + "punishments (" +
                "id " + idType + ", " +
                "player_uuid VARCHAR(36), " +
                "player_name VARCHAR(32), " +
                "ip VARCHAR(45), " +
                "type VARCHAR(20) NOT NULL, " +
                "permanent BOOLEAN NOT NULL, " +
                "created_at BIGINT NOT NULL, " +
                "expires_at BIGINT NOT NULL DEFAULT -1, " +
                "reason " + longText + ", " +
                "operator_uuid VARCHAR(36), " +
                "operator_name VARCHAR(32), " +
                "server VARCHAR(64), " +
                "world VARCHAR(64), " +
                "x DOUBLE DEFAULT 0, y DOUBLE DEFAULT 0, z DOUBLE DEFAULT 0, " +
                "source VARCHAR(16), " +
                "status VARCHAR(16) NOT NULL, " +
                "resolved_at BIGINT DEFAULT -1, " +
                "resolved_by_uuid VARCHAR(36), " +
                "resolved_by_name VARCHAR(32), " +
                "resolved_reason " + longText +
                ")";

        String playersDdl = "CREATE TABLE IF NOT EXISTS " + p + "players (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "username VARCHAR(32) NOT NULL, " +
                "last_ip VARCHAR(45), " +
                "platform VARCHAR(16) DEFAULT 'JAVA', " +
                "rank_name VARCHAR(32) DEFAULT 'default', " +
                "op_status BOOLEAN DEFAULT 0, " +
                "last_server VARCHAR(64), " +
                "last_world VARCHAR(64), " +
                "last_x DOUBLE DEFAULT 0, last_y DOUBLE DEFAULT 0, last_z DOUBLE DEFAULT 0, " +
                "first_join BIGINT, " +
                "last_join BIGINT, " +
                "last_quit BIGINT, " +
                "playtime_millis BIGINT DEFAULT 0, " +
                "blocks_broken BIGINT DEFAULT 0, " +
                "blocks_placed BIGINT DEFAULT 0, " +
                "mob_kills BIGINT DEFAULT 0, " +
                "player_kills BIGINT DEFAULT 0, " +
                "deaths BIGINT DEFAULT 0, " +
                "distance_traveled DOUBLE DEFAULT 0" +
                ")";

        String jailLocDdl = "CREATE TABLE IF NOT EXISTS " + p + "jail_locations (" +
                "name VARCHAR(64) PRIMARY KEY, server VARCHAR(64), world VARCHAR(64), " +
                "x DOUBLE, y DOUBLE, z DOUBLE, yaw FLOAT, pitch FLOAT)";
        String jailSessDdl = "CREATE TABLE IF NOT EXISTS " + p + "jail_sessions (" +
                "player_uuid VARCHAR(36) PRIMARY KEY, punishment_id BIGINT, jail_name VARCHAR(64), " +
                "prev_server VARCHAR(64), prev_world VARCHAR(64), prev_x DOUBLE, prev_y DOUBLE, prev_z DOUBLE, " +
                "prev_yaw FLOAT, prev_pitch FLOAT, inventory " + longText + ", armor " + longText + ", offhand " + longText + ", " +
                "flags VARCHAR(32), created_at BIGINT)";

        String playerPermDdl = "CREATE TABLE IF NOT EXISTS " + p + "player_permissions (" +
                "player_uuid VARCHAR(36) NOT NULL, command VARCHAR(64) NOT NULL, value BOOLEAN NOT NULL, " +
                "PRIMARY KEY (player_uuid, command))";
        String rankPermDdl = "CREATE TABLE IF NOT EXISTS " + p + "rank_permissions (" +
                "rank_name VARCHAR(64) NOT NULL, command VARCHAR(64) NOT NULL, value BOOLEAN NOT NULL, " +
                "PRIMARY KEY (rank_name, command))";

        String securityLogDdl = "CREATE TABLE IF NOT EXISTS " + p + "security_logs (" +
                "id " + idType + ", staff_uuid VARCHAR(36), staff_name VARCHAR(32), action_type VARCHAR(32), " +
                "action_count INT, window_seconds INT, triggered_at BIGINT, response " + longText + ")";

        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate(punishmentsDdl);
            st.executeUpdate(playerPermDdl);
            st.executeUpdate(rankPermDdl);
            st.executeUpdate(securityLogDdl);
            st.executeUpdate(jailLocDdl);
            st.executeUpdate(jailSessDdl);
            st.executeUpdate(playersDdl);
            createIndex(st, "idx_" + p + "punishments_player", p + "punishments", "player_uuid, type, status");
            createIndex(st, "idx_" + p + "punishments_ip", p + "punishments", "ip, type, status");
            createIndex(st, "idx_" + p + "punishments_status", p + "punishments", "status, permanent, expires_at");
            logger.info("Database schema ready (" + config.type() + ").");
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to run database migrations", ex);
            throw new IllegalStateException("Database migration failed", ex);
        }
    }

    /** MySQL lacks CREATE INDEX IF NOT EXISTS, so create and ignore "already exists" errors on every dialect. */
    private void createIndex(Statement st, String name, String table, String columns) {
        try {
            st.executeUpdate("CREATE INDEX " + name + " ON " + table + " (" + columns + ")");
        } catch (SQLException ex) {
            // Duplicate index on subsequent startups is expected and harmless.
        }
    }
}
