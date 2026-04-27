package me.bintanq.quantumcrates.model;

import com.google.gson.annotations.SerializedName;

/**
 * RarityDefinition — a single user-defined rarity tier.
 *
 * Stored in rarities.yml (via Bukkit config) and loaded by RarityManager.
 * Replaces all hardcoded COMMON/UNCOMMON/RARE/EPIC/LEGENDARY/MYTHIC references.
 *
 * Example rarities.yml entry:
 *   rarities:
 *     common:
 *       displayName: "Common"
 *       color: "&f"
 *       hexColor: "#aaaaaa"
 *       order: 0
 *       borderMaterial: "GRAY_STAINED_GLASS_PANE"
 */
public class RarityDefinition {

    /** Internal ID used in reward JSON and comparisons. Case-insensitive. */
    @SerializedName("id")
    private String id;

    /** Human-readable display name shown in GUI and messages. */
    @SerializedName("displayName")
    private String displayName;

    /**
     * Minecraft color code prefix (& codes) applied to items of this rarity.
     * E.g. "&6" for gold/legendary.
     */
    @SerializedName("color")
    private String color;

    /**
     * Hex color used in the web dashboard UI (CSS).
     * E.g. "#f5a623"
     */
    @SerializedName("hexColor")
    private String hexColor;

    /**
     * Sort order / tier index. 0 = lowest (most common).
     * Used by pity system to compare "minimum rarity" thresholds.
     */
    @SerializedName("order")
    private int order;

    /**
     * Bukkit Material name for border in Preview GUI when this is the highest rarity.
     * E.g. "ORANGE_STAINED_GLASS_PANE"
     */
    @SerializedName("borderMaterial")
    private String borderMaterial;

    /**
     * Unicode or emoji icon for web dashboard display.
     * E.g. "⭐" or "💎"
     */
    @SerializedName("icon")
    private String icon;

    public RarityDefinition() {}

    public RarityDefinition(String id, String displayName, String color,
                            String hexColor, int order, String borderMaterial, String icon) {
        this.id             = id;
        this.displayName    = displayName;
        this.color          = color;
        this.hexColor       = hexColor;
        this.order          = order;
        this.borderMaterial = borderMaterial;
        this.icon           = icon;
    }

    public String getId()             { return id; }
    public String getDisplayName()    { return displayName; }
    public String getColor()          { return color; }
    public String getHexColor()       { return hexColor; }
    public int    getOrder()          { return order; }
    public String getBorderMaterial() { return borderMaterial; }
    public String getIcon()           { return icon != null ? icon : "⬜"; }

    public void setId(String id)                   { this.id = id; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setColor(String color)             { this.color = color; }
    public void setHexColor(String hexColor)       { this.hexColor = hexColor; }
    public void setOrder(int order)                { this.order = order; }
    public void setBorderMaterial(String m)        { this.borderMaterial = m; }
    public void setIcon(String icon)               { this.icon = icon; }

    /** Returns the Minecraft color prefix with § instead of & */
    public String getColorCode() {
        return color != null ? color.replace("&", "\u00A7") : "\u00A7f";
    }
}