package me.bintanq.quantumcrates.model.reward;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * Reward — a single possible reward inside a crate.
 *
 * Fully GSON-serializable. The {@code type} field drives which hook
 * the RewardProcessor will use to materialize the actual item/command.
 */
public class Reward {

    @SerializedName("id")
    private String id;

    /** Display name shown in preview GUI and log. */
    @SerializedName("displayName")
    private String displayName;

    /**
     * Probability weight. Higher weight = more likely.
     * Not a direct percentage — relative to sum of all crate weights.
     */
    @SerializedName("weight")
    private double weight = 1.0;

    /** Rarity tier label (COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, etc.) */
    @SerializedName("rarity")
    private String rarity = "COMMON";

    /** Source type for materializing this reward. */
    @SerializedName("type")
    private RewardType type = RewardType.VANILLA;

    /* ── Vanilla Item ── */

    /** Material name, used when type = VANILLA. */
    @SerializedName("material")
    private String material;

    @SerializedName("amount")
    private int amount = 1;

    /** Optional custom display name for vanilla item. */
    @SerializedName("itemName")
    private String itemName;

    @SerializedName("lore")
    private List<String> lore = new ArrayList<>();

    @SerializedName("enchantments")
    private List<EnchantmentEntry> enchantments = new ArrayList<>();

    @SerializedName("customModelData")
    private int customModelData = -1;

    /* ── Custom Item Plugins ── */

    /** MMOItems type (e.g. "SWORD"). Used when type = MMOITEMS. */
    @SerializedName("mmoItemsType")
    private String mmoItemsType;

    /** MMOItems item ID. Used when type = MMOITEMS. */
    @SerializedName("mmoItemsId")
    private String mmoItemsId;

    /** ItemsAdder namespaced ID (e.g. "itemsadder:ruby"). */
    @SerializedName("itemsAdderId")
    private String itemsAdderId;

    /** Oraxen item ID. */
    @SerializedName("oraxenId")
    private String oraxenId;

    /* ── Commands ── */

    /**
     * Commands to run when this reward is given.
     * Use %player% placeholder. Prefix with "console:" or "player:" to denote executor.
     * Default executor is console if no prefix.
     */
    @SerializedName("commands")
    private List<String> commands = new ArrayList<>();

    /** Whether to broadcast the reward to the server. */
    @SerializedName("broadcast")
    private boolean broadcast = false;

    @SerializedName("broadcastMessage")
    private String broadcastMessage = "&e{player} &7won &e{reward} &7from a crate!";

    /* ─────────────────────── Inner Classes ─────────────────────── */

    public enum RewardType {
        /** Standard Bukkit ItemStack */
        VANILLA,
        /** MMOItems custom item */
        MMOITEMS,
        /** ItemsAdder custom item */
        ITEMSADDER,
        /** Oraxen custom item */
        ORAXEN,
        /** Command-only reward (no item given) */
        COMMAND,
        /** Item + command */
        VANILLA_WITH_COMMANDS
    }

    public static class EnchantmentEntry {
        @SerializedName("enchantment") public String enchantment;
        @SerializedName("level")       public int    level = 1;

        public EnchantmentEntry() {}
        public EnchantmentEntry(String enchantment, int level) {
            this.enchantment = enchantment;
            this.level = level;
        }
    }

    /* ─────────────────────── Computed ─────────────────────── */

    /**
     * Calculates the display percentage of this reward given the total crate weight.
     * Used in Preview GUI.
     */
    public double calculatePercentage(double totalWeight) {
        if (totalWeight <= 0) return 0;
        return (weight / totalWeight) * 100.0;
    }

    public boolean hasCommands() {
        return commands != null && !commands.isEmpty();
    }

    public boolean isCommandOnly() {
        return type == RewardType.COMMAND;
    }

    /* ─────────────────────── Getters ─────────────────────── */

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }

    public String getRarity() { return rarity; }
    public RewardType getType() { return type; }

    public String getMaterial() { return material; }
    public int getAmount() { return amount; }
    public String getItemName() { return itemName; }
    public List<String> getLore() { return lore; }
    public List<EnchantmentEntry> getEnchantments() { return enchantments; }
    public int getCustomModelData() { return customModelData; }

    public String getMmoItemsType() { return mmoItemsType; }
    public String getMmoItemsId() { return mmoItemsId; }
    public String getItemsAdderId() { return itemsAdderId; }
    public String getOraxenId() { return oraxenId; }

    public List<String> getCommands() { return commands; }
    public boolean isBroadcast() { return broadcast; }
    public String getBroadcastMessage() { return broadcastMessage; }
}
