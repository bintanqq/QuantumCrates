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

/**
 * KeyManager — validasi, konsumsi, dan pemberian key.
 *
 * Mode ditentukan GLOBAL di config.yml → keys.mode:
 *   virtual  = balance di DB (tidak ada item fisik)
 *   physical = item fisik di inventory dengan PDC tag
 *
 * Seluruh server pakai satu mode. Tidak ada campuran virtual+physical
 * dalam satu server untuk menghindari kebingungan player.
 */
public class KeyManager {

    public enum KeyMode { VIRTUAL, PHYSICAL }

    private final QuantumCrates plugin;
    private final PlayerDataManager playerDataManager;
    private KeyMode globalMode;

    // Physical key config (hanya relevan jika mode = PHYSICAL)
    private Material defaultPhysicalMaterial;
    private int defaultPhysicalCmd;
    private List<String> physicalExtraLore;

    public KeyManager(QuantumCrates plugin, PlayerDataManager playerDataManager) {
        this.plugin            = plugin;
        this.playerDataManager = playerDataManager;
        reload();
    }

    /** Reload config — dipanggil saat /qc reload */
    public void reload() {
        String modeStr = plugin.getConfig().getString("keys.mode", "virtual").toUpperCase();
        try {
            globalMode = KeyMode.valueOf(modeStr);
        } catch (IllegalArgumentException e) {
            Logger.warn("keys.mode tidak valid '" + modeStr + "', fallback ke VIRTUAL.");
            globalMode = KeyMode.VIRTUAL;
        }

        defaultPhysicalMaterial = Material.matchMaterial(
                plugin.getConfig().getString("keys.physical.material", "TRIPWIRE_HOOK"));
        if (defaultPhysicalMaterial == null) defaultPhysicalMaterial = Material.TRIPWIRE_HOOK;

        defaultPhysicalCmd  = plugin.getConfig().getInt("keys.physical.custom-model-data", -1);
        physicalExtraLore   = plugin.getConfig().getStringList("keys.physical.extra-lore");

        Logger.info("Key mode: &b" + globalMode.name());
    }

    public KeyMode getGlobalMode() { return globalMode; }

    /* ─────────────────────── Give Key ─────────────────────── */

    /**
     * Kasih key ke player — behavior sesuai global mode (virtual/physical).
     * Dipanggil dari command /qc give dan REST API.
     */
    public boolean giveKey(Player player, String keyId, int amount) {
        if (globalMode == KeyMode.VIRTUAL) {
            plugin.getDatabaseManager()
                  .addVirtualKeys(player.getUniqueId(), keyId, amount)
                  .thenRun(() -> MessageManager.send(player, "key-given-receiver",
                          "{amount}", String.valueOf(amount), "{key}", keyId));

            // Broadcast ke WebSocket
            plugin.getAsyncExecutor().execute(() -> {
                try {
                    int bal = plugin.getDatabaseManager()
                            .getVirtualKeys(player.getUniqueId(), keyId).get();
                    me.bintanq.quantumcrates.web.WebSocketBridge.getInstance()
                            .broadcastKeyTransaction(player.getUniqueId(), keyId, amount, bal);
                } catch (Exception ignored) {}
            });
        } else {
            // PHYSICAL — kasih item ke inventory
            int remaining = amount;
            while (remaining > 0) {
                int stack = Math.min(remaining, 64);
                ItemStack key = PhysicalKeyItem.create(
                        plugin, keyId, stack,
                        "&bCrate Key &8[&7" + keyId + "&8]",
                        buildPhysicalLore(keyId),
                        defaultPhysicalMaterial,
                        defaultPhysicalCmd
                );
                var overflow = player.getInventory().addItem(key);
                overflow.values().forEach(drop ->
                        player.getWorld().dropItemNaturally(player.getLocation(), drop));
                remaining -= stack;
            }
            MessageManager.send(player, "key-given-receiver",
                    "{amount}", String.valueOf(amount), "{key}", keyId);
        }
        return true;
    }

    private List<String> buildPhysicalLore(String keyId) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Key ID: &e" + keyId);
        lore.addAll(physicalExtraLore);
        return lore;
    }

    /* ─────────────────────── Has Keys ─────────────────────── */

    public boolean hasRequiredKeys(Player player, Crate crate) {
        for (Crate.KeyRequirement req : crate.getRequiredKeys()) {
            if (countAvailable(player, req) < req.getAmount()) return false;
        }
        return true;
    }

    public int countPossibleOpens(Player player, Crate crate) {
        int possible = Integer.MAX_VALUE;
        for (Crate.KeyRequirement req : crate.getRequiredKeys()) {
            int opens = countAvailable(player, req) / Math.max(1, req.getAmount());
            possible = Math.min(possible, opens);
        }
        return possible == Integer.MAX_VALUE ? 0 : possible;
    }

    private int countAvailable(Player player, Crate.KeyRequirement req) {
        return switch (req.getType()) {
            case VIRTUAL    -> getVirtualBalance(player, req.getKeyId());
            case PHYSICAL   -> countPhysical(player, req.getKeyId());
            case MMOITEMS   -> { var h = plugin.getHookManager().getMmoItemsHook();   yield h != null ? h.countKey(player, req.getKeyId()) : 0; }
            case ITEMSADDER -> { var h = plugin.getHookManager().getItemsAdderHook(); yield h != null ? h.countKey(player, req.getKeyId()) : 0; }
            case ORAXEN     -> { var h = plugin.getHookManager().getOraxenHook();     yield h != null ? h.countKey(player, req.getKeyId()) : 0; }
        };
    }

    /* ─────────────────────── Consume Keys ─────────────────────── */

    public boolean consumeKeys(Player player, Crate crate) {
        // Pre-check dulu
        for (Crate.KeyRequirement req : crate.getRequiredKeys()) {
            if (countAvailable(player, req) < req.getAmount()) return false;
        }
        // Consume semua
        for (Crate.KeyRequirement req : crate.getRequiredKeys()) {
            if (!consumeKey(player, req)) {
                Logger.warn("Key consume gagal mid-way: " + player.getName() + " key=" + req.getKeyId());
                return false;
            }
        }
        return true;
    }

    private boolean consumeKey(Player player, Crate.KeyRequirement req) {
        return switch (req.getType()) {
            case VIRTUAL    -> removeVirtual(player, req.getKeyId(), req.getAmount());
            case PHYSICAL   -> removePhysical(player, req.getKeyId(), req.getAmount());
            case MMOITEMS   -> { var h = plugin.getHookManager().getMmoItemsHook();   yield h != null && h.removeKey(player, req.getKeyId(), req.getAmount()); }
            case ITEMSADDER -> { var h = plugin.getHookManager().getItemsAdderHook(); yield h != null && h.removeKey(player, req.getKeyId(), req.getAmount()); }
            case ORAXEN     -> { var h = plugin.getHookManager().getOraxenHook();     yield h != null && h.removeKey(player, req.getKeyId(), req.getAmount()); }
        };
    }

    /* ─────────────────────── Virtual ─────────────────────── */

    public int getVirtualBalance(Player player, String keyId) {
        try {
            return plugin.getDatabaseManager()
                    .getVirtualKeys(player.getUniqueId(), keyId)
                    .get(1, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            Logger.warn("Virtual key balance timeout: " + player.getName());
            return 0;
        }
    }

    private boolean removeVirtual(Player player, String keyId, int amount) {
        try {
            return plugin.getDatabaseManager()
                    .removeVirtualKeys(player.getUniqueId(), keyId, amount)
                    .get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            Logger.severe("Gagal remove virtual key: " + e.getMessage());
            return false;
        }
    }

    /* ─────────────────────── Physical ─────────────────────── */

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

    /** Untuk REST API /api/keys */
    public Collection<String> getKnownKeyIds() {
        // Collect dari semua crate yang terdaftar
        Set<String> ids = new LinkedHashSet<>();
        for (Crate c : plugin.getCrateManager().getAllCrates()) {
            c.getRequiredKeys().forEach(r -> ids.add(r.getKeyId()));
        }
        return ids;
    }
}
