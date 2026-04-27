package me.bintanq.quantumcrates.hook.impl;

import me.bintanq.quantumcrates.hook.ItemHook;
import me.bintanq.quantumcrates.model.reward.Reward;
import me.bintanq.quantumcrates.util.Logger;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;

import net.Indyuce.mmoitems.manager.TypeManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * MMOItemsHook — integration with MMOItems for custom item rewards and keys.
 */
public class MMOItemsHook implements ItemHook {

    private boolean enabled = false;

    public MMOItemsHook() {
        try {
            Class.forName("net.Indyuce.mmoitems.MMOItems");
            enabled = true;
        } catch (ClassNotFoundException e) {
            enabled = false;
        }
    }

    @Override
    public String getPluginName() { return "MMOItems"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public ItemStack buildItem(Reward reward) {
        if (!enabled) return null;
        try {
            TypeManager typeManager = MMOItems.plugin.getTypes();
            Type type = typeManager.get(reward.getMmoItemsType());
            if (type == null) {
                Logger.warn("MMOItems: Unknown type '" + reward.getMmoItemsType() + "' for reward " + reward.getId());
                return null;
            }
            MMOItem mmoItem = MMOItems.plugin.getMMOItem(type, reward.getMmoItemsId());
            if (mmoItem == null) {
                Logger.warn("MMOItems: Unknown item ID '" + reward.getMmoItemsId() + "'");
                return null;
            }
            return mmoItem.newBuilder().build();
        } catch (Exception e) {
            Logger.warn("MMOItems buildItem failed: " + e.getMessage());
            return null;
        }
    }

    @Override
    public int countKey(Player player, String keyId) {
        if (!enabled) return 0;
        // MMOItems keys are physical items — scan inventory for matching MMOItem ID
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) continue;
            try {
                String id = net.Indyuce.mmoitems.MMOItems.getID(item);
                if (keyId.equalsIgnoreCase(id)) count += item.getAmount();
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
                String id = net.Indyuce.mmoitems.MMOItems.getID(item);
                if (!keyId.equalsIgnoreCase(id)) continue;
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
