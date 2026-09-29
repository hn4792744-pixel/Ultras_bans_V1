package com.ultras.bans.database;

import com.ultras.bans.punishment.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PunishmentRepositoryJdbc implements PunishmentRepository {

    private final DatabaseManager db;
    private final Executor executor;
    private final Logger logger;
    private final String table;

    public PunishmentRepositoryJdbc(DatabaseManager db, Executor executor, Logger logger) {
        this.db = db;
        this.executor = executor;
        this.logger = logger;
        this.table = db.prefix() + "punishments";
    }

    @Override
    public CompletableFuture<PunishmentRecord> insert(PunishmentRecord record) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO " + table + " (player_uuid, player_name, ip, type, permanent, created_at, " +
                    "expires_at, reason, operator_uuid, operator_name, server, world, x, y, z, source, status, " +
                    "resolved_at, resolved_by_uuid, resolved_by_name, resolved_reason) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            try (Connection conn = db.dataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                bindInsert(ps, record);
                ps.executeUpdate();

                long generatedId = -1;
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) generatedId = keys.getLong(1);
                }

                return PunishmentRecord.builder()
                        .id(generatedId)
                        .playerUuid(record.playerUuid())
                        .playerName(record.playerName())
                        .ip(record.ip())
                        .type(record.type())
                        .permanent(record.permanent())
                        .createdAt(record.createdAt())
                        .expiresAt(record.expiresAt())
                        .reason(record.reason())
                        .operatorUuid(record.operatorUuid())
                        .operatorName(record.operatorName())
                        .server(record.server())
                        .world(record.world())
                        .location(record.x(), record.y(), record.z())
                        .source(record.source())
                        .status(record.status())
                        .build();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Failed to insert punishment record", ex);
                throw new RuntimeException(ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> update(PunishmentRecord record) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE " + table + " SET status=?, resolved_at=?, resolved_by_uuid=?, resolved_by_name=?, " +
                    "resolved_reason=?, reason=? WHERE id=?";
            try (Connection conn = db.dataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, record.status().name());
                ps.setLong(2, record.resolvedAt());
                setNullableString(ps, 3, record.resolvedByUuid() == null ? null : record.resolvedByUuid().toString());
                setNullableString(ps, 4, record.resolvedByName());
                setNullableString(ps, 5, record.resolvedReason());
                ps.setString(6, record.reason());
                ps.setLong(7, record.id());
                ps.executeUpdate();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Failed to update punishment record #" + record.id(), ex);
                throw new RuntimeException(ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<PunishmentRecord> findById(long id) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + table + " WHERE id=?";
            try (Connection conn = db.dataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? map(rs) : null;
                }
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Failed to find punishment #" + id, ex);
                throw new RuntimeException(ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<PunishmentRecord>> findByPlayer(UUID playerUuid) {
        return queryList("SELECT * FROM " + table + " WHERE player_uuid=? ORDER BY created_at DESC",
                ps -> ps.setString(1, playerUuid.toString()));
    }

    @Override
    public CompletableFuture<List<PunishmentRecord>> findByIp(String ip) {
        return queryList("SELECT * FROM " + table + " WHERE ip=? ORDER BY created_at DESC",
                ps -> ps.setString(1, ip));
    }

    @Override
    public CompletableFuture<List<PunishmentRecord>> findActiveByPlayerAndType(UUID playerUuid, PunishmentType type) {
        return queryList("SELECT * FROM " + table + " WHERE player_uuid=? AND type=? AND status='ACTIVE' ORDER BY created_at DESC",
                ps -> { ps.setString(1, playerUuid.toString()); ps.setString(2, type.name()); });
    }

    @Override
    public CompletableFuture<List<PunishmentRecord>> findActiveByIpAndType(String ip, PunishmentType type) {
        return queryList("SELECT * FROM " + table + " WHERE ip=? AND type=? AND status='ACTIVE' ORDER BY created_at DESC",
                ps -> { ps.setString(1, ip); ps.setString(2, type.name()); });
    }

    @Override
    public CompletableFuture<List<PunishmentRecord>> findAllActive() {
        return queryList("SELECT * FROM " + table + " WHERE status='ACTIVE' AND type<>'WARN' ORDER BY created_at ASC",
                ps -> {});
    }

    @Override
    public CompletableFuture<List<PunishmentRecord>> findAllActiveTemporary() {
        return queryList("SELECT * FROM " + table + " WHERE status='ACTIVE' AND permanent=0 ORDER BY expires_at ASC",
                ps -> {});
    }

    @Override
    public CompletableFuture<List<PunishmentRecord>> findRecentByType(PunishmentType type, int limit) {
        return queryList("SELECT * FROM " + table + " WHERE type=? ORDER BY created_at DESC LIMIT ?",
                ps -> { ps.setString(1, type.name()); ps.setInt(2, limit); });
    }

    @Override
    public CompletableFuture<List<PunishmentRecord>> findLogs(UUID playerUuid, PunishmentType typeOrNull, int limit) {
        if (typeOrNull == null) {
            return queryList("SELECT * FROM " + table + " WHERE player_uuid=? ORDER BY created_at DESC LIMIT ?",
                    ps -> { ps.setString(1, playerUuid.toString()); ps.setInt(2, limit); });
        }
        return queryList("SELECT * FROM " + table + " WHERE player_uuid=? AND type=? ORDER BY created_at DESC LIMIT ?",
                ps -> { ps.setString(1, playerUuid.toString()); ps.setString(2, typeOrNull.name()); ps.setInt(3, limit); });
    }

    // ---- helpers ----

    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private CompletableFuture<List<PunishmentRecord>> queryList(String sql, Binder binder) {
        return CompletableFuture.supplyAsync(() -> {
            List<PunishmentRecord> results = new ArrayList<>();
            try (Connection conn = db.dataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                binder.bind(ps);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(map(rs));
                    }
                }
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Punishment query failed: " + sql, ex);
                throw new RuntimeException(ex);
            }
            return results;
        }, executor);
    }

    private void bindInsert(PreparedStatement ps, PunishmentRecord r) throws SQLException {
        setNullableString(ps, 1, r.playerUuid() == null ? null : r.playerUuid().toString());
        setNullableString(ps, 2, r.playerName());
        setNullableString(ps, 3, r.ip());
        ps.setString(4, r.type().name());
        ps.setBoolean(5, r.permanent());
        ps.setLong(6, r.createdAt());
        ps.setLong(7, r.expiresAt());
        setNullableString(ps, 8, r.reason());
        setNullableString(ps, 9, r.operatorUuid() == null ? null : r.operatorUuid().toString());
        setNullableString(ps, 10, r.operatorName());
        setNullableString(ps, 11, r.server());
        setNullableString(ps, 12, r.world());
        ps.setDouble(13, r.x());
        ps.setDouble(14, r.y());
        ps.setDouble(15, r.z());
        setNullableString(ps, 16, r.source() == null ? null : r.source().name());
        ps.setString(17, r.status().name());
        ps.setLong(18, r.resolvedAt());
        setNullableString(ps, 19, r.resolvedByUuid() == null ? null : r.resolvedByUuid().toString());
        setNullableString(ps, 20, r.resolvedByName());
        setNullableString(ps, 21, r.resolvedReason());
    }

    private void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private PunishmentRecord map(ResultSet rs) throws SQLException {
        String playerUuidStr = rs.getString("player_uuid");
        String operatorUuidStr = rs.getString("operator_uuid");
        String resolvedByUuidStr = rs.getString("resolved_by_uuid");
        String sourceStr = rs.getString("source");

        PunishmentRecord.Builder builder = PunishmentRecord.builder()
                .id(rs.getLong("id"))
                .playerUuid(playerUuidStr == null ? null : UUID.fromString(playerUuidStr))
                .playerName(rs.getString("player_name"))
                .ip(rs.getString("ip"))
                .type(PunishmentType.valueOf(rs.getString("type")))
                .permanent(rs.getBoolean("permanent"))
                .createdAt(rs.getLong("created_at"))
                .expiresAt(rs.getLong("expires_at"))
                .reason(rs.getString("reason"))
                .operatorUuid(operatorUuidStr == null ? null : UUID.fromString(operatorUuidStr))
                .operatorName(rs.getString("operator_name"))
                .server(rs.getString("server"))
                .world(rs.getString("world"))
                .location(rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"))
                .source(sourceStr == null ? PunishmentSource.CONSOLE : PunishmentSource.valueOf(sourceStr))
                .status(PunishmentStatus.valueOf(rs.getString("status")))
                .resolvedAt(rs.getLong("resolved_at"))
                .resolvedByUuid(resolvedByUuidStr == null ? null : UUID.fromString(resolvedByUuidStr))
                .resolvedByName(rs.getString("resolved_by_name"))
                .resolvedReason(rs.getString("resolved_reason"));

        return builder.build();
    }
}
