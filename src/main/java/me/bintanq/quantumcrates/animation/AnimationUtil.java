package me.bintanq.quantumcrates.animation;

import me.bintanq.quantumcrates.model.reward.Reward;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class AnimationUtil {

    private AnimationUtil() {}

    public static ItemStack buildDisplayItem(Reward reward) {
        Material mat = reward.getMaterial() != null
                ? Material.matchMaterial(reward.getMaterial().toUpperCase())
                : null;
        if (mat == null) mat = Material.PAPER;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = reward.getDisplayName() != null
                    ? reward.getDisplayName().replace("&", "\u00A7")
                    : reward.getId();
            meta.setDisplayName(name);
            if (reward.getLore() != null && !reward.getLore().isEmpty()) {
                meta.setLore(reward.getLore().stream()
                        .map(l -> l.replace("&", "\u00A7"))
                        .toList());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack filler(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName("\u00A7r"); item.setItemMeta(meta); }
        return item;
    }

    public static void fillAll(Inventory inv, Material mat) {
        ItemStack f = filler(mat);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, f);
    }

    /**
     * @param progress 0.0 (start) → 1.0 (end)
     */
    public static void playTickSound(Player player, double progress) {
        float pitch = (float) (0.5 + progress * 1.5);
        pitch = Math.max(0.5f, Math.min(2.0f, pitch));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, pitch);
    }

    public static void playWinSound(Player player) {
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }

    public static Reward randomReward(List<Reward> rewards) {
        return rewards.get(ThreadLocalRandom.current().nextInt(rewards.size()));
    }
}