package me.bintanq.quantumcrates.manager;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.log.LogManager;
import me.bintanq.quantumcrates.model.Crate;
import me.bintanq.quantumcrates.model.PlayerData;
import me.bintanq.quantumcrates.model.reward.RewardResult;
import me.bintanq.quantumcrates.serializer.GsonProvider;
import me.bintanq.quantumcrates.util.Logger;
import me.bintanq.quantumcrates.util.MessageManager;
import me.bintanq.quantumcrates.util.TimeUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CrateManager {

    private final QuantumCrates plugin;
    private final PlayerDataManager playerDataManager;
    private final me.bintanq.quantumcrates.processor.RewardProcessor rewardProcessor;
    private final LogManager logManager;
    private final KeyManager keyManager;

    private final Object saveLock = new Object();

    private final ConcurrentHashMap<String, Crate> crateRegistry = new ConcurrentHashMap<>();
    private final Set<UUID> openingLock = ConcurrentHashMap.newKeySet();
    private File cratesDir;

    public CrateManager(QuantumCrates plugin, PlayerDataManager playerDataManager,
                        me.bintanq.quantumcrates.processor.RewardProcessor rewardProcessor,
                        LogManager logManager, KeyManager keyManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.rewardProcessor = rewardProcessor;
        this.logManager = logManager;
        this.keyManager = keyManager;
    }

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
                if (crate.getId() == null || crate.getId().isEmpty())
                    crate.setId(file.getName().replace(".json", ""));
                crateRegistry.put(crate.getId(), crate);
                loaded++;
                Logger.debug("Loaded crate: &e" + crate.getId());
            } catch (IOException e) {
                Logger.severe("Failed to load crate file '" + file.getName() + "': " + e.getMessage());
            }
        }
        Logger.info("Loaded &e" + loaded + " &fcrates.");
    }

    public void saveCrate(Crate crate) {
        synchronized (saveLock) {
            File file = new File(cratesDir, crate.getId() + ".json");
            try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                GsonProvider.getGson().toJson(crate, writer);
            } catch (IOException e) {
                Logger.severe("Failed to save crate '" + crate.getId() + "': " + e.getMessage());
                return;
            }
            crateRegistry.put(crate.getId(), crate);
        }
    }

    public enum OpenResult {
        SUCCESS, NOT_FOUND, DISABLED, NOT_SCHEDULED,
        ON_COOLDOWN, MISSING_KEY, ALREADY_OPENING
    }

    public OpenResult canOpen(Player player, String crateId) {
        if (plugin.getAnimationManager().hasSession(player.getUniqueId()))
            return OpenResult.ALREADY_OPENING;
        Crate crate = crateRegistry.get(crateId);
        if (crate == null) return OpenResult.NOT_FOUND;
        if (!crate.isEnabled()) return OpenResult.DISABLED;
        if (openingLock.contains(player.getUniqueId())) return OpenResult.ALREADY_OPENING;
        if (!crate.isCurrentlyOpenable()) return OpenResult.NOT_SCHEDULED;

        PlayerData data = playerDataManager.getOrEmpty(player.getUniqueId());
        if (crate.getCooldownMs() > 0 && data.isOnCooldown(crateId, crate.getCooldownMs()))
            return OpenResult.ON_COOLDOWN;

        if (!keyManager.hasRequiredKeys(player, crate)) return OpenResult.MISSING_KEY;
        return OpenResult.SUCCESS;
    }

    public boolean openCrate(Player player, String crateId) {
        OpenResult check = canOpen(player, crateId);
        if (check != OpenResult.SUCCESS) {
            sendOpenResultFeedback(player, check, crateId);
            return false;
        }
        return executeOpen(player, crateId, false);
    }

    public void massOpen(Player player, String crateId, int count) {
        Crate crate = crateRegistry.get(crateId);
        if (crate == null || !crate.isMassOpenEnabled()) {
            MessageManager.send(player, "mass-open-disabled", "{crate}", crateId);
            return;
        }

        if (crate.getCooldownMs() > 0) {
            PlayerData data = playerDataManager.getOrEmpty(player.getUniqueId());
            if (data.isOnCooldown(crateId, crate.getCooldownMs())) {
                sendOpenResultFeedback(player, OpenResult.ON_COOLDOWN, crateId);
                return;
            }
        }

        int maxAllowed = crate.getMassOpenLimit();
        int canPerform = keyManager.countPossibleOpens(player, crate);
        int actual = (count <= 0) ? canPerform : Math.min(count, canPerform);
        if (maxAllowed > 0) actual = Math.min(actual, maxAllowed);

        if (actual <= 0) {
            sendOpenResultFeedback(player, OpenResult.MISSING_KEY, crateId);
            return;
        }

        AtomicInteger remaining = new AtomicInteger(actual);
        AtomicInteger successCount = new AtomicInteger(0);

        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (remaining.get() <= 0 || !player.isOnline()) {
                    if (successCount.get() > 0)
                        MessageManager.send(player, "mass-open-success", "{count}", String.valueOf(successCount.get()));
                    cancel();
                    return;
                }

                int batch = Math.min(10, remaining.get());
                for (int i = 0; i < batch; i++) {
                    if (executeOpen(player, crateId, true)) {
                        successCount.incrementAndGet();
                    } else {
                        remaining.set(0);
                        break;
                    }
                    remaining.decrementAndGet();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private boolean executeOpen(Player player, String crateId, boolean skipCooldownCheck) {
        Crate crate = crateRegistry.get(crateId);
        if (crate == null || !crate.isEnabled()) return false;
        if (openingLock.contains(player.getUniqueId())) return false;
        if (!crate.isCurrentlyOpenable()) return false;
        if (!keyManager.hasRequiredKeys(player, crate)) return false;

        PlayerData data = playerDataManager.getOrEmpty(player.getUniqueId());
        openingLock.add(player.getUniqueId());

        try {
            if (!keyManager.consumeKeys(player, crate)) return false;

            RewardResult result = rewardProcessor.roll(crate, data);

            boolean isRare = plugin.getRarityManager()
                    .isAtOrAbove(result.getReward().getRarity(), crate.getPity().getRareRarityMinimum());

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

            if (crate.getCooldownMs() > 0)
                playerDataManager.setLastOpen(player.getUniqueId(), crateId);

            plugin.getAnimationManager().startAnimation(player, crate, result);
            deliverReward(player, result);

            if (plugin.getParticleManager() != null)
                plugin.getParticleManager().playOpenEffect(crate, player.getLocation());

            if (result.getReward().isBroadcast()) {
                plugin.getServer().broadcastMessage(result.getReward().getBroadcastMessage()
                        .replace("{player}", player.getName())
                        .replace("{reward}", result.getReward().getDisplayName())
                        .replace("&", "\u00A7"));
            }

            org.bukkit.Location loc = player.getLocation();
            me.bintanq.quantumcrates.log.CrateLog crateLog = new me.bintanq.quantumcrates.log.CrateLog(
                    player.getUniqueId(), player.getName(), crateId,
                    result.getReward().getId(), result.getReward().getDisplayName(),
                    result.getPityAtRoll(), System.currentTimeMillis(),
                    loc.getWorld() != null ? loc.getWorld().getName() : "unknown",
                    loc.getX(), loc.getY(), loc.getZ());

            logManager.log(crateLog);
            me.bintanq.quantumcrates.web.WebSocketBridge.getInstance().broadcastCrateOpen(crateLog);

            if (plugin.getStatsScheduler() != null)
                plugin.getStatsScheduler().incrementOpenings();

            return true;
        } finally {
            openingLock.remove(player.getUniqueId());
        }
    }

    private void deliverReward(Player player, RewardResult result) {
        if (result.hasItem()) {
            ItemStack item = result.getItemStack();
            if (player.getInventory().firstEmpty() == -1) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
                MessageManager.send(player, "inventory-full", "{reward}", result.getReward().getDisplayName());
            } else {
                player.getInventory().addItem(item);
            }
        }
        if (result.hasCommands()) rewardProcessor.executeCommands(player, result);
        MessageManager.send(player, "reward-received", "{reward}", result.getReward().getDisplayName());
    }

    public void deliverRewardPublic(Player player, RewardResult result) {
        deliverReward(player, result);
    }

    private void sendOpenResultFeedback(Player player, OpenResult result, String crateId) {
        switch (result) {
            case NOT_FOUND   -> MessageManager.send(player, "crate-not-found", "{crate}", crateId);
            case DISABLED    -> MessageManager.send(player, "crate-disabled", "{crate}", crateId);
            case NOT_SCHEDULED -> {
                Crate crate = crateRegistry.get(crateId);
                String sched = crate != null && crate.getSchedule() != null
                        ? crate.getSchedule().getNextOpenDescription() : "Unknown";
                MessageManager.send(player, "crate-not-open", "{crate}", crateId, "{schedule}", sched);
            }
            case ON_COOLDOWN -> {
                Crate crate = crateRegistry.get(crateId);
                long rem = crate != null
                        ? playerDataManager.getRemainingCooldown(player.getUniqueId(), crateId, crate.getCooldownMs()) : 0;
                MessageManager.send(player, "cooldown-active", "{time}", TimeUtil.formatDuration(rem));
            }
            case MISSING_KEY -> {
                Crate crate = crateRegistry.get(crateId);
                String missingKeyId = (crate != null)
                        ? crate.getRequiredKeys().stream()
                        .filter(req -> keyManager.countPossibleOpens(player, crate) == 0)
                        .map(Crate.KeyRequirement::getKeyId)
                        .findFirst()
                        .orElse(crateId)
                        : crateId;
                MessageManager.send(player, "key-not-found", "{key}", missingKeyId);
            }
            case ALREADY_OPENING -> MessageManager.send(player, "already-opening");
            default              -> MessageManager.send(player, "crate-not-found", "{crate}", crateId);
        }
    }

    private void createExampleCrate() {
        File example = new File(cratesDir, "example_crate.json");
        RarityManager rm = plugin.getRarityManager();
        List<me.bintanq.quantumcrates.model.RarityDefinition> rarities = rm.getAll();

        String lowestRarity  = rarities.isEmpty() ? "COMMON"  : rarities.get(0).getId();
        String midRarity     = rarities.size() > 2 ? rarities.get(2).getId() : rarities.get(rarities.size() / 2).getId();
        String highestRarity = rarities.isEmpty() ? "MYTHIC"  : rarities.get(rarities.size() - 1).getId();
        String pityMinRarity = rarities.size() >= 5 ? rarities.get(rarities.size() - 2).getId() : highestRarity;

        String json = """
            {
              "id": "example_crate",
              "displayName": "&b&lExample Crate",
              "hologramLines": ["&b&lEXAMPLE CRATE", "&7Left-click to preview!", "&7Right-click to open!"],
              "requiredKeys": [
                { "keyId": "example_key", "amount": 1, "type": "VIRTUAL" },
              ],
              "rewards": [
                {
                  "id": "diamond",
                  "displayName": "&bDiamond",
                  "weight": 50.0,
                  "rarity": "%s",
                  "type": "VANILLA",
                  "material": "DIAMOND",
                  "amount": 1
                },
                {
                  "id": "emerald",
                  "displayName": "&aEmerald",
                  "weight": 25.0,
                  "rarity": "%s",
                  "type": "VANILLA",
                  "material": "EMERALD",
                  "amount": 2
                },
                {
                  "id": "netherite",
                  "displayName": "&4&lNetherite Ingot",
                  "weight": 5.0,
                  "rarity": "%s",
                  "type": "VANILLA",
                  "material": "NETHERITE_INGOT",
                  "amount": 1,
                  "broadcast": true,
                  "broadcastMessage": "&e{player} &7won &4Netherite&7 from Example Crate!"
                },
                {
                  "id": "cmd_reward",
                  "displayName": "&d&lMythic Command",
                  "weight": 1.0,
                  "rarity": "%s",
                  "type": "COMMAND",
                  "commands": ["console: give {player} minecraft:nether_star 5"],
                  "broadcast": true,
                  "broadcastMessage": "&d✦ {player} got a top-tier reward!"
                }
              ],
              "preview": {
                "sortOrder": "RARITY_DESC",
                "showChance": true,
                "showPity": true,
                "showKeyBalance": true,
                "showActualItem": true
              },
              "cooldownMs": 3600000,
              "pity": {
                "enabled": true,
                "threshold": 50,
                "rareRarityMinimum": "%s",
                "bonusChancePerOpen": 2.0,
                "softPityStart": 40
              },
              "massOpenEnabled": true,
              "massOpenLimit": 64,
              "enabled": true,
              "guiAnimation": "ROULETTE"
            }
            """.formatted(lowestRarity, midRarity, pityMinRarity, highestRarity, pityMinRarity);

        try (FileWriter w = new FileWriter(example, StandardCharsets.UTF_8)) {
            w.write(json);
        } catch (IOException e) {
            Logger.severe("Failed to create example crate file.");
        }
    }

    public void shutdown() { openingLock.clear(); }

    public Crate getCrate(String id) { return crateRegistry.get(id); }
    public Collection<Crate> getAllCrates() { return crateRegistry.values(); }
    public Map<String, Crate> getCrateRegistry() { return Collections.unmodifiableMap(crateRegistry); }

    public void registerCrate(Crate crate) {
        saveCrate(crate);
        if (plugin.getHologramManager() != null) plugin.getHologramManager().spawnHologram(crate);
        if (plugin.getParticleManager()  != null) plugin.getParticleManager().startIdleParticles(crate);
    }

    public void removeCrate(String id) {
        synchronized (saveLock) {
            crateRegistry.remove(id);
            File file = new File(cratesDir, id + ".json");
            if (file.exists()) file.delete();
        }
        if (plugin.getHologramManager() != null) plugin.getHologramManager().removeHologram(id);
        if (plugin.getParticleManager()  != null) plugin.getParticleManager().stopIdleParticles(id);
    }
}