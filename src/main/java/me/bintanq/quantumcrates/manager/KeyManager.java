package me.bintanq.quantumcrates.manager;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.model.Crate;
import me.bintanq.quantumcrates.util.Logger;
import me.bintanq.quantumcrates.util.MessageManager;
import me.bintanq.quantumcrates.util.PhysicalKeyItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
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
                    .thenRun(() -> MessageManager.send(player, "key-given-receiver",
                            "{amount}", String.valueOf(amount), "{key}", keyId));

            plugin.getAsyncExecutor().execute(() -> {
                try {
                    int bal = plugin.getDatabaseManager()
                            .getVirtualKeys(player.getUniqueId(), keyId).get();
                    me.bintanq.quantumcrates.web.WebSocketBridge.getInstance()
                            .broadcastKeyTransaction(player.getUniqueId(), keyId, amount, bal);
                } catch (Exception ignored) {}
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
        try {
            return plugin.getDatabaseManager()
                    .getVirtualKeys(player.getUniqueId(), keyId)
                    .get(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Logger.warn("Virtual key balance interrupted: " + player.getName());
            return 0;
        } catch (java.util.concurrent.ExecutionException | TimeoutException e) {
            Logger.warn("Virtual key balance timeout/error for " + player.getName() + ": " + e.getMessage());
            return 0;
        }
    }

    private boolean removeVirtual(Player player, String keyId, int amount) {
        try {
            return plugin.getDatabaseManager()
                    .removeVirtualKeys(player.getUniqueId(), keyId, amount)
                    .get(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Logger.severe("Virtual key removal interrupted for " + player.getName());
            return false;
        } catch (java.util.concurrent.ExecutionException | TimeoutException e) {
            Logger.severe("Failed to remove virtual key for " + player.getName() + ": " + e.getMessage());
            return false;
        }
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
}