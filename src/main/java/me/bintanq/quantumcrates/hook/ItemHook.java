package me.bintanq.quantumcrates.hook;

import me.bintanq.quantumcrates.model.reward.Reward;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * ItemHook — contract for all custom item plugin integrations.
 * Implement this for MMOItems, ItemsAdder, Oraxen, etc.
 */
public interface ItemHook {

    /** Plugin name for logging. */
    String getPluginName();

    /** Returns true if the hook successfully loaded. */
    boolean isEnabled();

    /** Builds a real ItemStack from a Reward definition. */
    ItemStack buildItem(Reward reward);

    /** Counts how many of the given key ID the player holds. */
    int countKey(Player player, String keyId);

    /** Removes {@code amount} of the key from the player's inventory. Returns false if insufficient. */
    boolean removeKey(Player player, String keyId, int amount);
}
