package me.bintanq.quantumcrates.manager;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.log.LogManager;
import me.bintanq.quantumcrates.model.Crate;
import me.bintanq.quantumcrates.model.PlayerData;
import me.bintanq.quantumcrates.model.SaveReport;
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
    private final ConcurrentHashMap<String, String> locationIndex = new ConcurrentHashMap<>();
    private final Set<UUID> openingLock = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>> rateLimitTracker
            = new ConcurrentHashMap<>();
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

    private String locationKey(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
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
                crate.migrateLegacyLocation();
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
        locationIndex.clear();
        crateRegistry.values().forEach(c -> {
            if (c.getLocations() != null) {
                c.getLocations().forEach(loc ->
                        locationIndex.put(locationKey(loc.world, (int)loc.x, (int)loc.y, (int)loc.z), c.getId()));
            }
        });
    }

    public void saveCrate(Crate crate) {
        synchronized (saveLock) {
            locationIndex.entrySet().removeIf(e -> e.getValue().equals(crate.getId()));

            File file = new File(cratesDir, crate.getId() + ".json");
            try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                GsonProvider.getGson().toJson(crate, writer);
            } catch (IOException e) {
                Logger.severe("Failed to save crate '" + crate.getId() + "': " + e.getMessage());
                return;
            }
            crateRegistry.put(crate.getId(), crate);

            if (crate.getLocations() != null) {
                for (Crate.SerializableLocation loc : crate.getLocations()) {
                    locationIndex.put(locationKey(loc.world, (int)loc.x, (int)loc.y, (int)loc.z), crate.getId());
                }
            }
        }
    }

    public SaveReport.Entry diffCrate(Crate before, Crate after) {
        if (before == null)
            return new SaveReport.Entry("CRATE", SaveReport.ChangeType.ADDED,
                    "Created crate '" + after.getId() + "'" +
                            " displayName='" + after.getDisplayName() + "'");

        List<String> changes = new ArrayList<>();

        // Basic fields
        if (!java.util.Objects.equals(before.getDisplayName(), after.getDisplayName()))
            changes.add("displayName '" + before.getDisplayName() + "' → '" + after.getDisplayName() + "'");
        if (before.isEnabled() != after.isEnabled())
            changes.add("enabled " + before.isEnabled() + " → " + after.isEnabled());
        if (before.getCooldownMs() != after.getCooldownMs())
            changes.add("cooldown " + before.getCooldownMs() + "ms → " + after.getCooldownMs() + "ms");
        if (before.isMassOpenEnabled() != after.isMassOpenEnabled())
            changes.add("massOpen " + before.isMassOpenEnabled() + " → " + after.isMassOpenEnabled());
        if (before.getMassOpenLimit() != after.getMassOpenLimit())
            changes.add("massOpenLimit " + before.getMassOpenLimit() + " → " + after.getMassOpenLimit());
        if (before.getOpenRateLimit() != after.getOpenRateLimit())
            changes.add("openRateLimit " + before.getOpenRateLimit() + " → " + after.getOpenRateLimit());
        if (before.getLifetimeOpenLimit() != after.getLifetimeOpenLimit())
            changes.add("lifetimeOpenLimit " + before.getLifetimeOpenLimit() + " → " + after.getLifetimeOpenLimit());
        if (before.isAccessDeniedKnockback() != after.isAccessDeniedKnockback())
            changes.add("knockback " + before.isAccessDeniedKnockback() + " → " + after.isAccessDeniedKnockback());
        if (Double.compare(before.getKnockbackStrength(), after.getKnockbackStrength()) != 0)
            changes.add("knockbackStrength " + before.getKnockbackStrength() + " → " + after.getKnockbackStrength());

        // Animations
        if (before.getGuiAnimation() != after.getGuiAnimation())
            changes.add("guiAnimation " + before.getGuiAnimation() + " → " + after.getGuiAnimation());
        if (!before.getIdleAnimation().getType().equals(after.getIdleAnimation().getType()))
            changes.add("idleAnimation " + before.getIdleAnimation().getType() + " → " + after.getIdleAnimation().getType());
        if (!before.getIdleAnimation().getParticle().equals(after.getIdleAnimation().getParticle()))
            changes.add("idleParticle " + before.getIdleAnimation().getParticle() + " → " + after.getIdleAnimation().getParticle());
        if (!before.getOpenAnimation().getType().equals(after.getOpenAnimation().getType()))
            changes.add("openAnimation " + before.getOpenAnimation().getType() + " → " + after.getOpenAnimation().getType());
        if (!before.getOpenAnimation().getParticle().equals(after.getOpenAnimation().getParticle()))
            changes.add("openParticle " + before.getOpenAnimation().getParticle() + " → " + after.getOpenAnimation().getParticle());

        // Hologram
        if (Double.compare(before.getHologramHeight(), after.getHologramHeight()) != 0)
            changes.add("hologramHeight " + before.getHologramHeight() + " → " + after.getHologramHeight());
        if (!before.getHologramLines().equals(after.getHologramLines()))
            changes.add("hologramLines (" + before.getHologramLines().size() + " → " + after.getHologramLines().size() + " lines)");

        // Pity
        Crate.PityConfig bp = before.getPity(), ap = after.getPity();
        if (bp.isEnabled() != ap.isEnabled())
            changes.add("pity " + bp.isEnabled() + " → " + ap.isEnabled());
        else if (ap.isEnabled()) {
            if (bp.getThreshold() != ap.getThreshold())
                changes.add("pity.threshold " + bp.getThreshold() + " → " + ap.getThreshold());
            if (bp.getSoftPityStart() != ap.getSoftPityStart())
                changes.add("pity.softStart " + bp.getSoftPityStart() + " → " + ap.getSoftPityStart());
            if (!java.util.Objects.equals(bp.getRareRarityMinimum(), ap.getRareRarityMinimum()))
                changes.add("pity.minRarity " + bp.getRareRarityMinimum() + " → " + ap.getRareRarityMinimum());
            if (Double.compare(bp.getBonusChancePerOpen(), ap.getBonusChancePerOpen()) != 0)
                changes.add("pity.bonusChance " + bp.getBonusChancePerOpen() + " → " + ap.getBonusChancePerOpen());
        }

        // Schedule
        boolean beforeHasSched = before.getSchedule() != null;
        boolean afterHasSched  = after.getSchedule() != null;
        if (beforeHasSched != afterHasSched) {
            changes.add(afterHasSched ? "schedule added (" + after.getSchedule().getMode() + ")"
                    : "schedule removed");
        } else if (beforeHasSched && !before.getSchedule().getMode().equals(after.getSchedule().getMode())) {
            changes.add("schedule " + before.getSchedule().getMode() + " → " + after.getSchedule().getMode());
        }

        // Required keys
        Set<String> beforeKeys = before.getRequiredKeys().stream()
                .map(Crate.KeyRequirement::getKeyId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> afterKeys = after.getRequiredKeys().stream()
                .map(Crate.KeyRequirement::getKeyId)
                .collect(java.util.stream.Collectors.toSet());
        afterKeys.stream().filter(k -> !beforeKeys.contains(k))
                .forEach(k -> changes.add("added key '" + k + "'"));
        beforeKeys.stream().filter(k -> !afterKeys.contains(k))
                .forEach(k -> changes.add("removed key '" + k + "'"));

        // Rewards — added/removed
        Set<String> beforeIds = before.getRewards().stream()
                .map(me.bintanq.quantumcrates.model.reward.Reward::getId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> afterIds = after.getRewards().stream()
                .map(me.bintanq.quantumcrates.model.reward.Reward::getId)
                .collect(java.util.stream.Collectors.toSet());
        afterIds.stream().filter(id -> !beforeIds.contains(id))
                .forEach(id -> changes.add("added reward '" + id + "'"));
        beforeIds.stream().filter(id -> !afterIds.contains(id))
                .forEach(id -> changes.add("removed reward '" + id + "'"));
        // Weight changes
        after.getRewards().forEach(ar -> before.getRewards().stream()
                .filter(br -> br.getId().equals(ar.getId()))
                .findFirst().ifPresent(br -> {
                    if (Double.compare(br.getWeight(), ar.getWeight()) != 0)
                        changes.add("reward '" + ar.getId() + "' weight " + br.getWeight() + " → " + ar.getWeight());
                }));

        // Locations
        int beforeLocs = before.getLocations().size();
        int afterLocs  = after.getLocations().size();
        if (beforeLocs != afterLocs)
            changes.add("locations " + beforeLocs + " → " + afterLocs);

        // Build detail string — compact jika banyak
        String detail;
        if (changes.isEmpty()) {
            detail = "no tracked fields changed";
        } else if (changes.size() <= 3) {
            detail = String.join("; ", changes);
        } else {
            // Ambil 3 pertama, sisanya ringkas
            detail = String.join("; ", changes.subList(0, 3))
                    + " (+" + (changes.size() - 3) + " more)";
        }

        return new SaveReport.Entry("CRATE", SaveReport.ChangeType.MODIFIED,
                "Modified crate '" + after.getId() + "': " + detail);
    }

    public enum OpenResult {
        SUCCESS, NOT_FOUND, DISABLED, NOT_SCHEDULED,
        ON_COOLDOWN, MISSING_KEY, ALREADY_OPENING,
        RATE_LIMITED, LIFETIME_LIMIT_REACHED
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
        if (crate.getCooldownMs() > 0
                && data.isOnCooldown(crateId, crate.getCooldownMs())
                && !player.hasPermission("quantumcrates.bypasscooldown"))
            return OpenResult.ON_COOLDOWN;

        if (!keyManager.hasRequiredKeys(player, crate)) return OpenResult.MISSING_KEY;
        if (crate.getOpenRateLimit() > 0) {
            long minInterval = 1000L / crate.getOpenRateLimit();
            ConcurrentHashMap<String, Long> playerMap = rateLimitTracker
                    .computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
            Long last = playerMap.get(crateId);
            if (last != null && System.currentTimeMillis() - last < minInterval) {
                return OpenResult.RATE_LIMITED;
            }
        }

        if (crate.getLifetimeOpenLimit() > 0
                && !player.hasPermission("quantumcrates.bypasslimit")) {
            int lifetimeUsed = playerDataManager.getLifetimeOpens(player.getUniqueId(), crateId);
            if (lifetimeUsed >= crate.getLifetimeOpenLimit())
                return OpenResult.LIFETIME_LIMIT_REACHED;
        }
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

        if (crate.getLifetimeOpenLimit() > 0
                && !player.hasPermission("quantumcrates.bypasslimit")) {
            int used = playerDataManager.getLifetimeOpens(player.getUniqueId(), crateId);
            int remaining = crate.getLifetimeOpenLimit() - used;
            if (remaining <= 0) {
                sendOpenResultFeedback(player, OpenResult.LIFETIME_LIMIT_REACHED, crateId);
                return;
            }
            canPerform = Math.min(canPerform, remaining);
        }

        int actual = (count <= 0) ? canPerform : Math.min(count, canPerform);
        if (maxAllowed > 0) actual = Math.min(actual, maxAllowed);

        if (actual <= 0) {
            sendOpenResultFeedback(player, OpenResult.MISSING_KEY, crateId);
            return;
        }

        if (openingLock.contains(player.getUniqueId())) {
            sendOpenResultFeedback(player, OpenResult.ALREADY_OPENING, crateId);
            return;
        }

        openingLock.add(player.getUniqueId());

        boolean consumed = keyManager.consumeKeysBatch(player, crate, actual);
        if (!consumed) {
            openingLock.remove(player.getUniqueId());
            sendOpenResultFeedback(player, OpenResult.MISSING_KEY, crateId);
            return;
        }

        final int totalToOpen = actual;
        final UUID capturedUuid = player.getUniqueId();
        final java.lang.ref.WeakReference<Player> playerRef = new java.lang.ref.WeakReference<>(player);
        final java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(totalToOpen);
        final java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final String capturedCrateId = crateId;

        // Respect rate limit: calculate max opens per tick (1 tick = 50ms)
        final int rateLimit = crate.getOpenRateLimit();
        // If rateLimit is e.g. 5/sec → 5 per 20 ticks → 1 per 4 ticks minimum
        // For simplicity: allow at most max(1, rateLimit) per second, spread across ticks
        // Each tick processes min(batchPerTick, remaining)
        final int batchPerTick = (rateLimit > 0) ? Math.max(1, rateLimit / 20) : 10;

        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                Player p = playerRef.get();

                if (p == null || !p.isOnline()) {
                    // REFUND remaining unconsumed keys
                    int leftover = remaining.get();
                    if (leftover > 0) {
                        Crate c = crateRegistry.get(capturedCrateId);
                        if (c != null) {
                            for (Crate.KeyRequirement req : c.getRequiredKeys()) {
                                if (req.getType() == Crate.KeyType.VIRTUAL) {
                                    plugin.getDatabaseManager().addVirtualKeys(capturedUuid, req.getKeyId(), req.getAmount() * leftover);
                                }
                            }
                        }
                        Logger.info("Refunded " + leftover + " mass-open keys to offline player " + capturedUuid);
                    }
                    openingLock.remove(capturedUuid);
                    cancel();
                    return;
                }

                if (remaining.get() <= 0) {
                    openingLock.remove(p.getUniqueId());
                    int success = successCount.get();
                    if (success > 0) {
                        MessageManager.send(p, "mass-open-success", "{count}", String.valueOf(success));
                    }
                    cancel();
                    return;
                }

                int batch = Math.min(batchPerTick, remaining.get());
                for (int i = 0; i < batch; i++) {
                    if (executeOpenNoKeyConsume(p, capturedCrateId)) {
                        successCount.incrementAndGet();
                    } else {
                        // Refund remaining keys that won't be used
                        int leftover = remaining.get() - 1; // -1 because this one failed
                        if (leftover > 0) {
                            Crate c = crateRegistry.get(capturedCrateId);
                            if (c != null) {
                                for (Crate.KeyRequirement req : c.getRequiredKeys()) {
                                    if (req.getType() == Crate.KeyType.VIRTUAL) {
                                        plugin.getDatabaseManager().addVirtualKeys(p.getUniqueId(), req.getKeyId(), req.getAmount() * leftover);
                                    }
                                }
                            }
                        }
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

            if (crate.getLifetimeOpenLimit() > 0
                    && !player.hasPermission("quantumcrates.bypasslimit")) {
                int lifetimeUsed = playerDataManager.getLifetimeOpens(player.getUniqueId(), crateId);
                if (lifetimeUsed >= crate.getLifetimeOpenLimit()) {
                    sendOpenResultFeedback(player, OpenResult.LIFETIME_LIMIT_REACHED, crateId);
                    return false;
                }
            }

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

            if (crate.getOpenRateLimit() > 0) {
                rateLimitTracker
                        .computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                        .put(crateId, System.currentTimeMillis());
            }
            playerDataManager.incrementLifetimeOpens(player.getUniqueId(), crateId);

            plugin.getAnimationManager().startAnimation(player, crate, result);

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

    /**
     * Internal — for mass open only. Keys already consumed in batch.
     * Skips key check and consume step.
     */
    private boolean executeOpenNoKeyConsume(Player player, String crateId) {
        Crate crate = crateRegistry.get(crateId);
        if (crate == null || !crate.isEnabled()) return false;
        if (!crate.isCurrentlyOpenable()) return false;

        if (crate.getLifetimeOpenLimit() > 0
                && !player.hasPermission("quantumcrates.bypasslimit")) {
            int lifetimeUsed = playerDataManager.getLifetimeOpens(player.getUniqueId(), crateId);
            if (lifetimeUsed >= crate.getLifetimeOpenLimit()) return false;
        }

        PlayerData data = playerDataManager.getOrEmpty(player.getUniqueId());

        try {
            RewardResult result = rewardProcessor.roll(crate, data);

            boolean isRare = plugin.getRarityManager()
                    .isAtOrAbove(result.getReward().getRarity(), crate.getPity().getRareRarityMinimum());

            if (isRare || result.isPityGuaranteed()) {
                playerDataManager.resetPity(player.getUniqueId(), crateId);
            } else {
                playerDataManager.incrementPity(player.getUniqueId(), crateId);
            }

            if (crate.getCooldownMs() > 0)
                playerDataManager.setLastOpen(player.getUniqueId(), crateId);
            playerDataManager.incrementLifetimeOpens(player.getUniqueId(), crateId);

            // Mass open: deliver langsung tanpa animasi GUI
            deliverRewardPublic(player, result);

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
        } catch (Exception e) {
            Logger.severe("executeOpenNoKeyConsume error: " + e.getMessage());
            return false;
        }
    }

    public void deliverRewardPublic(Player player, RewardResult result) {
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
            case RATE_LIMITED        -> MessageManager.send(player, "rate-limited");
            case LIFETIME_LIMIT_REACHED -> {
                Crate crate = crateRegistry.get(crateId);
                MessageManager.send(player, "lifetime-limit-reached",
                        "{limit}", String.valueOf(crate != null ? crate.getLifetimeOpenLimit() : 0));
            }
            default              -> MessageManager.send(player, "crate-not-found", "{crate}", crateId);
        }
    }

    public void sendOpenResultFeedbackPublic(Player player, OpenResult result, String crateId) {
        sendOpenResultFeedback(player, result, crateId);
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
          "hologramHeight": 1.2,
          "requiredKeys": [
            { "keyId": "example_key", "amount": 1, "type": "VIRTUAL" }
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
          "openRateLimit": 0,
          "lifetimeOpenLimit": 0,
          "enabled": true,
          "guiAnimation": "ROULETTE",
          "guiAnimationSpeed": 1.0,
          "particleAnimationSpeed": 1.0,
          "openSound": "BLOCK_NOTE_BLOCK_HAT",
          "winSound": "UI_TOAST_CHALLENGE_COMPLETE"
        }
        """.formatted(lowestRarity, midRarity, pityMinRarity, highestRarity, pityMinRarity);

        try (FileWriter w = new FileWriter(example, StandardCharsets.UTF_8)) {
            w.write(json);
        } catch (IOException e) {
            Logger.severe("Failed to create example crate file.");
        }
    }

    public Crate getCrateAtLocation(String world, int x, int y, int z) {
        String crateId = locationIndex.get(locationKey(world, x, y, z));
        return crateId != null ? crateRegistry.get(crateId) : null;
    }

    public void shutdown() {
        openingLock.clear();
        rateLimitTracker.clear();
    }

    public void cleanupPlayer(UUID uuid) {
        rateLimitTracker.remove(uuid);
    }

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
            locationIndex.entrySet().removeIf(e -> e.getValue().equals(id));
            crateRegistry.remove(id);
            File file = new File(cratesDir, id + ".json");
            if (file.exists()) file.delete();
        }
        if (plugin.getHologramManager() != null) plugin.getHologramManager().removeHologram(id);
        if (plugin.getParticleManager()  != null) plugin.getParticleManager().stopIdleParticles(id);
    }
}