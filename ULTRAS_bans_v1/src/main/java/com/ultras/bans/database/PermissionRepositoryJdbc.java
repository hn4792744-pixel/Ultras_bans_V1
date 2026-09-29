package com.ultras.bans.database;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Persistence for per-player and per-rank command permission overrides. */
public final class PermissionRepositoryJdbc {

    private final DatabaseManager db;
    private final Executor executor;
    private final Logger logger;
    private final String playerTable, rankTable;

    public PermissionRepositoryJdbc(DatabaseManager db, Executor executor, Logger logger) {
        this.db = db;
        this.executor = executor;
        this.logger = logger;
        this.playerTable = db.prefix() + "player_permissions";
        this.rankTable = db.prefix() + "rank_permissions";
    }

    public CompletableFuture<Map<UUID, Map<String, Boolean>>> loadPlayers() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, Map<String, Boolean>> out = new HashMap<>();
            try (Connection c = db.dataSource().getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT player_uuid, command, value FROM " + playerTable);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.computeIfAbsent(UUID.fromString(rs.getString(1)), k -> new HashMap<>()).put(rs.getString(2), rs.getBoolean(3));
                }
            } catch (SQLException ex) { fail("load player permissions", ex); }
            return out;
        }, executor);
    }

    public CompletableFuture<Map<String, Map<String, Boolean>>> loadRanks() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Map<String, Boolean>> out = new HashMap<>();
            try (Connection c = db.dataSource().getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT rank_name, command, value FROM " + rankTable);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.computeIfAbsent(rs.getString(1), k -> new HashMap<>()).put(rs.getString(2), rs.getBoolean(3));
                }
            } catch (SQLException ex) { fail("load rank permissions", ex); }
            return out;
        }, executor);
    }

    public CompletableFuture<Void> setPlayer(UUID uuid, String command, Boolean valueOrNull) {
        return write(playerTable, "player_uuid", uuid.toString(), command, valueOrNull);
    }

    public CompletableFuture<Void> setRank(String rank, String command, Boolean valueOrNull) {
        return write(rankTable, "rank_name", rank, command, valueOrNull);
    }

    private CompletableFuture<Void> write(String table, String keyCol, String key, String command, Boolean value) {
        return CompletableFuture.runAsync(() -> {
            try (Connection c = db.dataSource().getConnection()) {
                if (value == null) {
                    try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + table + " WHERE " + keyCol + "=? AND command=?")) {
                        ps.setString(1, key); ps.setString(2, command);
                        ps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ps = c.prepareStatement("REPLACE INTO " + table + " (" + keyCol + ", command, value) VALUES (?,?,?)")) {
                        ps.setString(1, key); ps.setString(2, command); ps.setBoolean(3, value);
                        ps.executeUpdate();
                    }
                }
            } catch (SQLException ex) { fail("write permission", ex); }
        }, executor);
    }

    private void fail(String what, SQLException ex) {
        logger.log(Level.SEVERE, "Failed to " + what, ex);
        throw new RuntimeException(ex);
    }
}
