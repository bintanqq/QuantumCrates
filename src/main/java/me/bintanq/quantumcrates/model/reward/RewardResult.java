package me.bintanq.quantumcrates.model.reward;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * RewardResult — the materialized output of a single crate roll.
 *
 * Contains the chosen {@link Reward} definition, the actual {@link ItemStack}
 * (if applicable), and any commands to execute. Passed from RewardProcessor
 * back to CrateManager for delivery to the player.
 */
public class RewardResult {

    private final Reward reward;
    private final ItemStack itemStack;   // null for COMMAND-only rewards
    private final List<String> commands; // commands with %player% placeholder

    private final boolean isPityGuaranteed;
    private final int pityAtRoll;

    public RewardResult(
            Reward reward,
            ItemStack itemStack,
            List<String> commands,
            boolean isPityGuaranteed,
            int pityAtRoll
    ) {
        this.reward           = reward;
        this.itemStack        = itemStack;
        this.commands         = commands;
        this.isPityGuaranteed = isPityGuaranteed;
        this.pityAtRoll       = pityAtRoll;
    }

    public Reward getReward() { return reward; }
    public ItemStack getItemStack() { return itemStack; }
    public List<String> getCommands() { return commands; }
    public boolean hasItem() { return itemStack != null; }
    public boolean hasCommands() { return commands != null && !commands.isEmpty(); }
    public boolean isPityGuaranteed() { return isPityGuaranteed; }
    public int getPityAtRoll() { return pityAtRoll; }
}
