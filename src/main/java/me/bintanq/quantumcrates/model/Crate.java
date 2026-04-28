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
 * to the Web Interface via WebSocket.
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

    // NOTE: idleParticle and openParticle (legacy string fields) removed.
    // Particle type and effect are now fully defined inside idleAnimation and openAnimation.

    @SerializedName("idleAnimation")
    private AnimationConfig idleAnimation = new AnimationConfig();

    @SerializedName("openAnimation")
    private AnimationConfig openAnimation = new AnimationConfig();

    public static class AnimationConfig {
        @SerializedName("type")
        private String type = "RING";

        @SerializedName("particle")
        private String particle = "HAPPY_VILLAGER";

        @SerializedName("speed")
        private double speed = 1.0;

        @SerializedName("radius")
        private double radius = 1.0;

        @SerializedName("density")
        private int density = 8;

        public String getType()     { return type; }
        public String getParticle() { return particle; }
        public double getSpeed()    { return speed; }
        public double getRadius()   { return radius; }
        public int getDensity()     { return density; }
    }

    public AnimationConfig getIdleAnimation() { return idleAnimation != null ? idleAnimation : new AnimationConfig(); }
    public AnimationConfig getOpenAnimation() { return openAnimation != null ? openAnimation : new AnimationConfig(); }

    @SerializedName("enabled")
    private boolean enabled = true;

    /* ─────────────────────── Inner Classes ─────────────────────── */

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
        VIRTUAL,
        PHYSICAL,
        MMOITEMS,
        ITEMSADDER,
        ORAXEN
    }

    public static class PityConfig {
        @SerializedName("enabled")
        private boolean enabled = false;

        @SerializedName("threshold")
        private int threshold = 50;

        @SerializedName("rareRarityMinimum")
        private String rareRarityMinimum = "RARE";

        @SerializedName("bonusChancePerOpen")
        private double bonusChancePerOpen = 2.0;

        @SerializedName("softPityStart")
        private int softPityStart = 40;

        public boolean isEnabled() { return enabled; }
        public int getThreshold() { return threshold; }
        public String getRareRarityMinimum() { return rareRarityMinimum; }
        public double getBonusChancePerOpen() { return bonusChancePerOpen; }
        public int getSoftPityStart() { return softPityStart; }
    }

    public static class PreviewConfig {
        @SerializedName("title")
        private String title = null;

        @SerializedName("sortOrder")
        private SortOrder sortOrder = SortOrder.RARITY_DESC;

        @SerializedName("borderMaterial")
        private String borderMaterial = null;

        @SerializedName("showPity")
        private boolean showPity = true;

        @SerializedName("showKeyBalance")
        private boolean showKeyBalance = true;

        @SerializedName("showChance")
        private boolean showChance = true;

        @SerializedName("showWeight")
        private boolean showWeight = false;

        @SerializedName("chanceFormat")
        private String chanceFormat = "§7Chance: §e{chance}%";

        @SerializedName("prevButtonMaterial")
        private String prevButtonMaterial = "ARROW";

        @SerializedName("nextButtonMaterial")
        private String nextButtonMaterial = "ARROW";

        @SerializedName("closeButtonMaterial")
        private String closeButtonMaterial = "BARRIER";

        @SerializedName("rewardFooterLore")
        private java.util.List<String> rewardFooterLore = new java.util.ArrayList<>();

        @SerializedName("showActualItem")
        private boolean showActualItem = true;

        public enum SortOrder {
            RARITY_DESC, RARITY_ASC, WEIGHT_DESC, WEIGHT_ASC, CONFIG_ORDER
        }

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

    public double getTotalWeight() {
        return rewards.stream().mapToDouble(Reward::getWeight).sum();
    }

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

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}