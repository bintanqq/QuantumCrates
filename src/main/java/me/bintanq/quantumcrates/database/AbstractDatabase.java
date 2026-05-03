package me.bintanq.quantumcrates.database;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.log.CrateLog;
import me.bintanq.quantumcrates.model.PlayerData;
import me.bintanq.quantumcrates.serializer.GsonProvider;
import me.bintanq.quantumcrates.util.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public abstract class AbstractDatabase implements DatabaseManager {

    protected final QuantumCrates plugin;
    protected final Executor asyncExecutor;

    protected AbstractDatabase(QuantumCrates plugin) {
        this.plugin = plugin;
        this.asyncExecutor = plugin.getAsyncExecutor();
    }

    protected void createTables() throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS qc_player_data (
                        uuid            VARCHAR(36)  NOT NULL PRIMARY KEY,
                        pity_data       TEXT         NOT NULL DEFAULT '{}',
                        cooldown_data   TEXT         NOT NULL DEFAULT '{}',
                        last_seen       BIGINT       NOT NULL DEFAULT 0
                    )
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS qc_virtual_keys (
                        uuid            VARCHAR(36)  NOT NULL,
                        key_id          VARCHAR(64)  NOT NULL,
                        amount          INT          NOT NULL DEFAULT 0,
                        PRIMARY KEY (uuid, key_id)
                    )
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS qc_crate_logs (
                        id              INTEGER      PRIMARY KEY """ + autoIncrementSyntax() + """
                        ,
                        uuid            VARCHAR(36)  NOT NULL,
                        player_name     VARCHAR(16)  NOT NULL,
                        crate_id        VARCHAR(64)  NOT NULL,
                        reward_id       VARCHAR(128) NOT NULL,
                        reward_display  TEXT         NOT NULL,
                        pity_at_open    INT          NOT NULL DEFAULT 0,
                        timestamp       BIGINT       NOT NULL,
                        world           VARCHAR(64),
                        x               DOUBLE,
                        y               DOUBLE,
                        z               DOUBLE
                    )
                """);

                stmt.execute("CREATE INDEX IF NOT EXISTS idx_logs_uuid  ON qc_crate_logs(uuid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_logs_crate ON qc_crate_logs(crate_id)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_logs_ts    ON qc_crate_logs(timestamp)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_vkeys_uuid ON qc_virtual_keys(uuid)");

                conn.commit();
                Logger.info("Database schema &averified/created.");
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    protected abstract String autoIncrementSyntax();

    @Override
    public CompletableFuture<PlayerData> loadPlayerData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT pity_data, cooldown_data, last_seen FROM qc_player_data WHERE uuid = ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return GsonProvider.fromJson(
                                "{\"uuid\":\"%s\",\"pityData\":%s,\"cooldownData\":%s,\"lastSeen\":%d}".formatted(
                                        uuid, rs.getString("pity_data"),
                                        rs.getString("cooldown_data"), rs.getLong("last_seen")),
                                PlayerData.class);
                    }
                }
            } catch (SQLException e) {
                Logger.severe("Failed to load PlayerData for " + uuid + ": " + e.getMessage());
            }
            return new PlayerData(uuid);
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Void> savePlayerData(PlayerData data) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(upsertPlayerDataSql())) {
                bindPlayerData(ps, data);
                ps.executeUpdate();
            } catch (SQLException e) {
                Logger.severe("Failed to save PlayerData for " + data.getUuid() + ": " + e.getMessage());
            }
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Void> savePlayerDataBatch(List<PlayerData> batch) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement(upsertPlayerDataSql())) {
                    for (PlayerData data : batch) {
                        bindPlayerData(ps, data);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                    conn.commit();
                }
            } catch (SQLException e) {
                Logger.severe("Failed batch save PlayerData: " + e.getMessage());
            }
        }, asyncExecutor);
    }

    private void bindPlayerData(PreparedStatement ps, PlayerData data) throws SQLException {
        ps.setString(1, data.getUuid().toString());
        ps.setString(2, GsonProvider.toJson(data.getPityData()));
        ps.setString(3, GsonProvider.toJson(data.getCooldownData()));
        ps.setLong(4, data.getLastSeen());
    }

    protected abstract String upsertPlayerDataSql();

    @Override
    public CompletableFuture<Void> addVirtualKeys(UUID uuid, String keyId, int amount) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(upsertAddKeysSql())) {
                ps.setString(1, uuid.toString());
                ps.setString(2, keyId);
                ps.setInt(3, amount);
                ps.executeUpdate();
            } catch (SQLException e) {
                Logger.severe("Failed to add virtual keys: " + e.getMessage());
            }
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Boolean> removeVirtualKeys(UUID uuid, String keyId, int amount) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                try {
                    try (PreparedStatement check = conn.prepareStatement(
                            "SELECT amount FROM qc_virtual_keys WHERE uuid = ? AND key_id = ?")) {
                        check.setString(1, uuid.toString());
                        check.setString(2, keyId);
                        try (ResultSet rs = check.executeQuery()) {
                            if (!rs.next() || rs.getInt("amount") < amount) {
                                conn.rollback();
                                return false;
                            }
                        }
                    }
                    try (PreparedStatement update = conn.prepareStatement(
                            "UPDATE qc_virtual_keys SET amount = amount - ? WHERE uuid = ? AND key_id = ?")) {
                        update.setInt(1, amount);
                        update.setString(2, uuid.toString());
                        update.setString(3, keyId);
                        update.executeUpdate();
                    }
                    conn.commit();
                    return true;
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                Logger.severe("Failed to remove virtual keys: " + e.getMessage());
                return false;
            }
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Integer> getVirtualKeys(UUID uuid, String keyId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT amount FROM qc_virtual_keys WHERE uuid = ? AND key_id = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, keyId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("amount");
                }
            } catch (SQLException e) {
                Logger.severe("Failed to get virtual keys: " + e.getMessage());
            }
            return 0;
        }, asyncExecutor);
    }

    protected abstract String upsertAddKeysSql();

    @Override
    public CompletableFuture<Void> insertLog(CrateLog log) {
        return CompletableFuture.runAsync(() -> insertLogSync(log), asyncExecutor);
    }

    @Override
    public CompletableFuture<Void> insertLogBatch(List<CrateLog> logs) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement(insertLogSql())) {
                    for (CrateLog log : logs) {
                        bindLog(ps, log);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                    conn.commit();
                }
            } catch (SQLException e) {
                Logger.severe("Failed batch insert logs: " + e.getMessage());
            }
        }, asyncExecutor);
    }

    private void insertLogSync(CrateLog log) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(insertLogSql())) {
            bindLog(ps, log);
            ps.executeUpdate();
        } catch (SQLException e) {
            Logger.severe("Failed to insert log: " + e.getMessage());
        }
    }

    private static String insertLogSql() {
        return """
            INSERT INTO qc_crate_logs
            (uuid, player_name, crate_id, reward_id, reward_display, pity_at_open, timestamp, world, x, y, z)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    }

    private void bindLog(PreparedStatement ps, CrateLog log) throws SQLException {
        ps.setString(1, log.getUuid().toString());
        ps.setString(2, log.getPlayerName());
        ps.setString(3, log.getCrateId());
        ps.setString(4, log.getRewardId());
        ps.setString(5, log.getRewardDisplay());
        ps.setInt(6, log.getPityAtOpen());
        ps.setLong(7, log.getTimestamp());
        ps.setString(8, log.getWorld());
        ps.setDouble(9, log.getX());
        ps.setDouble(10, log.getY());
        ps.setDouble(11, log.getZ());
    }

    @Override
    public CompletableFuture<List<CrateLog>> getPlayerLogs(UUID uuid, int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> queryLogs(
                "SELECT * FROM qc_crate_logs WHERE uuid = ? ORDER BY timestamp DESC LIMIT ? OFFSET ?",
                uuid.toString(), limit, offset), asyncExecutor);
    }

    @Override
    public CompletableFuture<List<CrateLog>> getCrateLogs(String crateId, int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> queryLogs(
                "SELECT * FROM qc_crate_logs WHERE crate_id = ? ORDER BY timestamp DESC LIMIT ? OFFSET ?",
                crateId, limit, offset), asyncExecutor);
    }

    private List<CrateLog> queryLogs(String sql, String param, int limit, int offset) {
        List<CrateLog> logs = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) logs.add(mapLog(rs));
            }
        } catch (SQLException e) {
            Logger.severe("Failed to query logs: " + e.getMessage());
        }
        return logs;
    }

    @Override
    public CompletableFuture<Long> getCrateOpeningCount(String crateId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT COUNT(*) FROM qc_crate_logs WHERE crate_id = ?")) {
                ps.setString(1, crateId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getLong(1);
                }
            } catch (SQLException e) {
                Logger.severe("Failed to get opening count: " + e.getMessage());
            }
            return 0L;
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Boolean> removeVirtualKeysBatch(UUID uuid, String keyId, int totalAmount) {
        return removeVirtualKeys(uuid, keyId, totalAmount);
    }


    private CrateLog mapLog(ResultSet rs) throws SQLException {
        return new CrateLog(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("player_name"),
                rs.getString("crate_id"),
                rs.getString("reward_id"),
                rs.getString("reward_display"),
                rs.getInt("pity_at_open"),
                rs.getLong("timestamp"),
                rs.getString("world"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"));
    }
}