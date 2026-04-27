package me.bintanq.quantumcrates.manager;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.log.LogManager;
import me.bintanq.quantumcrates.model.Crate;
import me.bintanq.quantumcrates.model.PlayerData;
import me.bintanq.quantumcrates.model.reward.RewardResult;
import me.bintanq.quantumcrates.processor.RewardProcessor;
import me.bintanq.quantumcrates.serializer.GsonProvider;
import me.bintanq.quantumcrates.util.Logger;
import me.bintanq.quantumcrates.util.MessageManager;
import me.bintanq.quantumcrates.util.TimeUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CrateManager — central orchestrator for all crate operations.
 *
 * Responsibilities:
 *  - Load/reload crate configs from /crates/*.json
 *  - Validate key requirements (multi-key, virtual, physical, custom plugins)
 *  - Enforce cooldown and schedule restrictions
 *  - Drive the roll pipeline (RewardProcessor → pity update → delivery → log)
 *  - Mass-open batch processing with per-tick throttling to avoid TPS spikes
 */
public class CrateManager {

    private final QuantumCrates   plugin;
    private final PlayerDataManager playerDataManager;
    private final RewardProcessor rewardProcessor;
    private final LogManager      logManager;
    private final KeyManager      keyManager;

    /** id → Crate loaded from JSON. */
    private final ConcurrentHashMap<String, Crate> crateRegistry = new ConcurrentHashMap<>();

    /** Players currently in an opening animation (prevents double-open). */
    private final Set<UUID> openingLock = ConcurrentHashMap.newKeySet();

    /** Directory where crate JSON files are stored. */
    private File cratesDir;

    public CrateManager(
            QuantumCrates plugin,
            PlayerDataManager playerDataManager,
            RewardProcessor rewardProcessor,
            LogManager logManager,
            KeyManager keyManager
    ) {
        this.plugin            = plugin;
        this.playerDataManager = playerDataManager;
        this.rewardProcessor   = rewardProcessor;
        this.logManager        = logManager;
        this.keyManager        = keyManager;
    }

    /* ─────────────────────── Config Loading ─────────────────────── */

    /**
     * Loads all *.json files from /plugins/QuantumCrates/crates/.
     * Clears existing registry first (reload-safe).
     */
    public void loadAllCrates() {
        cratesDir = new File(plugin.getDataFolder(), "crates");
        if (!cratesDir.exists()) {
            cratesDir.mkdirs();
            createExampleCrate();
        }

        crateRegistry.clear();
        File[] files = cratesDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            Logger.warn("No crate files found in /crates/. Create *.json files to define crates.");
            return;
        }

        int loaded = 0;
        for (File file : files) {
            try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
                Crate crate = GsonProvider.getGson().fromJson(reader, Crate.class);
                if (crate.getId() == null || crate.getId().isEmpty()) {
                    // Infer ID from file name if not set
                    crate.setId(file.getName().replace(".json", ""));
                }
                crateRegistry.put(crate.getId(), crate);
                loaded++;
                Logger.debug("Loaded crate: &e" + crate.getId());
            } catch (IOException e) {
                Logger.severe("Failed to load crate file '" + file.getName() + "': " + e.getMessage());
            }
        }
        Logger.info("Loaded &e" + loaded + " &fcrates.");
    }

    /**
     * Saves a crate's current state to its JSON file.
     * Used by Web Interface API (Phase 2) after remote edits.
     */
    public void saveCrate(Crate crate) {
        File file = new File(cratesDir, crate.getId() + ".json");
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            GsonProvider.getGson().toJson(crate, writer);
        } catch (IOException e) {
            Logger.severe("Failed to save crate '" + crate.getId() + "': " + e.getMessage());
        }
    }

    /* ─────────────────────── Open Validation ─────────────────────── */

    /**
     * Result enum for open attempt validation.
     */
    public enum OpenResult {
        SUCCESS,
        NOT_FOUND,
        DISABLED,
        NOT_SCHEDULED,
        ON_COOLDOWN,
        MISSING_KEY,
        ALREADY_OPENING
    }

    /**
     * Validates whether a player can open a crate right now.
     * Returns {@link OpenResult} without performing the actual open.
     * Main-thread safe.
     */
    public OpenResult canOpen(Player player, String crateId) {
        Crate crate = crateRegistry.get(crateId);
        if (crate == null)       return OpenResult.NOT_FOUND;
        if (!crate.isEnabled())  return OpenResult.DISABLED;
        if (openingLock.contains(player.getUniqueId())) return OpenResult.ALREADY_OPENING;
        if (!crate.isCurrentlyOpenable())               return OpenResult.NOT_SCHEDULED;

        PlayerData data = playerDataManager.getOrEmpty(player.getUniqueId());
        if (crate.getCooldownMs() > 0 && data.isOnCooldown(crateId, crate.getCooldownMs())) {
            return OpenResult.ON_COOLDOWN;
        }

        if (!keyManager.hasRequiredKeys(player, crate)) return OpenResult.MISSING_KEY;

        return OpenResult.SUCCESS;
    }

    /* ─────────────────────── Single Open ─────────────────────── */

    /**
     * Attempts to open a crate for a player.
     * Consumes keys, rolls reward, updates pity, delivers item, writes log.
     *
     * MUST be called from the main thread.
     *
     * @return true if the opening was successful
     */
    public boolean openCrate(Player player, String crateId) {
        OpenResult check = canOpen(player, crateId);
        if (check != OpenResult.SUCCESS) {
            sendOpenResultFeedback(player, check, crateId);
            return false;
        }

        Crate      crate = crateRegistry.get(crateId);
        PlayerData data  = playerDataManager.getOrEmpty(player.getUniqueId());

        // Lock player from double-opening
        openingLock.add(player.getUniqueId());

        try {
            // 1. Consume keys
            boolean consumed = keyManager.consumeKeys(player, crate);
            if (!consumed) {
                openingLock.remove(player.getUniqueId());
                return false;
            }

            // 2. Roll reward
            RewardResult result = rewardProcessor.roll(crate, data);

            // 3. Update pity
            boolean isRare = isRareReward(result.getReward().getRarity(), crate.getPity().getRareRarityMinimum());
            if (isRare || result.isPityGuaranteed()) {
                playerDataManager.resetPity(player.getUniqueId(), crateId);
                me.bintanq.quantumcrates.web.WebSocketBridge.getInstance()
                        .broadcastPityUpdate(player.getUniqueId(), crateId, 0, true);
            } else {
                playerDataManager.incrementPity(player.getUniqueId(), crateId);
                int newPity = playerDataManager.getPity(player.getUniqueId(), crateId);
                me.bintanq.quantumcrates.web.WebSocketBridge.getInstance()
                        .broadcastPityUpdate(player.getUniqueId(), crateId, newPity, false);
            }

            // 4. Update cooldown
            if (crate.getCooldownMs() > 0) {
                playerDataManager.setLastOpen(player.getUniqueId(), crateId);
            }

            // 5. Deliver reward (main thread)
            deliverReward(player, result);

            // 5b. Play opening particle effect
            if (plugin.getParticleManager() != null) {
                plugin.getParticleManager().playOpenEffect(crate, player.getLocation());
            }

            // 6. Broadcast
            if (result.getReward().isBroadcast()) {
                String msg = result.getReward().getBroadcastMessage()
                        .replace("{player}", player.getName())
                        .replace("{reward}", result.getReward().getDisplayName())
                        .replace("&", "\u00A7");
                plugin.getServer().broadcastMessage(msg);
            }

            // 7. Log (async via buffer)
            Location loc = player.getLocation();
            me.bintanq.quantumcrates.log.CrateLog crateLog = new me.bintanq.quantumcrates.log.CrateLog(
                    player.getUniqueId(), player.getName(),
                    crateId,
                    result.getReward().getId(),
                    result.getReward().getDisplayName(),
                    result.getPityAtRoll(),
                    System.currentTimeMillis(),
                    loc.getWorld() != null ? loc.getWorld().getName() : "unknown",
                    loc.getX(), loc.getY(), loc.getZ()
            );
            logManager.log(crateLog);

            // 8. Broadcast to WebSocket clients (Phase 2)
            me.bintanq.quantumcrates.web.WebSocketBridge.getInstance().broadcastCrateOpen(crateLog);

            // 9. Increment daily stats counter
            if (plugin.getStatsScheduler() != null) {
                plugin.getStatsScheduler().incrementOpenings();
            }

            return true;

        } finally {
            openingLock.remove(player.getUniqueId());
        }
    }

    /* ─────────────────────── Mass Open ─────────────────────── */

    /**
     * Performs a mass-open: opens up to {@code count} crates in sequence.
     *
     * To prevent TPS spikes, we batch 10 opens per tick and distribute
     * the remainder across subsequent ticks using a BukkitRunnable.
     *
     * @param player  The opener
     * @param crateId Target crate
     * @param count   Number of crates to open (-1 = open as many as player has keys for)
     */
    public void massOpen(Player player, String crateId, int count) {
        Crate crate = crateRegistry.get(crateId);
        if (crate == null || !crate.isMassOpenEnabled()) {
            MessageManager.send(player, "mass-open-disabled", "{crate}", crateId);
            return;
        }

        int maxAllowed = crate.getMassOpenLimit();
        int actual     = (count <= 0 || (maxAllowed > 0 && count > maxAllowed))
                         ? maxAllowed
                         : count;

        // Count how many opens the player can actually perform
        int canPerform = keyManager.countPossibleOpens(player, crate);
        actual = Math.min(actual <= 0 ? canPerform : actual, canPerform);

        if (actual <= 0) {
            sendOpenResultFeedback(player, OpenResult.MISSING_KEY, crateId);
            return;
        }

        final int totalOpens = actual;
        final int[] remaining = {totalOpens};
        final int[] successCount = {0};

        final int PER_TICK = 10; // opens per server tick — keep TPS stable

        new BukkitRunnable() {
            @Override
            public void run() {
                if (remaining[0] <= 0 || !player.isOnline()) {
                    if (successCount[0] > 0) {
                        MessageManager.send(player, "mass-open-success",
                                "{count}", String.valueOf(successCount[0]));
                    }
                    cancel();
                    return;
                }

                int batch = Math.min(PER_TICK, remaining[0]);
                for (int i = 0; i < batch; i++) {
                    // Check keys before each open in mass-open to be accurate
                    if (canOpen(player, crateId) != OpenResult.SUCCESS) {
                        remaining[0] = 0;
                        break;
                    }
                    boolean success = openCrate(player, crateId);
                    if (success) successCount[0]++;
                    remaining[0]--;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /* ─────────────────────── Reward Delivery ─────────────────────── */

    private void deliverReward(Player player, RewardResult result) {
        if (result.hasItem()) {
            ItemStack item = result.getItemStack();
            if (player.getInventory().firstEmpty() == -1) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
                MessageManager.send(player, "inventory-full",
                        "{reward}", result.getReward().getDisplayName());
            } else {
                player.getInventory().addItem(item);
            }
        }
        if (result.hasCommands()) {
            rewardProcessor.executeCommands(player, result);
        }
        MessageManager.send(player, "reward-received",
                "{reward}", result.getReward().getDisplayName());
    }

    /* ─────────────────────── Feedback Messages ─────────────────────── */

    private void sendOpenResultFeedback(Player player, OpenResult result, String crateId) {
        switch (result) {
            case NOT_FOUND   -> MessageManager.send(player, "crate-not-found", "{crate}", crateId);
            case DISABLED    -> MessageManager.send(player, "crate-disabled",  "{crate}", crateId);
            case NOT_SCHEDULED -> {
                Crate crate  = crateRegistry.get(crateId);
                String sched = crate != null && crate.getSchedule() != null
                               ? crate.getSchedule().getNextOpenDescription() : "Unknown";
                MessageManager.send(player, "crate-not-open", "{crate}", crateId, "{schedule}", sched);
            }
            case ON_COOLDOWN -> {
                Crate crate = crateRegistry.get(crateId);
                long rem    = crate != null
                              ? playerDataManager.getRemainingCooldown(player.getUniqueId(), crateId, crate.getCooldownMs())
                              : 0;
                MessageManager.send(player, "cooldown-active", "{time}", TimeUtil.formatDuration(rem));
            }
            case MISSING_KEY     -> MessageManager.send(player, "key-not-found", "{key}", crateId);
            case ALREADY_OPENING -> MessageManager.send(player, "already-opening");
            default              -> MessageManager.send(player, "crate-not-found", "{crate}", crateId);
        }
    }

    /* ─────────────────────── Rarity Helpers ─────────────────────── */

    private static final List<String> RARITY_ORDER = List.of(
            "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"
    );

    private boolean isRareReward(String rewardRarity, String minimumRarity) {
        int rewardIdx  = RARITY_ORDER.indexOf(rewardRarity.toUpperCase());
        int minimumIdx = RARITY_ORDER.indexOf(minimumRarity.toUpperCase());
        if (rewardIdx < 0) return false;
        return rewardIdx >= minimumIdx;
    }

    /* ─────────────────────── Example Crate ─────────────────────── */

    private void createExampleCrate() {
        // Writes a starter JSON so new users have a reference
        File example = new File(cratesDir, "example_crate.json");
        String json = """
            {
              "id": "example_crate",
              "displayName": "&b&lExample Crate",
              "hologramLines": ["&b&lEXAMPLE CRATE", "&7Klik kiri untuk preview!", "&7Klik kanan untuk buka!"],
              "requiredKeys": [
                { "keyId": "example_key", "amount": 1, "type": "VIRTUAL" }
              ],
              "rewards": [
                {
                  "id": "diamond",
                  "displayName": "&bDiamond",
                  "weight": 50.0,
                  "rarity": "COMMON",
                  "type": "VANILLA",
                  "material": "DIAMOND",
                  "amount": 1
                },
                {
                  "id": "emerald",
                  "displayName": "&aEmerald",
                  "weight": 25.0,
                  "rarity": "UNCOMMON",
                  "type": "VANILLA",
                  "material": "EMERALD",
                  "amount": 2
                },
                {
                  "id": "gold_block",
                  "displayName": "&6Gold Block",
                  "weight": 15.0,
                  "rarity": "RARE",
                  "type": "VANILLA",
                  "material": "GOLD_BLOCK",
                  "amount": 1
                },
                {
                  "id": "netherite",
                  "displayName": "&4&lNetherite Ingot",
                  "weight": 5.0,
                  "rarity": "LEGENDARY",
                  "type": "VANILLA",
                  "material": "NETHERITE_INGOT",
                  "amount": 1,
                  "broadcast": true,
                  "broadcastMessage": "&e{player} &7memenangkan &4Netherite&7 dari Example Crate!"
                },
                {
                  "id": "cmd_reward",
                  "displayName": "&d&lMythic Command",
                  "weight": 1.0,
                  "rarity": "MYTHIC",
                  "type": "COMMAND",
                  "commands": [
                    "console: give {player} minecraft:nether_star 5",
                    "console: broadcast &d{player} mendapat Mythic reward!"
                  ],
                  "broadcast": true,
                  "broadcastMessage": "&d✦ {player} mendapat reward MYTHIC dari crate!"
                }
              ],
              "preview": {
                "title": "&0&lPreview &8» &b{crate}",
                "sortOrder": "RARITY_DESC",
                "borderMaterial": null,
                "showChance": true,
                "showWeight": false,
                "showPity": true,
                "showKeyBalance": true,
                "chanceFormat": "&7Chance: &e{chance}",
                "rewardFooterLore": [],
                "prevButtonMaterial": "ARROW",
                "nextButtonMaterial": "ARROW",
                "closeButtonMaterial": "BARRIER",
                "showActualItem": true
              },
              "cooldownMs": 3600000,
              "pity": {
                "enabled": true,
                "threshold": 50,
                "rareRarityMinimum": "LEGENDARY",
                "bonusChancePerOpen": 2.0,
                "softPityStart": 40
              },
              "massOpenEnabled": true,
              "massOpenLimit": 64,
              "enabled": true
            }
            """;
        try (FileWriter w = new FileWriter(example, StandardCharsets.UTF_8)) {
            w.write(json);
        } catch (IOException e) {
            Logger.severe("Failed to create example crate file.");
        }
    }

    /* ─────────────────────── Shutdown ─────────────────────── */

    public void shutdown() {
        openingLock.clear();
    }

    /* ─────────────────────── Public Registry Access ─────────────────────── */

    public Crate getCrate(String id) { return crateRegistry.get(id); }
    public Collection<Crate> getAllCrates() { return crateRegistry.values(); }
    public Map<String, Crate> getCrateRegistry() { return Collections.unmodifiableMap(crateRegistry); }

    public void registerCrate(Crate crate) {
        crateRegistry.put(crate.getId(), crate);
        saveCrate(crate);
        // Refresh hologram + particles for newly registered crate
        if (plugin.getHologramManager() != null) plugin.getHologramManager().spawnHologram(crate);
        if (plugin.getParticleManager()  != null) plugin.getParticleManager().startIdleParticles(crate);
    }

    public void removeCrate(String id) {
        crateRegistry.remove(id);
        File file = new File(cratesDir, id + ".json");
        if (file.exists()) file.delete();
        // Tear down hologram + particles
        if (plugin.getHologramManager() != null) plugin.getHologramManager().removeHologram(id);
        if (plugin.getParticleManager()  != null) plugin.getParticleManager().stopIdleParticles(id);
    }

    /* ─────────────────────── Helpers ─────────────────────── */

    private String colorize(String s) {
        return s.replace("&", "\u00A7");
    }
}
