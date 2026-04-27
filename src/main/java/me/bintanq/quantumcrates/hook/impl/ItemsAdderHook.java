package me.bintanq.quantumcrates.hook.impl;

import dev.lone.itemsadder.api.CustomStack;
import me.bintanq.quantumcrates.hook.ItemHook;
import me.bintanq.quantumcrates.model.reward.Reward;
import me.bintanq.quantumcrates.util.Logger;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * ItemsAdderHook — integration with ItemsAdder for custom item rewards and keys.
 *
 * Items are identified by their namespaced ID, e.g. "mypack:ruby_sword".
 */
public class ItemsAdderHook implements ItemHook {

    private boolean enabled = false;

    public ItemsAdderHook() {
        try {
            Class.forName("dev.lone.itemsadder.api.CustomStack");
            enabled = true;
        } catch (ClassNotFoundException e) {
            enabled = false;
        }
    }

    @Override
    public String getPluginName() { return "ItemsAdder"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public ItemStack buildItem(Reward reward) {
        if (!enabled) return null;
        try {
            CustomStack customStack = CustomStack.getInstance(reward.getItemsAdderId());
            if (customStack == null) {
                Logger.warn("ItemsAdder: Unknown item '" + reward.getItemsAdderId() + "' for reward " + reward.getId());
                return null;
            }
            ItemStack item = customStack.getItemStack().clone();
            item.setAmount(Math.max(1, reward.getAmount()));
            return item;
        } catch (Exception e) {
            Logger.warn("ItemsAdder buildItem failed: " + e.getMessage());
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
                CustomStack customStack = CustomStack.byItemStack(item);
                if (customStack != null && keyId.equalsIgnoreCase(customStack.getNamespacedID())) {
                    count += item.getAmount();
                }
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
                CustomStack customStack = CustomStack.byItemStack(item);
                if (customStack == null || !keyId.equalsIgnoreCase(customStack.getNamespacedID())) continue;
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
