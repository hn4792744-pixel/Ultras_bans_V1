package com.ultras.bans.database;

import com.ultras.bans.model.PlayerPlatform;
import com.ultras.bans.model.PlayerProfile;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PlayerRepositoryJdbc implements PlayerRepository {

    private final DatabaseManager db;
    private final Executor executor;
    private final Logger logger;
    private final String table;

    public PlayerRepositoryJdbc(DatabaseManager db, Executor executor, Logger logger) {
        this.db = db;
        this.executor = executor;
        this.logger = logger;
        this.table = db.prefix() + "players";
    }

    @Override
    public CompletableFuture<Void> upsert(PlayerProfile p) {
        return CompletableFuture.runAsync(() -> {
            String sql;
            if (db.isSqlite()) {
                sql = "INSERT INTO " + table + " (uuid, username, last_ip, platform, rank_name, op_status, " +
                        "last_server, last_world, last_x, last_y, last_z, first_join, last_join, last_quit, " +
                        "playtime_millis, blocks_broken, blocks_placed, mob_kills, player_kills, deaths, distance_traveled) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET username=excluded.username, last_ip=excluded.last_ip, " +
                        "platform=excluded.platform, rank_name=excluded.rank_name, op_status=excluded.op_status, " +
                        "last_server=excluded.last_server, last_world=excluded.last_world, last_x=excluded.last_x, " +
                        "last_y=excluded.last_y, last_z=excluded.last_z, last_join=excluded.last_join, " +
                        "last_quit=excluded.last_quit, playtime_millis=excluded.playtime_millis, " +
                        "blocks_broken=excluded.blocks_broken, blocks_placed=excluded.blocks_placed, " +
                        "mob_kills=excluded.mob_kills, player_kills=excluded.player_kills, deaths=excluded.deaths, " +
                        "distance_traveled=excluded.distance_traveled";
            } else {
                sql = "INSERT INTO " + table + " (uuid, username, last_ip, platform, rank_name, op_status, " +
                        "last_server, last_world, last_x, last_y, last_z, first_join, last_join, last_quit, " +
                        "playtime_millis, blocks_broken, blocks_placed, mob_kills, player_kills, deaths, distance_traveled) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE username=VALUES(username), last_ip=VALUES(last_ip), " +
                        "platform=VALUES(platform), rank_name=VALUES(rank_name), op_status=VALUES(op_status), " +
                        "last_server=VALUES(last_server), last_world=VALUES(last_world), last_x=VALUES(last_x), " +
                        "last_y=VALUES(last_y), last_z=VALUES(last_z), last_join=VALUES(last_join), " +
                        "last_quit=VALUES(last_quit), playtime_millis=VALUES(playtime_millis), " +
                        "blocks_broken=VALUES(blocks_broken), blocks_placed=VALUES(blocks_placed), " +
                        "mob_kills=VALUES(mob_kills), player_kills=VALUES(player_kills), deaths=VALUES(deaths), " +
                        "distance_traveled=VALUES(distance_traveled)";
            }

            try (Connection conn = db.dataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, p.uuid().toString());
                ps.setString(2, p.username());
                ps.setString(3, p.lastIp());
                ps.setString(4, p.platform().name());
                ps.setString(5, p.rank());
                ps.setBoolean(6, p.opStatus());
                ps.setString(7, p.lastServer());
                ps.setString(8, p.lastWorld());
                ps.setDouble(9, p.lastX());
                ps.setDouble(10, p.lastY());
                ps.setDouble(11, p.lastZ());
                ps.setLong(12, p.firstJoin());
                ps.setLong(13, p.lastJoin());
                ps.setLong(14, p.lastQuit());
                ps.setLong(15, p.playtimeMillis());
                ps.setLong(16, p.blocksBroken());
                ps.setLong(17, p.blocksPlaced());
                ps.setLong(18, p.mobKills());
                ps.setLong(19, p.playerKills());
                ps.setLong(20, p.deaths());
                ps.setDouble(21, p.distanceTraveled());
                ps.executeUpdate();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Failed to upsert player profile for " + p.username(), ex);
                throw new RuntimeException(ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<PlayerProfile> findByUuid(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + table + " WHERE uuid=?";
            try (Connection conn = db.dataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? map(rs) : null;
                }
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Failed to find player " + uuid, ex);
                throw new RuntimeException(ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<PlayerProfile> findByName(String username) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + table + " WHERE LOWER(username)=LOWER(?) ORDER BY last_join DESC LIMIT 1";
            try (Connection conn = db.dataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? map(rs) : null;
                }
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Failed to find player by name " + username, ex);
                throw new RuntimeException(ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<PlayerProfile>> findByIp(String ip) {
        return CompletableFuture.supplyAsync(() -> {
            List<PlayerProfile> results = new ArrayList<>();
            String sql = "SELECT * FROM " + table + " WHERE last_ip=? ORDER BY last_join DESC";
            try (Connection conn = db.dataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ip);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) results.add(map(rs));
                }
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Failed to find players by IP", ex);
                throw new RuntimeException(ex);
            }
            return results;
        }, executor);
    }

    @Override
    public CompletableFuture<List<PlayerProfile>> findAll(int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            List<PlayerProfile> results = new ArrayList<>();
            String sql = "SELECT * FROM " + table + " ORDER BY last_join DESC LIMIT ? OFFSET ?";
            try (Connection conn = db.dataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, limit);
                ps.setInt(2, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) results.add(map(rs));
                }
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Failed to list players", ex);
                throw new RuntimeException(ex);
            }
            return results;
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> countAll() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM " + table;
            try (Connection conn = db.dataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Failed to count players", ex);
                throw new RuntimeException(ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<PlayerProfile>> search(String nameContains, int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            List<PlayerProfile> results = new ArrayList<>();
            String sql = "SELECT * FROM " + table + " WHERE LOWER(username) LIKE ? ORDER BY last_join DESC LIMIT ? OFFSET ?";
            try (Connection conn = db.dataSource().getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, "%" + nameContains.toLowerCase().replace("%", "") + "%");
                ps.setInt(2, limit);
                ps.setInt(3, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) results.add(map(rs));
                }
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Failed to search players", ex);
                throw new RuntimeException(ex);
            }
            return results;
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> countSearch(String nameContains) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM " + table + " WHERE LOWER(username) LIKE ?";
            try (Connection conn = db.dataSource().getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, "%" + nameContains.toLowerCase().replace("%", "") + "%");
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Failed to count search", ex);
                throw new RuntimeException(ex);
            }
        }, executor);
    }

    private PlayerProfile map(ResultSet rs) throws SQLException {
        PlayerProfile profile = new PlayerProfile(UUID.fromString(rs.getString("uuid")), rs.getString("username"));
        profile.lastIp(rs.getString("last_ip"));
        profile.platform(PlayerPlatform.valueOf(rs.getString("platform")));
        profile.rank(rs.getString("rank_name"));
        profile.opStatus(rs.getBoolean("op_status"));
        profile.lastServer(rs.getString("last_server"));
        profile.lastWorld(rs.getString("last_world"));
        profile.lastLocation(rs.getDouble("last_x"), rs.getDouble("last_y"), rs.getDouble("last_z"));
        profile.firstJoin(rs.getLong("first_join"));
        profile.lastJoin(rs.getLong("last_join"));
        profile.lastQuit(rs.getLong("last_quit"));
        profile.playtimeMillis(rs.getLong("playtime_millis"));
        profile.blocksBroken(rs.getLong("blocks_broken"));
        profile.blocksPlaced(rs.getLong("blocks_placed"));
        profile.mobKills(rs.getLong("mob_kills"));
        profile.playerKills(rs.getLong("player_kills"));
        profile.deaths(rs.getLong("deaths"));
        profile.distanceTraveled(rs.getDouble("distance_traveled"));
        return profile;
    }
}
