package me.bintanq.quantumcrates.processor;

import me.bintanq.quantumcrates.hook.HookManager;
import me.bintanq.quantumcrates.model.Crate;
import me.bintanq.quantumcrates.model.PlayerData;
import me.bintanq.quantumcrates.model.reward.Reward;
import me.bintanq.quantumcrates.model.reward.RewardResult;
import me.bintanq.quantumcrates.util.Logger;
import me.bintanq.quantumcrates.QuantumCrates;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * RewardProcessor — the heart of the loot engine.
 *
 * Responsibilities:
 *  1. Weight-based probabilistic reward selection (correct, no floating-point drift).
 *  2. Soft/Hard pity integration — adjusts effective weights before rolling.
 *  3. Materializes the winning Reward into a real ItemStack via the correct hook.
 *  4. Executes command rewards on the main thread.
 */
public class RewardProcessor {

    private final QuantumCrates plugin;
    private final HookManager hookManager;

    public RewardProcessor(QuantumCrates plugin, HookManager hookManager) {
        this.plugin      = plugin;
        this.hookManager = hookManager;
    }

    /* ─────────────────────── Core Roll ─────────────────────── */

    /**
     * Rolls a single reward from the given crate, taking pity into account.
     *
     * This method is pure computation — does NOT give items or execute commands.
     * Delivery is handled by {@link me.bintanq.quantumcrates.manager.CrateManager}.
     *
     * @param crate      The crate being opened
     * @param playerData The opening player's persistent data
     * @return A {@link RewardResult} describing the outcome
     */
    public RewardResult roll(Crate crate, PlayerData playerData) {
        List<Reward> rewards = crate.getRewards();
        if (rewards == null || rewards.isEmpty()) {
            throw new IllegalStateException("Crate '" + crate.getId() + "' has no rewards configured!");
        }

        Crate.PityConfig pity  = crate.getPity();
        int currentPity        = playerData.getPity(crate.getId());
        boolean pityGuaranteed = false;

        // ── Hard Pity: Guarantee a rare reward ──
        if (pity.isEnabled() && currentPity >= pity.getThreshold()) {
            Reward guaranteed = selectGuaranteedRare(rewards, pity.getRareRarityMinimum());
            if (guaranteed != null) {
                pityGuaranteed = true;
                Logger.debug("HARD PITY triggered for " + playerData.getUuid() + " on crate " + crate.getId());
                return buildResult(guaranteed, pityGuaranteed, currentPity);
            }
        }

        // ── Soft Pity: Boost rare weights ──
        List<Reward> effectiveRewards = rewards;
        if (pity.isEnabled() && currentPity >= pity.getSoftPityStart()) {
            effectiveRewards = applyPityBoost(rewards, pity, currentPity, crate.getId());
        }

        // ── Standard Weight-Based Roll ──
        Reward chosen = weightedRoll(effectiveRewards);
        return buildResult(chosen, false, currentPity);
    }

    /**
     * Performs a weight-based random selection.
     *
     * Algorithm: Sum all weights, pick random double in [0, totalWeight),
     * walk through rewards subtracting weights until cumulative >= random.
     * O(n) — fine for typical reward pool sizes (< 100 items).
     */
    private Reward weightedRoll(List<Reward> rewards) {
        double totalWeight = rewards.stream().mapToDouble(Reward::getWeight).sum();
        if (totalWeight <= 0) {
            // Fallback: uniform random if all weights are 0
            return rewards.get(ThreadLocalRandom.current().nextInt(rewards.size()));
        }

        double random    = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumWeight = 0;

        for (Reward reward : rewards) {
            cumWeight += reward.getWeight();
            if (random < cumWeight) return reward;
        }

        // Should never reach here due to floating-point edge cases — return last
        return rewards.get(rewards.size() - 1);
    }

    /**
     * Creates a modified reward list where rare items have boosted weights.
     * Each step above softPityStart adds {@code bonusChancePerOpen} % of total weight
     * to each qualifying rare reward.
     */
    private List<Reward> applyPityBoost(
            List<Reward> original,
            Crate.PityConfig pity,
            int currentPity,
            String crateId
    ) {
        int    stepsAboveSoft = currentPity - pity.getSoftPityStart();
        double totalWeight    = original.stream().mapToDouble(Reward::getWeight).sum();
        double bonusPerRare   = (pity.getBonusChancePerOpen() / 100.0) * totalWeight * stepsAboveSoft;

        List<String> rareRarities = getRaritiesAtOrAbove(pity.getRareRarityMinimum());
        List<Reward> boosted = new ArrayList<>(original.size());

        for (Reward r : original) {
            if (rareRarities.contains(r.getRarity().toUpperCase())) {
                // Create a temporary view with boosted weight (do NOT mutate original)
                Reward boostedReward = cloneWithWeight(r, r.getWeight() + bonusPerRare);
                boosted.add(boostedReward);
            } else {
                boosted.add(r);
            }
        }

        Logger.debug("Soft pity active on " + crateId + " — steps=" + stepsAboveSoft +
                     " bonus/rare=" + String.format("%.2f", bonusPerRare));
        return boosted;
    }

    private Reward selectGuaranteedRare(List<Reward> rewards, String minimumRarity) {
        List<String> qualifyingRarities = getRaritiesAtOrAbove(minimumRarity);
        List<Reward> rares = rewards.stream()
                .filter(r -> qualifyingRarities.contains(r.getRarity().toUpperCase()))
                .toList();
        if (rares.isEmpty()) return null;
        return weightedRoll(rares); // still weight-based among rares
    }

    private RewardResult buildResult(Reward reward, boolean pityGuaranteed, int pityAtRoll) {
        ItemStack item = null;
        if (!reward.isCommandOnly()) {
            item = materializeItem(reward);
        }
        return new RewardResult(reward, item, reward.getCommands(), pityGuaranteed, pityAtRoll);
    }

    /* ─────────────────────── Item Materialization ─────────────────────── */

    /**
     * Converts a Reward definition into a real Bukkit ItemStack.
     * Delegates to the appropriate hook for custom item plugins.
     */
    public ItemStack materializeItem(Reward reward) {
        return switch (reward.getType()) {
            case VANILLA, VANILLA_WITH_COMMANDS, COMMAND -> buildVanillaItem(reward);
            case MMOITEMS   -> hookManager.getMmoItemsHook() != null
                               ? hookManager.getMmoItemsHook().buildItem(reward)
                               : fallback(reward, "MMOItems");
            case ITEMSADDER -> hookManager.getItemsAdderHook() != null
                               ? hookManager.getItemsAdderHook().buildItem(reward)
                               : fallback(reward, "ItemsAdder");
            case ORAXEN     -> hookManager.getOraxenHook() != null
                               ? hookManager.getOraxenHook().buildItem(reward)
                               : fallback(reward, "Oraxen");
        };
    }

    private ItemStack buildVanillaItem(Reward reward) {
        Material material = Material.matchMaterial(
                reward.getMaterial() != null ? reward.getMaterial() : "STONE");
        if (material == null) {
            Logger.warn("Invalid material '" + reward.getMaterial() + "' in reward '" + reward.getId() + "'. Using STONE.");
            material = Material.STONE;
        }

        ItemStack item = new ItemStack(material, Math.max(1, reward.getAmount()));
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        if (reward.getItemName() != null && !reward.getItemName().isEmpty()) {
            meta.setDisplayName(colorize(reward.getItemName()));
        }

        if (reward.getLore() != null && !reward.getLore().isEmpty()) {
            List<String> coloredLore = reward.getLore().stream()
                    .map(this::colorize)
                    .toList();
            meta.setLore(coloredLore);
        }

        if (reward.getCustomModelData() > 0) {
            meta.setCustomModelData(reward.getCustomModelData());
        }

        item.setItemMeta(meta);

        // Enchantments
        if (reward.getEnchantments() != null) {
            for (Reward.EnchantmentEntry entry : reward.getEnchantments()) {
                try {
                    Enchantment ench = Enchantment.getByKey(
                            NamespacedKey.minecraft(entry.enchantment.toLowerCase()));
                    if (ench != null) {
                        item.addUnsafeEnchantment(ench, entry.level);
                    }
                } catch (Exception e) {
                    Logger.warn("Unknown enchantment: " + entry.enchantment);
                }
            }
        }

        return item;
    }

    private ItemStack fallback(Reward reward, String pluginName) {
        Logger.warn(pluginName + " hook not loaded — reward '" + reward.getId() + "' falls back to vanilla.");
        return buildVanillaItem(reward);
    }

    /* ─────────────────────── Command Execution ─────────────────────── */

    /**
     * Executes the commands attached to a reward.
     * MUST be called from the main thread (Bukkit scheduler dispatch).
     *
     * Command prefix rules:
     *  - "player:" prefix  → executed as the player
     *  - "console:" prefix → executed by console (default if no prefix)
     */
    public void executeCommands(Player player, RewardResult result) {
        if (!result.hasCommands()) return;
        for (String rawCmd : result.getCommands()) {
            String cmd = rawCmd
                    .replace("%player%", player.getName())
                    .replace("{player}", player.getName());

            if (cmd.startsWith("player:")) {
                player.performCommand(cmd.substring(7).trim());
            } else {
                String finalCmd = cmd.startsWith("console:") ? cmd.substring(8).trim() : cmd;
                plugin.getServer().dispatchCommand(
                        plugin.getServer().getConsoleSender(), finalCmd);
            }
        }
    }

    /* ─────────────────────── Rarity Ordering ─────────────────────── */

    /** Ordered rarity tiers from lowest to highest. */
    private static final List<String> RARITY_ORDER = List.of(
            "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"
    );

    /**
     * Returns all rarity names at or above the given minimum.
     * Unlisted rarities are treated as COMMON-level.
     */
    private List<String> getRaritiesAtOrAbove(String minimum) {
        int minIndex = RARITY_ORDER.indexOf(minimum.toUpperCase());
        if (minIndex < 0) minIndex = 0;
        return RARITY_ORDER.subList(minIndex, RARITY_ORDER.size());
    }

    /* ─────────────────────── Helpers ─────────────────────── */

    private Reward cloneWithWeight(Reward original, double newWeight) {
        // We return a lightweight wrapper — avoids full deep-clone
        return new WeightOverrideReward(original, newWeight);
    }

    private String colorize(String s) {
        return s.replace("&", "\u00A7");
    }

    /* ─────────────────────── WeightOverrideReward ─────────────────────── */

    /**
     * Thin wrapper that overrides the weight of an existing Reward.
     * Used for soft-pity boosting without mutating original config.
     */
    private static class WeightOverrideReward extends Reward {
        private final Reward delegate;
        private final double overrideWeight;

        WeightOverrideReward(Reward delegate, double overrideWeight) {
            this.delegate      = delegate;
            this.overrideWeight = overrideWeight;
        }

        @Override public String getId()           { return delegate.getId(); }
        @Override public String getDisplayName()  { return delegate.getDisplayName(); }
        @Override public double getWeight()        { return overrideWeight; }
        @Override public String getRarity()        { return delegate.getRarity(); }
        @Override public RewardType getType()      { return delegate.getType(); }
        @Override public String getMaterial()      { return delegate.getMaterial(); }
        @Override public int    getAmount()        { return delegate.getAmount(); }
        @Override public String getItemName()      { return delegate.getItemName(); }
        @Override public java.util.List<String> getLore() { return delegate.getLore(); }
        @Override public java.util.List<EnchantmentEntry> getEnchantments() { return delegate.getEnchantments(); }
        @Override public int    getCustomModelData(){ return delegate.getCustomModelData(); }
        @Override public String getMmoItemsType()  { return delegate.getMmoItemsType(); }
        @Override public String getMmoItemsId()    { return delegate.getMmoItemsId(); }
        @Override public String getItemsAdderId()  { return delegate.getItemsAdderId(); }
        @Override public String getOraxenId()      { return delegate.getOraxenId(); }
        @Override public java.util.List<String> getCommands() { return delegate.getCommands(); }
        @Override public boolean isBroadcast()     { return delegate.isBroadcast(); }
        @Override public String getBroadcastMessage(){ return delegate.getBroadcastMessage(); }
        @Override public boolean isCommandOnly()   { return delegate.isCommandOnly(); }
    }
}
