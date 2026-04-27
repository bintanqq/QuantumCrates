package me.bintanq.quantumcrates.log;

import com.google.gson.annotations.SerializedName;

import java.util.UUID;

/**
 * CrateLog — an immutable record of a single crate opening event.
 *
 * Designed for both database persistence (qc_crate_logs) and
 * real-time streaming to the Web Interface via WebSocket (Phase 2).
 */
public class CrateLog {

    @SerializedName("uuid")
    private final UUID uuid;

    @SerializedName("playerName")
    private final String playerName;

    @SerializedName("crateId")
    private final String crateId;

    @SerializedName("rewardId")
    private final String rewardId;

    @SerializedName("rewardDisplay")
    private final String rewardDisplay;

    /** Pity counter value at the moment of opening. */
    @SerializedName("pityAtOpen")
    private final int pityAtOpen;

    @SerializedName("timestamp")
    private final long timestamp;

    @SerializedName("world")
    private final String world;

    @SerializedName("x")
    private final double x;

    @SerializedName("y")
    private final double y;

    @SerializedName("z")
    private final double z;

    public CrateLog(
            UUID uuid, String playerName, String crateId,
            String rewardId, String rewardDisplay,
            int pityAtOpen, long timestamp,
            String world, double x, double y, double z
    ) {
        this.uuid          = uuid;
        this.playerName    = playerName;
        this.crateId       = crateId;
        this.rewardId      = rewardId;
        this.rewardDisplay = rewardDisplay;
        this.pityAtOpen    = pityAtOpen;
        this.timestamp     = timestamp;
        this.world         = world;
        this.x             = x;
        this.y             = y;
        this.z             = z;
    }

    /* ─────────────────────── Getters ─────────────────────── */

    public UUID   getUuid()          { return uuid; }
    public String getPlayerName()    { return playerName; }
    public String getCrateId()       { return crateId; }
    public String getRewardId()      { return rewardId; }
    public String getRewardDisplay() { return rewardDisplay; }
    public int    getPityAtOpen()    { return pityAtOpen; }
    public long   getTimestamp()     { return timestamp; }
    public String getWorld()         { return world; }
    public double getX()             { return x; }
    public double getY()             { return y; }
    public double getZ()             { return z; }
}
