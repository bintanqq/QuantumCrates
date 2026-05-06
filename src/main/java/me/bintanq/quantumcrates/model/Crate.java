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

    @SerializedName("hologramHeight")
    private double hologramHeight = 1.2;

    @SerializedName("locations")
    private List<SerializableLocation> locations = new ArrayList<>();

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

    @SerializedName("accessDeniedKnockback")
    private boolean accessDeniedKnockback = false;

    @SerializedName("knockbackStrength")
    private double knockbackStrength = 0.6;


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

        public String getType()     { return type; }
        public String getParticle() { return particle; }

    }

    @SerializedName("guiAnimation")
    private GuiAnimationType guiAnimation = GuiAnimationType.ROULETTE;

    @SerializedName("guiAnimationSpeed")
    private double guiAnimationSpeed = 1.0;

    @SerializedName("particleAnimationSpeed")
    private double particleAnimationSpeed = 1.0;

    @SerializedName("openSound")
    private String openSound = "BLOCK_NOTE_BLOCK_HAT";

    @SerializedName("winSound")
    private String winSound = "UI_TOAST_CHALLENGE_COMPLETE";

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

    public enum GuiAnimationType {
        ROULETTE, SHUFFLER, BOUNDARY, SINGLE_SPIN, FLICKER
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

    @SerializedName("location")
    @com.google.gson.annotations.Expose(serialize = false, deserialize = true)
    private SerializableLocation _legacyLocation = null;

    public void migrateLegacyLocation() {
        if (_legacyLocation != null) {
            addLocation(_legacyLocation);
            _legacyLocation = null;
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

    public boolean hasLocationAt(String world, int bx, int by, int bz) {
        return getLocations().stream().anyMatch(loc ->
                loc != null
                        && loc.world != null
                        && loc.world.equals(world)
                        && (int) loc.x == bx
                        && (int) loc.y == by
                        && (int) loc.z == bz);
    }

    public boolean addLocation(SerializableLocation loc) {
        if (hasLocationAt(loc.world, (int) loc.x, (int) loc.y, (int) loc.z)) return false;
        getLocations().add(loc);
        return true;
    }

    public boolean removeLocation(int index) {
        if (index < 0 || index >= getLocations().size()) return false;
        getLocations().remove(index);
        return true;
    }

    /* ─────────────────────── Getters / Setters ─────────────────────── */


    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public List<String> getHologramLines() { return hologramLines; }
    public List<SerializableLocation> getLocations() {
        if (locations == null) locations = new ArrayList<>();
        return locations;
    }
    public double getHologramHeight() { return hologramHeight; }
    public void setLocations(List<SerializableLocation> locations) {
        this.locations = locations;
    }


    public boolean isAccessDeniedKnockback() { return accessDeniedKnockback; }
    public void setAccessDeniedKnockback(boolean v) { this.accessDeniedKnockback = v; }
    public double getKnockbackStrength() { return Math.max(0, Math.min(knockbackStrength, 3.0)); }
    public void setKnockbackStrength(double v) { this.knockbackStrength = v; }

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

    public GuiAnimationType getGuiAnimation() { return guiAnimation != null ? guiAnimation : GuiAnimationType.ROULETTE; }
    public void setGuiAnimation(GuiAnimationType guiAnimation) { this.guiAnimation = guiAnimation; }

    public double getGuiAnimationSpeed() { return guiAnimationSpeed > 0 ? guiAnimationSpeed : 1.0; }
    public void setGuiAnimationSpeed(double guiAnimationSpeed) { this.guiAnimationSpeed = guiAnimationSpeed; }

    public double getParticleAnimationSpeed() { return particleAnimationSpeed > 0 ? particleAnimationSpeed : 1.0; }
    public void setParticleAnimationSpeed(double particleAnimationSpeed) { this.particleAnimationSpeed = particleAnimationSpeed; }

    public String getOpenSound() { return openSound != null ? openSound : "BLOCK_NOTE_BLOCK_HAT"; }
    public void setOpenSound(String openSound) { this.openSound = openSound; }

    public String getWinSound() { return winSound != null ? winSound : "UI_TOAST_CHALLENGE_COMPLETE"; }
    public void setWinSound(String winSound) { this.winSound = winSound; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}