package me.bintanq.quantumcrates.model;

import com.google.gson.annotations.SerializedName;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerData {

    @SerializedName("uuid")
    private final UUID uuid;

    /**
     * Map of crateId → pity counter (number of opens since last rare).
     * Pity is per-crate, not global.
     */
    @SerializedName("pityData")
    private Map<String, Integer> pityData;

    /**
     * Map of crateId → epoch-millis timestamp of last open.
     * Used for cooldown enforcement.
     */
    @SerializedName("cooldownData")
    private Map<String, Long> cooldownData;

    @SerializedName("lifetimeOpens")
    private Map<String, Integer> lifetimeOpens = new HashMap<>();

    @SerializedName("lastSeen")
    private long lastSeen;

    /* ─────────────────────── Constructors ─────────────────────── */

    public PlayerData(UUID uuid) {
        this.uuid         = uuid;
        this.pityData     = new HashMap<>();
        this.cooldownData = new HashMap<>();
        this.lastSeen     = System.currentTimeMillis();
    }

    /* ─────────────────────── Pity Logic ─────────────────────── */

    public int getPity(String crateId) {
        return pityData.getOrDefault(crateId, 0);
    }

    public void incrementPity(String crateId) {
        pityData.merge(crateId, 1, Integer::sum);
    }

    public void resetPity(String crateId) {
        pityData.put(crateId, 0);
    }

    /* ─────────────────────── Cooldown Logic ─────────────────────── */

    public long getLastOpen(String crateId) {
        return cooldownData.getOrDefault(crateId, 0L);
    }

    public void setLastOpen(String crateId, long timestamp) {
        cooldownData.put(crateId, timestamp);
    }

    /**
     * Returns remaining cooldown in milliseconds, or 0 if cooldown expired.
     *
     * @param crateId     The crate to check
     * @param cooldownMs  The crate's configured cooldown duration in ms
     */
    public long getRemainingCooldown(String crateId, long cooldownMs) {
        long elapsed = System.currentTimeMillis() - getLastOpen(crateId);
        return Math.max(0L, cooldownMs - elapsed);
    }

    public boolean isOnCooldown(String crateId, long cooldownMs) {
        return getRemainingCooldown(crateId, cooldownMs) > 0;
    }

    public int getLifetimeOpens(String crateId) {
        if (lifetimeOpens == null) lifetimeOpens = new HashMap<>();
        return lifetimeOpens.getOrDefault(crateId, 0);
    }

    public void incrementLifetimeOpens(String crateId) {
        if (lifetimeOpens == null) lifetimeOpens = new HashMap<>();
        lifetimeOpens.merge(crateId, 1, Integer::sum);
    }

    public void resetLifetimeOpens(String crateId) {
        if (lifetimeOpens == null) lifetimeOpens = new HashMap<>();
        lifetimeOpens.remove(crateId);
    }

    /* ─────────────────────── Getters / Setters ─────────────────────── */

    public UUID getUuid() { return uuid; }
    public Map<String, Integer> getPityData() { return pityData; }
    public Map<String, Long> getCooldownData() { return cooldownData; }
    public Map<String, Integer> getLifetimeOpens() {
        if (lifetimeOpens == null) lifetimeOpens = new HashMap<>();
        return lifetimeOpens;
    }
    public long getLastSeen() { return lastSeen; }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }
}
