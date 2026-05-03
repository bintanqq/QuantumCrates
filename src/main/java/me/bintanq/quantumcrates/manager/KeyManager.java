package me.bintanq.quantumcrates.manager;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.model.Crate;
import me.bintanq.quantumcrates.util.Logger;
import me.bintanq.quantumcrates.util.MessageManager;
import me.bintanq.quantumcrates.util.PhysicalKeyItem;
import me.bintanq.quantumcrates.util.UUIDResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class KeyManager {

    public enum KeyMode { VIRTUAL, PHYSICAL }

    private final QuantumCrates plugin;
    private final PlayerDataManager playerDataManager;
    private KeyMode globalMode;
    private Material defaultPhysicalMaterial;
    private int defaultPhysicalCmd;
    private List<String> physicalExtraLore;
    private final VirtualKeyCache keyCache = new VirtualKeyCache();

    public KeyManager(QuantumCrates plugin, PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        reload();
    }

    public void reload() {
        String modeStr = plugin.getConfig().getString("keys.mode", "virtual").toUpperCase();
        try {
            globalMode = KeyMode.valueOf(modeStr);
        } catch (IllegalArgumentException e) {
            Logger.warn("Invalid keys.mode '" + modeStr + "', falling back to VIRTUAL.");
            globalMode = KeyMode.VIRTUAL;
        }

        defaultPhysicalMaterial = Material.matchMaterial(
                plugin.getConfig().getString("keys.physical.material", "TRIPWIRE_HOOK"));
        if (defaultPhysicalMaterial == null) defaultPhysicalMaterial = Material.TRIPWIRE_HOOK;

        defaultPhysicalCmd = plugin.getConfig().getInt("keys.physical.custom-model-data", -1);
        physicalExtraLore  = plugin.getConfig().getStringList("keys.physical.extra-lore");
        Logger.info("Key mode: &a" + globalMode.name());
    }

    public KeyMode getGlobalMode() { return globalMode; }

    public boolean giveKey(Player player, String keyId, int amount) {
        if (globalMode == KeyMode.VIRTUAL) {
            plugin.getDatabaseManager()
                    .addVirtualKeys(player.getUniqueId(), keyId, amount)
                    .thenRun(() -> {
                        // Update cache write-through
                        keyCache.add(player.getUniqueId(), keyId, amount);
                        MessageManager.send(player, "key-given-receiver",
                                "{amount}", String.valueOf(amount), "{key}", keyId);

                        int bal = keyCache.get(player.getUniqueId(), keyId);
                        me.bintanq.quantumcrates.web.WebSocketBridge.getInstance()
                                .broadcastKeyTransaction(player.getUniqueId(), keyId, amount, bal);
                    });
        } else {
            int remaining = amount;
            while (remaining > 0) {
                int stack = Math.min(remaining, 64);
                String displayName = plugin.getConfig().getString(
                                "keys.physical.display-name", "&bCrate Key &8[&7{key}&8]")
                        .replace("{key}", keyId);
                ItemStack key = PhysicalKeyItem.create(plugin, keyId, stack,
                        displayName,
                        buildPhysicalLore(keyId), defaultPhysicalMaterial, defaultPhysicalCmd);
                var overflow = player.getInventory().addItem(key);
                overflow.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
                remaining -= stack;
            }
            MessageManager.send(player, "key-given-receiver",
                    "{amount}", String.valueOf(amount), "{key}", keyId);
        }
        return true;
    }

    public CompletableFuture<Boolean> giveKeyOffline(String nameOrUuid,
                                                     String keyId, int amount) {
        return UUIDResolver.resolve(nameOrUuid, plugin.getAsyncExecutor())
                .thenComposeAsync(uuid -> {
                    if (uuid == null) return CompletableFuture.completedFuture(false);

                    return plugin.getPlayerDataManager().loadPlayer(uuid)
                            .thenComposeAsync(ignored ->
                                            plugin.getDatabaseManager().addVirtualKeys(uuid, keyId, amount)
                                                    .thenApplyAsync(v -> {
                                                        Player online = Bukkit.getPlayer(uuid);
                                                        if (online != null)
                                                            MessageManager.send(online, "key-given-receiver",
                                                                    "{amount}", String.valueOf(amount), "{key}", keyId);
                                                        return true;
                                                    }, plugin.getAsyncExecutor()),
                                    plugin.getAsyncExecutor());
                }, plugin.getAsyncExecutor());
    }

    private List<String> buildPhysicalLore(String keyId) {
        List<String> lore = new ArrayList<>();
        String idLine = plugin.getConfig().getString(
                "keys.physical.id-lore", "&7Key ID: &e{key}").replace("{key}", keyId);
        lore.add(idLine);
        lore.addAll(physicalExtraLore);
        return lore;
    }

    public boolean hasRequiredKeys(Player player, Crate crate) {
        return crate.getRequiredKeys().stream()
                .allMatch(req -> countAvailable(player, req) >= req.getAmount());
    }

    public int countPossibleOpens(Player player, Crate crate) {
        return crate.getRequiredKeys().stream()
                .mapToInt(req -> countAvailable(player, req) / Math.max(1, req.getAmount()))
                .min()
                .orElse(0);
    }

    private int countAvailable(Player player, Crate.KeyRequirement req) {
        return switch (req.getType()) {
            case VIRTUAL  -> getVirtualBalance(player, req.getKeyId());
            case PHYSICAL -> countPhysical(player, req.getKeyId());
            case MMOITEMS   -> { var h = plugin.getHookManager().getMmoItemsHook();   yield h != null ? h.countKey(player, req.getKeyId()) : 0; }
            case ITEMSADDER -> { var h = plugin.getHookManager().getItemsAdderHook(); yield h != null ? h.countKey(player, req.getKeyId()) : 0; }
            case ORAXEN     -> { var h = plugin.getHookManager().getOraxenHook();     yield h != null ? h.countKey(player, req.getKeyId()) : 0; }
        };
    }

    public boolean consumeKeys(Player player, Crate crate) {
        for (Crate.KeyRequirement req : crate.getRequiredKeys()) {
            if (countAvailable(player, req) < req.getAmount()) return false;
        }
        for (Crate.KeyRequirement req : crate.getRequiredKeys()) {
            if (!consumeKey(player, req)) {
                Logger.warn("Key consume failed mid-way: " + player.getName() + " key=" + req.getKeyId());
                return false;
            }
        }
        return true;
    }

    private boolean consumeKey(Player player, Crate.KeyRequirement req) {
        return switch (req.getType()) {
            case VIRTUAL  -> removeVirtual(player, req.getKeyId(), req.getAmount());
            case PHYSICAL -> removePhysical(player, req.getKeyId(), req.getAmount());
            case MMOITEMS   -> { var h = plugin.getHookManager().getMmoItemsHook();   yield h != null && h.removeKey(player, req.getKeyId(), req.getAmount()); }
            case ITEMSADDER -> { var h = plugin.getHookManager().getItemsAdderHook(); yield h != null && h.removeKey(player, req.getKeyId(), req.getAmount()); }
            case ORAXEN     -> { var h = plugin.getHookManager().getOraxenHook();     yield h != null && h.removeKey(player, req.getKeyId(), req.getAmount()); }
        };
    }

    public int getVirtualBalance(Player player, String keyId) {
        int cached = keyCache.get(player.getUniqueId(), keyId);
        if (cached >= 0) return cached;

        try {
            int balance = plugin.getDatabaseManager()
                    .getVirtualKeys(player.getUniqueId(), keyId)
                    .get(1, TimeUnit.SECONDS);
            keyCache.set(player.getUniqueId(), keyId, balance);
            return balance;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (ExecutionException | TimeoutException e) {
            Logger.warn("Virtual key balance timeout for " + player.getName() + ": " + e.getMessage());
            return 0;
        }
    }

    private boolean removeVirtual(Player player, String keyId, int amount) {
        if (keyCache.isCached(player.getUniqueId(), keyId)) {
            boolean subtracted = keyCache.subtract(player.getUniqueId(), keyId, amount);
            if (!subtracted) return false;

            plugin.getDatabaseManager()
                    .removeVirtualKeys(player.getUniqueId(), keyId, amount)
                    .thenAccept(dbSuccess -> {
                        if (!dbSuccess) {
                            // DB gagal (race condition) — rollback cache
                            Logger.warn("DB remove failed post-cache-subtract for "
                                    + player.getName() + " key=" + keyId + " amount=" + amount
                                    + ". Rolling back cache.");
                            keyCache.add(player.getUniqueId(), keyId, amount);
                        }
                    });
            return true;
        }
        try {
            boolean success = plugin.getDatabaseManager()
                    .removeVirtualKeys(player.getUniqueId(), keyId, amount)
                    .get(2, TimeUnit.SECONDS);
            if (success) {
                keyCache.add(player.getUniqueId(), keyId, -amount);
            }
            return success;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException e) {
            Logger.severe("Failed to remove virtual key for " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    public void preloadKeys(UUID uuid) {
        Set<String> knownIds = new HashSet<>(getKnownKeyIds());
        if (knownIds.isEmpty()) return;

        for (String keyId : knownIds) {
            plugin.getDatabaseManager()
                    .getVirtualKeys(uuid, keyId)
                    .thenAccept(balance -> keyCache.set(uuid, keyId, balance));
        }
    }

    public void invalidateCache(UUID uuid) {
        keyCache.invalidateAll(uuid);
    }

    private int countPhysical(Player player, String keyId) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (PhysicalKeyItem.isKey(plugin, item, keyId)) count += item.getAmount();
        }
        return count;
    }

    private boolean removePhysical(Player player, String keyId, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (!PhysicalKeyItem.isKey(plugin, item, keyId)) continue;
            if (item.getAmount() <= remaining) {
                remaining -= item.getAmount();
                player.getInventory().setItem(i, null);
            } else {
                item.setAmount(item.getAmount() - remaining);
                remaining = 0;
            }
        }
        return remaining <= 0;
    }

    public Collection<String> getKnownKeyIds() {
        Set<String> ids = new LinkedHashSet<>();
        plugin.getCrateManager().getAllCrates().forEach(c -> {
            if (c.getRequiredKeys() != null) {
                c.getRequiredKeys().forEach(r -> {
                    if (r != null && r.getKeyId() != null) {
                        ids.add(r.getKeyId());
                    }
                });
            }
        });
        return ids;
    }

    /**
     * Consume keys for ALL mass opens at once — 1 DB query total.
     * @param count total number of opens (keys multiplied by count)
     */
    public boolean consumeKeysBatch(Player player, Crate crate, int count) {
        for (Crate.KeyRequirement req : crate.getRequiredKeys()) {
            int totalNeeded = req.getAmount() * count;
            if (countAvailable(player, req) < totalNeeded) return false;
        }
        for (Crate.KeyRequirement req : crate.getRequiredKeys()) {
            int totalNeeded = req.getAmount() * count;
            if (!consumeKeyAmount(player, req, totalNeeded)) {
                Logger.warn("Batch key consume failed mid-way: " + player.getName());
                return false;
            }
        }
        return true;
    }

    private boolean consumeKeyAmount(Player player, Crate.KeyRequirement req, int totalAmount) {
        return switch (req.getType()) {
            case VIRTUAL  -> removeVirtual(player, req.getKeyId(), totalAmount);
            case PHYSICAL -> removePhysical(player, req.getKeyId(), totalAmount);
            case MMOITEMS   -> { var h = plugin.getHookManager().getMmoItemsHook();   yield h != null && h.removeKey(player, req.getKeyId(), totalAmount); }
            case ITEMSADDER -> { var h = plugin.getHookManager().getItemsAdderHook(); yield h != null && h.removeKey(player, req.getKeyId(), totalAmount); }
            case ORAXEN     -> { var h = plugin.getHookManager().getOraxenHook();     yield h != null && h.removeKey(player, req.getKeyId(), totalAmount); }
        };
    }

    VirtualKeyCache getKeyCache() { return keyCache; }
}