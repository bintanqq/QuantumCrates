package me.bintanq.quantumcrates.hook.impl;

import io.th0rgal.oraxen.api.OraxenItems;
import me.bintanq.quantumcrates.hook.ItemHook;
import me.bintanq.quantumcrates.model.reward.Reward;
import me.bintanq.quantumcrates.util.Logger;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * OraxenHook — integration with Oraxen for custom item rewards and keys.
 */
public class OraxenHook implements ItemHook {

    private boolean enabled = false;

    public OraxenHook() {
        try {
            Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            enabled = true;
        } catch (ClassNotFoundException e) {
            enabled = false;
        }
    }

    @Override
    public String getPluginName() { return "Oraxen"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public ItemStack buildItem(Reward reward) {
        if (!enabled) return null;
        try {
            var builder = OraxenItems.getItemById(reward.getOraxenId());
            if (builder == null) {
                Logger.warn("Oraxen: Unknown item '" + reward.getOraxenId() + "' for reward " + reward.getId());
                return null;
            }
            ItemStack item = builder.build();
            item.setAmount(Math.max(1, reward.getAmount()));
            return item;
        } catch (Exception e) {
            Logger.warn("Oraxen buildItem failed: " + e.getMessage());
            return null;
        }
    }

    @Override
    public int countKey(Player player, String keyId) {
        if (!enabled) return 0;
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) continue;
            try {
                String oraxenId = OraxenItems.getIdByItem(item);
                if (keyId.equalsIgnoreCase(oraxenId)) count += item.getAmount();
            } catch (Exception ignored) {}
        }
        return count;
    }

    @Override
    public boolean removeKey(Player player, String keyId, int amount) {
        if (!enabled) return false;
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType().isAir()) continue;
            try {
                String oraxenId = OraxenItems.getIdByItem(item);
                if (!keyId.equalsIgnoreCase(oraxenId)) continue;
                if (item.getAmount() <= remaining) {
                    remaining -= item.getAmount();
                    player.getInventory().setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - remaining);
                    remaining = 0;
                }
            } catch (Exception ignored) {}
        }
        return remaining <= 0;
    }
}
