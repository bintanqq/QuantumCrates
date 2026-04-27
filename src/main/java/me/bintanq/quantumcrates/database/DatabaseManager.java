package me.bintanq.quantumcrates.database;

import me.bintanq.quantumcrates.log.CrateLog;
import me.bintanq.quantumcrates.model.PlayerData;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * DatabaseManager — abstraction layer over SQLite / MySQL.
 *
 * ALL methods that return CompletableFuture are inherently async;
 * callers should never block the main thread waiting on them.
 */
public interface DatabaseManager {

    /**
     * Initialises the connection pool and runs schema migration.
     * Called once on plugin enable — safe to block (happens before server accepts players).
     */
    void init() throws Exception;

    /**
     * Gracefully closes all connections. Called on plugin disable.
     */
    void close();

    /**
     * Returns a raw JDBC connection for advanced / batch use.
     * Caller MUST close() the connection after use (HikariCP returns it to pool).
     */
    Connection getConnection() throws SQLException;

    /* ─────────────────────── PlayerData ─────────────────────── */

    /**
     * Loads a player's persistent data from the database.
     * Returns an empty {@link PlayerData} if the player has never opened a crate.
     */
    CompletableFuture<PlayerData> loadPlayerData(UUID uuid);

    /**
     * Persists (upsert) a player's data snapshot to the database.
     */
    CompletableFuture<Void> savePlayerData(PlayerData data);

    /**
     * Bulk-save a batch of PlayerData objects (used on shutdown flush).
     */
    CompletableFuture<Void> savePlayerDataBatch(List<PlayerData> batch);

    /* ─────────────────────── Virtual Keys ─────────────────────── */

    /**
     * Adds {@code amount} virtual keys of {@code keyId} to the player.
     * Thread-safe via DB-level atomic increment.
     */
    CompletableFuture<Void> addVirtualKeys(UUID uuid, String keyId, int amount);

    /**
     * Removes {@code amount} virtual keys. Returns false via future if balance insufficient.
     */
    CompletableFuture<Boolean> removeVirtualKeys(UUID uuid, String keyId, int amount);

    /**
     * Returns the current virtual key balance for a specific key type.
     */
    CompletableFuture<Integer> getVirtualKeys(UUID uuid, String keyId);

    /* ─────────────────────── Logs ─────────────────────── */

    /**
     * Inserts a single crate opening log entry.
     */
    CompletableFuture<Void> insertLog(CrateLog log);

    /**
     * Inserts a batch of log entries efficiently (used by LogManager buffer).
     */
    CompletableFuture<Void> insertLogBatch(List<CrateLog> logs);

    /**
     * Retrieves paginated opening history for a given player.
     *
     * @param uuid   Player UUID
     * @param limit  Max rows
     * @param offset Pagination offset
     */
    CompletableFuture<List<CrateLog>> getPlayerLogs(UUID uuid, int limit, int offset);

    /**
     * Retrieves paginated opening history for a given crate (web analytics).
     */
    CompletableFuture<List<CrateLog>> getCrateLogs(String crateId, int limit, int offset);

    /**
     * Returns total opening count for a crate (web dashboard stats).
     */
    CompletableFuture<Long> getCrateOpeningCount(String crateId);
}
