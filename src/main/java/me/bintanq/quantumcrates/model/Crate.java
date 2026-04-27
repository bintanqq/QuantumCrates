package me.bintanq.quantumcrates.model;

import com.google.gson.annotations.SerializedName;
import me.bintanq.quantumcrates.model.reward.Reward;
import me.bintanq.quantumcrates.scheduler.CrateSchedule;

import java.util.ArrayList;
import java.util.List;

/**
 * Crate — the full definition of a crate, GSON-serializable.
 *
 * Stored as /plugins/QuantumCrates/crates/{id}.json and synced
 * to the Web Interface via WebSocket (Phase 2).
 */
public class Crate {

    @SerializedName("id")
    private String id;

    @SerializedName("displayName")
    private String displayName;

    @SerializedName("hologramLines")
    private List<String> hologramLines = new ArrayList<>();

    /** Physical location of the crate block in the world. */
    @SerializedName("location")
    private SerializableLocation location;

    /** Required keys to open this crate. Supports multi-key combos. */
    @SerializedName("requiredKeys")
    private List<KeyRequirement> requiredKeys = new ArrayList<>();

    /** All possible rewards from this crate. */
    @SerializedName("rewards")
    private List<Reward> rewards = new ArrayList<>();

    /** Cooldown in milliseconds between openings per player. 0 = no cooldown. */
    @SerializedName("cooldownMs")
    private long cooldownMs = 0;

    /** Pity configuration for this crate. */
    @SerializedName("pity")
    private PityConfig pity = new PityConfig();

    /** Optional scheduling: when this crate is openable. Null = always open. */
    @SerializedName("schedule")
    private CrateSchedule schedule = null;

    /** Preview GUI configuration — customizable per crate. */
    @SerializedName("preview")
    private PreviewConfig preview = new PreviewConfig();

    /** Whether mass-open is allowed for this crate. */
    @SerializedName("massOpenEnabled")
    private boolean massOpenEnabled = true;

    /** Max openings per mass-open call. -1 = unlimited. */
    @SerializedName("massOpenLimit")
    private int massOpenLimit = -1;

    /** Particle effect identifier (references particle config key). */
    @SerializedName("idleParticle")
    private String idleParticle = "VILLAGER_HAPPY";

    @SerializedName("openParticle")
    private String openParticle = "FIREWORKS_SPARK";

    @SerializedName("enabled")
    private boolean enabled = true;

    /* ─────────────────────── Inner Classes ─────────────────────── */

    /**
     * A single key requirement entry.
     * Supports virtual keys, physical items, and custom item plugin keys.
     */
    public static class KeyRequirement {
        @SerializedName("keyId")
        private String keyId;

        @SerializedName("amount")
        private int amount = 1;

        @SerializedName("type")
        private KeyType type = KeyType.VIRTUAL;

        public KeyRequirement() {}
        public KeyRequirement(String keyId, int amount, KeyType type) {
            this.keyId  = keyId;
            this.amount = amount;
            this.type   = type;
        }

        public String getKeyId() { return keyId; }
        public int getAmount() { return amount; }
        public KeyType getType() { return type; }
    }

    public enum KeyType {
        /** Stored in database balance */
        VIRTUAL,
        /** Physical item in player inventory */
        PHYSICAL,
        /** Key from MMOItems */
        MMOITEMS,
        /** Key from ItemsAdder */
        ITEMSADDER,
        /** Key from Oraxen */
        ORAXEN
    }

    /**
     * Pity system configuration per crate.
     */
    public static class PityConfig {
        /** Pity is active only if enabled. */
        @SerializedName("enabled")
        private boolean enabled = false;

        /** After this many opens without a rare, guarantee triggers. */
        @SerializedName("threshold")
        private int threshold = 50;

        /**
         * Minimum rarity tier that counts as a "rare" for pity purposes.
         * Matches Reward.rarity values defined in reward config.
         */
        @SerializedName("rareRarityMinimum")
        private String rareRarityMinimum = "RARE";

        /**
         * Bonus chance percent added per open after softPityStart.
         * E.g. 2.0 means +2% per open over soft-pity threshold.
         */
        @SerializedName("bonusChancePerOpen")
        private double bonusChancePerOpen = 2.0;

        /**
         * Soft pity kicks in at this count, adding bonus chance per open.
         * Hard pity at {@code threshold} guarantees a rare.
         */
        @SerializedName("softPityStart")
        private int softPityStart = 40;

        public boolean isEnabled() { return enabled; }
        public int getThreshold() { return threshold; }
        public String getRareRarityMinimum() { return rareRarityMinimum; }
        public double getBonusChancePerOpen() { return bonusChancePerOpen; }
        public int getSoftPityStart() { return softPityStart; }
    }

    /**
     * Preview GUI configuration — fully customizable per crate.
     */
    public static class PreviewConfig {

        /** Title yang muncul di atas inventory. Supports & color codes. Null = pakai default. */
        @SerializedName("title")
        private String title = null;

        /**
         * Sort order untuk reward di preview.
         * RARITY_DESC   → rare di atas (default)
         * RARITY_ASC    → common di atas
         * WEIGHT_DESC   → weight terbesar di atas
         * WEIGHT_ASC    → weight terkecil di atas
         * CONFIG_ORDER  → urutan sesuai definisi di JSON (tidak di-sort)
         */
        @SerializedName("sortOrder")
        private SortOrder sortOrder = SortOrder.RARITY_DESC;

        /**
         * Material border inventory.
         * Null = auto (ditentukan dari rarity tertinggi di crate).
         * Isi nama material Bukkit untuk override, e.g. "CYAN_STAINED_GLASS_PANE".
         */
        @SerializedName("borderMaterial")
        private String borderMaterial = null;

        /**
         * Apakah nampilin info pity counter player di info item.
         */
        @SerializedName("showPity")
        private boolean showPity = true;

        /**
         * Apakah nampilin key balance player di info item.
         */
        @SerializedName("showKeyBalance")
        private boolean showKeyBalance = true;

        /**
         * Apakah nampilin chance % tiap reward.
         * Beberapa server sengaja sembunyikan ini biar ada unsur misteri.
         */
        @SerializedName("showChance")
        private boolean showChance = true;

        /**
         * Apakah nampilin weight numerik tiap reward.
         */
        @SerializedName("showWeight")
        private boolean showWeight = false;

        /**
         * Format string untuk chance. Placeholder: {chance}, {rarity}, {weight}.
         * Default: "§7Chance: §e{chance}%"
         */
        @SerializedName("chanceFormat")
        private String chanceFormat = "§7Chance: §e{chance}%";

        /**
         * Material untuk tombol navigasi Prev/Next.
         */
        @SerializedName("prevButtonMaterial")
        private String prevButtonMaterial = "ARROW";

        @SerializedName("nextButtonMaterial")
        private String nextButtonMaterial = "ARROW";

        /**
         * Material untuk tombol Close.
         */
        @SerializedName("closeButtonMaterial")
        private String closeButtonMaterial = "BARRIER";

        /**
         * Custom lore tambahan yang muncul di bawah stats tiap reward item.
         * Supports placeholders: {chance}, {rarity}, {weight}, {amount}.
         */
        @SerializedName("rewardFooterLore")
        private java.util.List<String> rewardFooterLore = new java.util.ArrayList<>();

        /**
         * Apakah nampilin item asli reward (true) atau icon kustom (false).
         * Kalau false, gunakan iconMaterial dari reward jika ada.
         */
        @SerializedName("showActualItem")
        private boolean showActualItem = true;

        public enum SortOrder {
            RARITY_DESC,
            RARITY_ASC,
            WEIGHT_DESC,
            WEIGHT_ASC,
            CONFIG_ORDER
        }

        // Getters
        public String getTitle()                    { return title; }
        public SortOrder getSortOrder()             { return sortOrder; }
        public String getBorderMaterial()           { return borderMaterial; }
        public boolean isShowPity()                 { return showPity; }
        public boolean isShowKeyBalance()           { return showKeyBalance; }
        public boolean isShowChance()               { return showChance; }
        public boolean isShowWeight()               { return showWeight; }
        public String getChanceFormat()             { return chanceFormat; }
        public String getPrevButtonMaterial()       { return prevButtonMaterial; }
        public String getNextButtonMaterial()       { return nextButtonMaterial; }
        public String getCloseButtonMaterial()      { return closeButtonMaterial; }
        public java.util.List<String> getRewardFooterLore() { return rewardFooterLore; }
        public boolean isShowActualItem()           { return showActualItem; }
    }

    /**
     * Serializable world location (avoids Bukkit Location serialization issues).
     */
    public static class SerializableLocation {
        @SerializedName("world")  public String world;
        @SerializedName("x")      public double x;
        @SerializedName("y")      public double y;
        @SerializedName("z")      public double z;
        @SerializedName("yaw")    public float yaw;
        @SerializedName("pitch")  public float pitch;

        public SerializableLocation() {}
        public SerializableLocation(String world, double x, double y, double z) {
            this.world = world; this.x = x; this.y = y; this.z = z;
        }
    }

    /* ─────────────────────── Computed Helpers ─────────────────────── */

    /**
     * Returns the total weight sum of all rewards.
     * Used by RewardProcessor for probability calculation.
     */
    public double getTotalWeight() {
        return rewards.stream().mapToDouble(Reward::getWeight).sum();
    }

    /**
     * Returns true if this crate is currently openable based on schedule.
     */
    public boolean isCurrentlyOpenable() {
        if (schedule == null) return true;
        return schedule.isCurrentlyActive();
    }

    /* ─────────────────────── Getters / Setters ─────────────────────── */

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public List<String> getHologramLines() { return hologramLines; }
    public SerializableLocation getLocation() { return location; }
    public void setLocation(SerializableLocation location) { this.location = location; }

    public List<KeyRequirement> getRequiredKeys() { return requiredKeys; }
    public List<Reward> getRewards() { return rewards; }

    public long getCooldownMs() { return cooldownMs; }
    public void setCooldownMs(long cooldownMs) { this.cooldownMs = cooldownMs; }

    public PityConfig getPity() { return pity; }
    public CrateSchedule getSchedule() { return schedule; }
    public void setSchedule(CrateSchedule schedule) { this.schedule = schedule; }

    public PreviewConfig getPreview() { return preview != null ? preview : new PreviewConfig(); }
    public boolean isMassOpenEnabled() { return massOpenEnabled; }
    public int getMassOpenLimit() { return massOpenLimit; }

    public String getIdleParticle() { return idleParticle; }
    public String getOpenParticle() { return openParticle; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
