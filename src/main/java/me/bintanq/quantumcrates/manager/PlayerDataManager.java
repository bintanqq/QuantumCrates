package me.bintanq.quantumcrates.manager;

import me.bintanq.quantumcrates.database.DatabaseManager;
import me.bintanq.quantumcrates.model.PlayerData;
import me.bintanq.quantumcrates.util.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * PlayerDataManager — thread-safe in-memory cache with async DB persistence.
 *
 * Pattern: Load-on-demand, write-back (dirty tracking).
 * On player join → load from DB into cache.
 * On player quit → flush dirty cache entry to DB, then evict.
 * Shutdown → flush all remaining dirty entries.
 *
 * The main thread ONLY reads from the in-memory cache (O(1), no blocking).
 * All DB I/O happens on the async executor.
 */
public class PlayerDataManager {

    private final DatabaseManager db;
    private final Executor        asyncExecutor;

    /** uuid → PlayerData (volatile reads via ConcurrentHashMap). */
    private final ConcurrentHashMap<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    /** Tracks which UUIDs have unsaved changes. */
    private final ConcurrentHashMap<UUID, Boolean> dirtySet = new ConcurrentHashMap<>();

    /** Pending load futures — prevents duplicate DB loads for the same player. */
    private final ConcurrentHashMap<UUID, CompletableFuture<PlayerData>> pendingLoads =
            new ConcurrentHashMap<>();

    public PlayerDataManager(DatabaseManager db, Executor asyncExecutor) {
        this.db            = db;
        this.asyncExecutor = asyncExecutor;
    }

    /* ─────────────────────── Load / Unload ─────────────────────── */

    /**
     * Asynchronously loads player data from DB into cache.
     * Safe to call multiple times — returns cached value immediately if present.
     *
     * @return future that completes with the loaded (or cached) PlayerData
     */
    public CompletableFuture<PlayerData> loadPlayer(UUID uuid) {
        // Already in cache
        PlayerData cached = cache.get(uuid);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        // Deduplicate concurrent load requests
        return pendingLoads.computeIfAbsent(uuid, id ->
            db.loadPlayerData(id).thenApply(data -> {
                cache.put(id, data);
                pendingLoads.remove(id);
                return data;
            })
        );
    }

    /**
     * Saves and evicts a player from cache. Called on player quit.
     */
    public void unloadPlayer(UUID uuid) {
        PlayerData data = cache.remove(uuid);
        if (data != null && isDirty(uuid)) {
            data.setLastSeen(System.currentTimeMillis());
            db.savePlayerData(data);
            dirtySet.remove(uuid);
        }
    }

    /* ─────────────────────── Read / Write ─────────────────────── */

    /**
     * Returns cached PlayerData or an empty new instance.
     * This is a synchronous, main-thread-safe call.
     * Call {@link #loadPlayer(UUID)} first to ensure data is warm.
     */
    public PlayerData getOrEmpty(UUID uuid) {
        return cache.getOrDefault(uuid, new PlayerData(uuid));
    }

    /**
     * Marks a player's data as dirty (needs saving).
     * Called after mutating PlayerData.
     */
    public void markDirty(UUID uuid) {
        dirtySet.put(uuid, Boolean.TRUE);
    }

    public boolean isDirty(UUID uuid) {
        return dirtySet.containsKey(uuid);
    }

    /* ─────────────────────── Pity Helpers (main-thread safe) ─────────────────────── */

    public int getPity(UUID uuid, String crateId) {
        return getOrEmpty(uuid).getPity(crateId);
    }

    public void incrementPity(UUID uuid, String crateId) {
        PlayerData data = getOrEmpty(uuid);
        data.incrementPity(crateId);
        cache.put(uuid, data);
        markDirty(uuid);
    }

    public void resetPity(UUID uuid, String crateId) {
        PlayerData data = getOrEmpty(uuid);
        data.resetPity(crateId);
        cache.put(uuid, data);
        markDirty(uuid);
    }

    /* ─────────────────────── Cooldown Helpers (main-thread safe) ─────────────────────── */

    public boolean isOnCooldown(UUID uuid, String crateId, long cooldownMs) {
        return getOrEmpty(uuid).isOnCooldown(crateId, cooldownMs);
    }

    public long getRemainingCooldown(UUID uuid, String crateId, long cooldownMs) {
        return getOrEmpty(uuid).getRemainingCooldown(crateId, cooldownMs);
    }

    public void setLastOpen(UUID uuid, String crateId) {
        PlayerData data = getOrEmpty(uuid);
        data.setLastOpen(crateId, System.currentTimeMillis());
        cache.put(uuid, data);
        markDirty(uuid);
    }

    /* ─────────────────────── Shutdown ─────────────────────── */

    /**
     * Batch-saves all dirty cache entries. Blocks until complete.
     * Must be called on plugin disable before DB is closed.
     */
    public void flushAll() {
        List<PlayerData> dirty = new ArrayList<>();
        for (UUID uuid : dirtySet.keySet()) {
            PlayerData data = cache.get(uuid);
            if (data != null) {
                data.setLastSeen(System.currentTimeMillis());
                dirty.add(data);
            }
        }
        if (!dirty.isEmpty()) {
            try {
                db.savePlayerDataBatch(dirty).get(10, TimeUnit.SECONDS);
                Logger.info("Flushed &e" + dirty.size() + " &fplayer data entries.");
            } catch (Exception e) {
                Logger.severe("Failed to flush player data on shutdown: " + e.getMessage());
            }
        }
        dirtySet.clear();
        cache.clear();
    }
}
