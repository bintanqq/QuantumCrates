package me.bintanq.quantumcrates.manager;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.model.RarityDefinition;
import me.bintanq.quantumcrates.util.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RarityManager — central registry for all user-defined rarity tiers.
 *
 * Replaces every hardcoded COMMON/UNCOMMON/RARE/EPIC/LEGENDARY/MYTHIC reference
 * throughout the plugin. Rarities are loaded from rarities.yml in the plugin folder.
 *
 * Usage:
 *   RarityManager rm = plugin.getRarityManager();
 *   String color      = rm.getColor("RARE");       // "§9"
 *   int    order      = rm.getOrder("LEGENDARY");  // 4
 *   boolean isHigher  = rm.isAtOrAbove("EPIC", "RARE"); // true
 *   List<String> ids  = rm.getIdsAtOrAbove("RARE"); // ["RARE","EPIC","LEGENDARY","MYTHIC"]
 */
public class RarityManager {

    private final QuantumCrates plugin;

    /** Ordered list of rarity definitions, sorted by order ascending (lowest first). */
    private final List<RarityDefinition> rarities = new ArrayList<>();

    /** id (uppercased) → definition for O(1) lookup. */
    private final Map<String, RarityDefinition> byId = new LinkedHashMap<>();

    public RarityManager(QuantumCrates plugin) {
        this.plugin = plugin;
        reload();
    }

    /* ─────────────────────── Load / Reload ─────────────────────── */

    public void reload() {
        rarities.clear();
        byId.clear();

        File file = new File(plugin.getDataFolder(), "rarities.yml");
        if (!file.exists()) {
            saveDefaultRarities(file);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection section = config.getConfigurationSection("rarities");
        if (section == null) {
            Logger.warn("rarities.yml has no 'rarities' section — using defaults.");
            loadDefaults();
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection r = section.getConfigurationSection(key);
            if (r == null) continue;

            RarityDefinition def = new RarityDefinition(
                    key.toUpperCase(),
                    r.getString("displayName", key),
                    r.getString("color", "&f"),
                    r.getString("hexColor", "#aaaaaa"),
                    r.getInt("order", 0),
                    r.getString("borderMaterial", "GRAY_STAINED_GLASS_PANE"),
                    r.getString("icon", "⬜")
            );
            rarities.add(def);
            byId.put(key.toUpperCase(), def);
        }

        rarities.sort(Comparator.comparingInt(RarityDefinition::getOrder));

        if (rarities.isEmpty()) {
            Logger.warn("rarities.yml is empty — using defaults.");
            loadDefaults();
            return;
        }

        Logger.info("Loaded &e" + rarities.size() + " &frarity tiers: "
                + rarities.stream().map(RarityDefinition::getId).collect(Collectors.joining(", ")));
    }

    /* ─────────────────────── Lookup ─────────────────────── */

    public RarityDefinition get(String id) {
        if (id == null) return getFallback();
        RarityDefinition def = byId.get(id.toUpperCase());
        return def != null ? def : getFallback();
    }

    public boolean exists(String id) {
        return id != null && byId.containsKey(id.toUpperCase());
    }

    /** Returns the Minecraft color code (§ prefix) for the given rarity ID. */
    public String getColor(String rarityId) {
        return get(rarityId).getColorCode();
    }

    /** Returns the hex color for web UI. */
    public String getHexColor(String rarityId) {
        return get(rarityId).getHexColor();
    }

    /** Returns the tier order (higher = rarer). */
    public int getOrder(String rarityId) {
        return get(rarityId).getOrder();
    }

    /** Returns the border material for Preview GUI. */
    public Material getBorderMaterial(String rarityId) {
        String matName = get(rarityId).getBorderMaterial();
        Material mat = matName != null ? Material.matchMaterial(matName.toUpperCase()) : null;
        return mat != null ? mat : Material.GRAY_STAINED_GLASS_PANE;
    }

    /** Returns all rarity IDs sorted ascending by order. */
    public List<String> getAllIds() {
        return rarities.stream().map(RarityDefinition::getId).collect(Collectors.toList());
    }

    /** Returns all definitions sorted ascending by order. */
    public List<RarityDefinition> getAll() {
        return Collections.unmodifiableList(rarities);
    }

    /* ─────────────────────── Comparison Helpers ─────────────────────── */

    /**
     * Returns true if rarityId is at or above the minimumId tier.
     * Used by pity system.
     */
    public boolean isAtOrAbove(String rarityId, String minimumId) {
        return getOrder(rarityId) >= getOrder(minimumId);
    }

    /**
     * Returns all rarity IDs at or above the given minimum (inclusive).
     * Sorted ascending. Used by RewardProcessor for pity calculations.
     */
    public List<String> getIdsAtOrAbove(String minimumId) {
        int minOrder = getOrder(minimumId);
        return rarities.stream()
                .filter(r -> r.getOrder() >= minOrder)
                .map(RarityDefinition::getId)
                .collect(Collectors.toList());
    }

    /**
     * Returns the highest-order rarity among a collection of rarity IDs.
     * Returns the lowest rarity if list is empty.
     */
    public String getHighestRarity(Collection<String> rarityIds) {
        return rarityIds.stream()
                .max(Comparator.comparingInt(this::getOrder))
                .orElse(getLowestId());
    }

    /** Returns the ID of the lowest-tier rarity. */
    public String getLowestId() {
        return rarities.isEmpty() ? "COMMON" : rarities.get(0).getId();
    }

    /** Returns the ID of the highest-tier rarity. */
    public String getHighestId() {
        return rarities.isEmpty() ? "MYTHIC" : rarities.get(rarities.size() - 1).getId();
    }

    /* ─────────────────────── REST API serialization ─────────────────────── */

    /**
     * Save updated rarities back to rarities.yml.
     * Called from WebServer REST API.
     */
    public void saveRarities(List<RarityDefinition> updated) {
        File file = new File(plugin.getDataFolder(), "rarities.yml");
        YamlConfiguration config = new YamlConfiguration();

        for (RarityDefinition def : updated) {
            String path = "rarities." + def.getId().toLowerCase();
            config.set(path + ".displayName",    def.getDisplayName());
            config.set(path + ".color",          def.getColor());
            config.set(path + ".hexColor",       def.getHexColor());
            config.set(path + ".order",          def.getOrder());
            config.set(path + ".borderMaterial", def.getBorderMaterial());
            config.set(path + ".icon",           def.getIcon());
        }

        try {
            config.save(file);
            reload();
            Logger.info("Rarities saved and reloaded — &e" + updated.size() + " &ftiers.");
        } catch (Exception e) {
            Logger.severe("Failed to save rarities.yml: " + e.getMessage());
        }
    }

    /* ─────────────────────── Defaults ─────────────────────── */

    private void loadDefaults() {
        List<RarityDefinition> defaults = defaultRarities();
        defaults.forEach(r -> {
            rarities.add(r);
            byId.put(r.getId(), r);
        });
        rarities.sort(Comparator.comparingInt(RarityDefinition::getOrder));
    }

    private void saveDefaultRarities(File file) {
        try {
            file.getParentFile().mkdirs();
            InputStream in = plugin.getResource("rarities.yml");
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                defaults.save(file);
                Logger.info("Created default rarities.yml");
                return;
            }
        } catch (Exception ignored) {}

        // Fallback: write defaults programmatically
        YamlConfiguration config = new YamlConfiguration();
        for (RarityDefinition def : defaultRarities()) {
            String path = "rarities." + def.getId().toLowerCase();
            config.set(path + ".displayName",    def.getDisplayName());
            config.set(path + ".color",          def.getColor());
            config.set(path + ".hexColor",       def.getHexColor());
            config.set(path + ".order",          def.getOrder());
            config.set(path + ".borderMaterial", def.getBorderMaterial());
            config.set(path + ".icon",           def.getIcon());
        }
        try {
            config.save(file);
            Logger.info("Created default rarities.yml");
        } catch (Exception e) {
            Logger.severe("Failed to create rarities.yml: " + e.getMessage());
        }
    }

    private List<RarityDefinition> defaultRarities() {
        return List.of(
                new RarityDefinition("COMMON",    "Common",    "&f", "#aaaaaa", 0, "GRAY_STAINED_GLASS_PANE",   "⬜"),
                new RarityDefinition("UNCOMMON",  "Uncommon",  "&a", "#00d97e", 1, "GREEN_STAINED_GLASS_PANE",  "🟩"),
                new RarityDefinition("RARE",      "Rare",      "&9", "#4488ff", 2, "BLUE_STAINED_GLASS_PANE",   "🔷"),
                new RarityDefinition("EPIC",      "Epic",      "&5", "#9b59f5", 3, "MAGENTA_STAINED_GLASS_PANE","🟣"),
                new RarityDefinition("LEGENDARY", "Legendary", "&6", "#f5a623", 4, "ORANGE_STAINED_GLASS_PANE", "🟡"),
                new RarityDefinition("MYTHIC",    "Mythic",    "&d", "#ff44aa", 5, "PURPLE_STAINED_GLASS_PANE", "🩷")
        );
    }

    private RarityDefinition getFallback() {
        return rarities.isEmpty()
                ? new RarityDefinition("COMMON", "Common", "&f", "#aaaaaa", 0, "GRAY_STAINED_GLASS_PANE", "⬜")
                : rarities.get(0);
    }
}