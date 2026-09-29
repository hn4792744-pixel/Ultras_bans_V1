package com.ultras.bans.database;

import com.ultras.bans.model.JailLocation;
import com.ultras.bans.model.JailSession;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Persistence for jail spawn locations and per-player jail sessions (REPLACE INTO works on SQLite and MySQL/MariaDB). */
public final class JailRepositoryJdbc {

    private final DatabaseManager db;
    private final Executor executor;
    private final Logger logger;
    private final String locTable, sessTable;

    public JailRepositoryJdbc(DatabaseManager db, Executor executor, Logger logger) {
        this.db = db;
        this.executor = executor;
        this.logger = logger;
        this.locTable = db.prefix() + "jail_locations";
        this.sessTable = db.prefix() + "jail_sessions";
    }

    public CompletableFuture<Void> saveLocation(JailLocation l) {
        return CompletableFuture.runAsync(() -> {
            String sql = "REPLACE INTO " + locTable + " (name, server, world, x, y, z, yaw, pitch) VALUES (?,?,?,?,?,?,?,?)";
            try (Connection c = db.dataSource().getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, l.name()); ps.setString(2, l.server()); ps.setString(3, l.world());
                ps.setDouble(4, l.x()); ps.setDouble(5, l.y()); ps.setDouble(6, l.z());
                ps.setFloat(7, l.yaw()); ps.setFloat(8, l.pitch());
                ps.executeUpdate();
            } catch (SQLException ex) { fail("save jail location", ex); }
        }, executor);
    }

    public CompletableFuture<List<JailLocation>> loadLocations() {
        return CompletableFuture.supplyAsync(() -> {
            List<JailLocation> out = new ArrayList<>();
            try (Connection c = db.dataSource().getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT * FROM " + locTable);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new JailLocation(rs.getString("name"), rs.getString("server"), rs.getString("world"),
                            rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch")));
                }
            } catch (SQLException ex) { fail("load jail locations", ex); }
            return out;
        }, executor);
    }

    public CompletableFuture<Void> saveSession(JailSession s) {
        return CompletableFuture.runAsync(() -> {
            String sql = "REPLACE INTO " + sessTable + " (player_uuid, punishment_id, jail_name, prev_server, prev_world, " +
                    "prev_x, prev_y, prev_z, prev_yaw, prev_pitch, inventory, armor, offhand, flags, created_at) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            try (Connection c = db.dataSource().getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, s.playerUuid.toString()); ps.setLong(2, s.punishmentId); ps.setString(3, s.jailName);
                ps.setString(4, s.prevServer); ps.setString(5, s.prevWorld);
                ps.setDouble(6, s.prevX); ps.setDouble(7, s.prevY); ps.setDouble(8, s.prevZ);
                ps.setFloat(9, s.prevYaw); ps.setFloat(10, s.prevPitch);
                ps.setString(11, s.inventoryB64); ps.setString(12, s.armorB64); ps.setString(13, s.offhandB64);
                ps.setString(14, s.flagsCsv()); ps.setLong(15, s.createdAt);
                ps.executeUpdate();
            } catch (SQLException ex) { fail("save jail session", ex); }
        }, executor);
    }

    public CompletableFuture<Void> updateFlags(UUID uuid, String flagsCsv) {
        return CompletableFuture.runAsync(() -> {
            try (Connection c = db.dataSource().getConnection();
                 PreparedStatement ps = c.prepareStatement("UPDATE " + sessTable + " SET flags=? WHERE player_uuid=?")) {
                ps.setString(1, flagsCsv); ps.setString(2, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException ex) { fail("update jail flags", ex); }
        }, executor);
    }

    public CompletableFuture<Void> deleteSession(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            try (Connection c = db.dataSource().getConnection();
                 PreparedStatement ps = c.prepareStatement("DELETE FROM " + sessTable + " WHERE player_uuid=?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException ex) { fail("delete jail session", ex); }
        }, executor);
    }

    public CompletableFuture<List<JailSession>> loadSessions() {
        return CompletableFuture.supplyAsync(() -> {
            List<JailSession> out = new ArrayList<>();
            try (Connection c = db.dataSource().getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT * FROM " + sessTable);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JailSession s = new JailSession(UUID.fromString(rs.getString("player_uuid")), rs.getLong("punishment_id"),
                            rs.getString("jail_name"), rs.getString("prev_server"), rs.getString("prev_world"),
                            rs.getDouble("prev_x"), rs.getDouble("prev_y"), rs.getDouble("prev_z"),
                            rs.getFloat("prev_yaw"), rs.getFloat("prev_pitch"),
                            rs.getString("inventory"), rs.getString("armor"), rs.getString("offhand"), rs.getLong("created_at"));
                    s.applyFlagsCsv(rs.getString("flags"));
                    out.add(s);
                }
            } catch (SQLException ex) { fail("load jail sessions", ex); }
            return out;
        }, executor);
    }

    private void fail(String what, SQLException ex) {
        logger.log(Level.SEVERE, "Failed to " + what, ex);
        throw new RuntimeException(ex);
    }
}
