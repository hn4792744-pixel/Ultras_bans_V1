package com.ultras.bans.database;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SecurityLogRepositoryJdbc {

    private final DatabaseManager db;
    private final Executor executor;
    private final Logger logger;
    private final String table;

    public SecurityLogRepositoryJdbc(DatabaseManager db, Executor executor, Logger logger) {
        this.db = db;
        this.executor = executor;
        this.logger = logger;
        this.table = db.prefix() + "security_logs";
    }

    public CompletableFuture<Void> log(UUID staffUuid, String staffName, String actionType, int count, int windowSeconds, String response) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO " + table + " (staff_uuid, staff_name, action_type, action_count, window_seconds, triggered_at, response) VALUES (?,?,?,?,?,?,?)";
            try (Connection c = db.dataSource().getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, staffUuid == null ? null : staffUuid.toString());
                ps.setString(2, staffName);
                ps.setString(3, actionType);
                ps.setInt(4, count);
                ps.setInt(5, windowSeconds);
                ps.setLong(6, System.currentTimeMillis());
                ps.setString(7, response);
                ps.executeUpdate();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Failed to write security log", ex);
            }
        }, executor);
    }
}
