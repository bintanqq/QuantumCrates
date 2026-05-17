package me.bintanq.quantumcrates.util;

import me.bintanq.quantumcrates.QuantumCrates;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * ConfigMigrator — automatic forward migration for config.yml, rarities.yml,
 * and crate JSON files.
 *
 * <p>
 * Each config file has an internal {@code config-version} key. When the plugin
 * detects an older version, it applies incremental migrations to add new keys
 * with sensible defaults. Old or incomplete configs never crash the plugin.
 * </p>
 */
public final class ConfigMigrator {

    // Current schema versions
    private static final int CONFIG_VERSION = 3;
    private static final int RARITIES_VERSION = 1;

    private ConfigMigrator() {
    }

    /**
     * Migrates config.yml if needed. Adds new keys with defaults silently.
     *
     * @param plugin the plugin instance
     */
    public static void migrateConfig(QuantumCrates plugin) {
        try {
            FileConfiguration config = plugin.getConfig();
            int currentVersion = config.getInt("config-version", 1);

            if (currentVersion >= CONFIG_VERSION)
                return;

            Logger.info("Migrating config.yml from v" + currentVersion + " → v" + CONFIG_VERSION + "...");

            // v1 → v2: add update checker, vault, and config-version keys
            if (currentVersion < 2) {
                setIfAbsent(config, "settings.check-updates", true,
                        "# Whether to check for plugin updates on startup (async, no lag).");
                setIfAbsent(config, "settings.notify-ops-on-join", true,
                        "# Notify operators on join if an update is available.");

                // Vault economy integration
                setIfAbsent(config, "vault.enabled", true,
                        "# Enable Vault economy integration for money rewards.");
                setIfAbsent(config, "vault.currency-format", "${amount}",
                        "# Format string for economy amounts. {amount} is replaced with the value.");
            }

            // v2 → v3: add missing message keys
            if (currentVersion < 3) {
                setIfAbsent(config, "messages.key-invalid", "&cInvalid key name: &e{key}", null);
            }

            config.set("config-version", CONFIG_VERSION);
            plugin.saveConfig();
            Logger.info("&aconfig.yml migrated to v" + CONFIG_VERSION + " successfully.");
        } catch (Exception e) {
            Logger.warn("Config migration failed (continuing with defaults): " + e.getMessage());
        }
    }

    /**
     * Migrates rarities.yml if needed.
     *
     * @param plugin the plugin instance
     */
    public static void migrateRarities(QuantumCrates plugin) {
        try {
            File raritiesFile = new File(plugin.getDataFolder(), "rarities.yml");
            if (!raritiesFile.exists())
                return;

            YamlConfiguration config = YamlConfiguration.loadConfiguration(raritiesFile);
            int currentVersion = config.getInt("config-version", 0);

            if (currentVersion >= RARITIES_VERSION)
                return;

            Logger.info("Migrating rarities.yml from v" + currentVersion + " → v" + RARITIES_VERSION + "...");

            // v0 → v1: just stamp the version — rarities schema hasn't changed
            config.set("config-version", RARITIES_VERSION);
            config.save(raritiesFile);
            Logger.info("&ararities.yml migrated to v" + RARITIES_VERSION + " successfully.");
        } catch (Exception e) {
            Logger.warn("Rarities migration failed (continuing with defaults): " + e.getMessage());
        }
    }

    /**
     * Migrates crate JSON files if needed. Adds missing fields with defaults.
     *
     * @param plugin the plugin instance
     */
    public static void migrateCrateFiles(QuantumCrates plugin) {
        try {
            File cratesDir = new File(plugin.getDataFolder(), "crates");
            if (!cratesDir.exists() || !cratesDir.isDirectory())
                return;

            File[] files = cratesDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (files == null)
                return;

            int migrated = 0;
            for (File file : files) {
                if (migrateSingleCrateFile(file))
                    migrated++;
            }
            if (migrated > 0) {
                Logger.info("&aMigrated " + migrated + " crate file(s) with missing fields.");
            }
        } catch (Exception e) {
            Logger.warn("Crate file migration failed: " + e.getMessage());
        }
    }

    private static boolean migrateSingleCrateFile(File file) {
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            boolean changed = false;

            // Add missing fields with defaults using simple JSON manipulation
            // This ensures old crate files from earlier versions don't crash
            if (!content.contains("\"openRateLimit\"")) {
                content = insertJsonField(content, "openRateLimit", "0");
                changed = true;
            }
            if (!content.contains("\"lifetimeOpenLimit\"")) {
                content = insertJsonField(content, "lifetimeOpenLimit", "0");
                changed = true;
            }
            if (!content.contains("\"accessDeniedKnockback\"")) {
                content = insertJsonField(content, "accessDeniedKnockback", "false");
                changed = true;
            }
            if (!content.contains("\"knockbackStrength\"")) {
                content = insertJsonField(content, "knockbackStrength", "0.6");
                changed = true;
            }
            if (!content.contains("\"guiAnimationSpeed\"")) {
                content = insertJsonField(content, "guiAnimationSpeed", "1.0");
                changed = true;
            }
            if (!content.contains("\"particleAnimationSpeed\"")) {
                content = insertJsonField(content, "particleAnimationSpeed", "1.0");
                changed = true;
            }

            if (changed) {
                try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                    writer.write(content);
                }
                return true;
            }
        } catch (Exception e) {
            Logger.debug("Failed to migrate crate file " + file.getName() + ": " + e.getMessage());
        }
        return false;
    }

    /**
     * Inserts a JSON field before the last closing brace.
     */
    private static String insertJsonField(String json, String key, String value) {
        int lastBrace = json.lastIndexOf('}');
        if (lastBrace < 0)
            return json;

        // Check if we need a comma
        String before = json.substring(0, lastBrace).stripTrailing();
        boolean needsComma = !before.endsWith("{") && !before.endsWith(",");

        String insertion = (needsComma ? "," : "") + "\n  \"" + key + "\": " + value + "\n";
        return json.substring(0, lastBrace) + insertion + json.substring(lastBrace);
    }

    private static void setIfAbsent(FileConfiguration config, String path, Object value, String comment) {
        if (!config.contains(path)) {
            config.set(path, value);
        }
    }
}
