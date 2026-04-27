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
 * All rarity comparisons delegate to RarityManager (no hardcoded tier lists).
 */
public class RewardProcessor {

    private final QuantumCrates plugin;
    private final HookManager hookManager;

    public RewardProcessor(QuantumCrates plugin, HookManager hookManager) {
        this.plugin      = plugin;
        this.hookManager = hookManager;
    }

    /* ─────────────────────── Core Roll ─────────────────────── */

    public RewardResult roll(Crate crate, PlayerData playerData) {
        List<Reward> rewards = crate.getRewards();
        if (rewards == null || rewards.isEmpty()) {
            throw new IllegalStateException("Crate '" + crate.getId() + "' has no rewards configured!");
        }

        Crate.PityConfig pity  = crate.getPity();
        int currentPity        = playerData.getPity(crate.getId());
        boolean pityGuaranteed = false;

        if (pity.isEnabled() && currentPity >= pity.getThreshold()) {
            Reward guaranteed = selectGuaranteedRare(rewards, pity.getRareRarityMinimum());
            if (guaranteed != null) {
                pityGuaranteed = true;
                Logger.debug("HARD PITY triggered for " + playerData.getUuid() + " on crate " + crate.getId());
                return buildResult(guaranteed, true, currentPity);
            }
        }

        List<Reward> effectiveRewards = rewards;
        if (pity.isEnabled() && currentPity >= pity.getSoftPityStart()) {
            effectiveRewards = applyPityBoost(rewards, pity, currentPity, crate.getId());
        }

        Reward chosen = weightedRoll(effectiveRewards);
        return buildResult(chosen, false, currentPity);
    }

    private Reward weightedRoll(List<Reward> rewards) {
        double totalWeight = rewards.stream().mapToDouble(Reward::getWeight).sum();
        if (totalWeight <= 0) {
            return rewards.get(ThreadLocalRandom.current().nextInt(rewards.size()));
        }

        double random    = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumWeight = 0;

        for (Reward reward : rewards) {
            cumWeight += reward.getWeight();
            if (random < cumWeight) return reward;
        }

        return rewards.get(rewards.size() - 1);
    }

    private List<Reward> applyPityBoost(
            List<Reward> original,
            Crate.PityConfig pity,
            int currentPity,
            String crateId
    ) {
        int    stepsAboveSoft = currentPity - pity.getSoftPityStart();
        double totalWeight    = original.stream().mapToDouble(Reward::getWeight).sum();
        double bonusPerRare   = (pity.getBonusChancePerOpen() / 100.0) * totalWeight * stepsAboveSoft;

        // Delegate to RarityManager — no hardcoded list
        List<String> rareRarities = plugin.getRarityManager().getIdsAtOrAbove(pity.getRareRarityMinimum());
        List<Reward> boosted = new ArrayList<>(original.size());

        for (Reward r : original) {
            if (rareRarities.contains(r.getRarity().toUpperCase())) {
                boosted.add(cloneWithWeight(r, r.getWeight() + bonusPerRare));
            } else {
                boosted.add(r);
            }
        }

        Logger.debug("Soft pity active on " + crateId + " — steps=" + stepsAboveSoft +
                " bonus/rare=" + String.format("%.2f", bonusPerRare));
        return boosted;
    }

    private Reward selectGuaranteedRare(List<Reward> rewards, String minimumRarity) {
        List<String> qualifyingRarities = plugin.getRarityManager().getIdsAtOrAbove(minimumRarity);
        List<Reward> rares = rewards.stream()
                .filter(r -> qualifyingRarities.contains(r.getRarity().toUpperCase()))
                .toList();
        if (rares.isEmpty()) return null;
        return weightedRoll(rares);
    }

    private RewardResult buildResult(Reward reward, boolean pityGuaranteed, int pityAtRoll) {
        ItemStack item = null;
        if (!reward.isCommandOnly()) {
            item = materializeItem(reward);
        }
        return new RewardResult(reward, item, reward.getCommands(), pityGuaranteed, pityAtRoll);
    }

    /* ─────────────────────── Item Materialization ─────────────────────── */

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
            meta.setLore(reward.getLore().stream().map(this::colorize).toList());
        }

        if (reward.getCustomModelData() > 0) {
            meta.setCustomModelData(reward.getCustomModelData());
        }

        item.setItemMeta(meta);

        if (reward.getEnchantments() != null) {
            for (Reward.EnchantmentEntry entry : reward.getEnchantments()) {
                try {
                    Enchantment ench = Enchantment.getByKey(
                            NamespacedKey.minecraft(entry.enchantment.toLowerCase()));
                    if (ench != null) item.addUnsafeEnchantment(ench, entry.level);
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

    /* ─────────────────────── Helpers ─────────────────────── */

    private Reward cloneWithWeight(Reward original, double newWeight) {
        return new WeightOverrideReward(original, newWeight);
    }

    private String colorize(String s) {
        return s.replace("&", "\u00A7");
    }

    /* ─────────────────────── WeightOverrideReward ─────────────────────── */

    private static class WeightOverrideReward extends Reward {
        private final Reward delegate;
        private final double overrideWeight;

        WeightOverrideReward(Reward delegate, double overrideWeight) {
            this.delegate      = delegate;
            this.overrideWeight = overrideWeight;
        }

        @Override public String getId()            { return delegate.getId(); }
        @Override public String getDisplayName()   { return delegate.getDisplayName(); }
        @Override public double getWeight()         { return overrideWeight; }
        @Override public String getRarity()         { return delegate.getRarity(); }
        @Override public RewardType getType()       { return delegate.getType(); }
        @Override public String getMaterial()       { return delegate.getMaterial(); }
        @Override public int    getAmount()         { return delegate.getAmount(); }
        @Override public String getItemName()       { return delegate.getItemName(); }
        @Override public java.util.List<String> getLore()              { return delegate.getLore(); }
        @Override public java.util.List<EnchantmentEntry> getEnchantments() { return delegate.getEnchantments(); }
        @Override public int    getCustomModelData()                   { return delegate.getCustomModelData(); }
        @Override public String getMmoItemsType()  { return delegate.getMmoItemsType(); }
        @Override public String getMmoItemsId()    { return delegate.getMmoItemsId(); }
        @Override public String getItemsAdderId()  { return delegate.getItemsAdderId(); }
        @Override public String getOraxenId()      { return delegate.getOraxenId(); }
        @Override public java.util.List<String> getCommands()         { return delegate.getCommands(); }
        @Override public boolean isBroadcast()     { return delegate.isBroadcast(); }
        @Override public String getBroadcastMessage()                 { return delegate.getBroadcastMessage(); }
        @Override public boolean isCommandOnly()   { return delegate.isCommandOnly(); }
    }
}