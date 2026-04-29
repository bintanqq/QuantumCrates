package me.bintanq.quantumcrates.animation.impl;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.animation.AnimationUtil;
import me.bintanq.quantumcrates.animation.CrateAnimation;
import me.bintanq.quantumcrates.animation.CrateSession;
import me.bintanq.quantumcrates.model.reward.Reward;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class FlickerAnimation implements CrateAnimation {

    private static final int   WINNER_SLOT  = 22;
    private static final int   TOTAL_TICKS  = 60;
    private static final int   FLICKER_SLOTS = 54;

    private final QuantumCrates plugin;

    public FlickerAnimation(QuantumCrates plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start(CrateSession session) {
        List<Reward> pool = session.getCrate().getRewards();
        Reward winner = session.getResult().getReward();

        Inventory inv = Bukkit.createInventory(null, 54,
                "\u00A70\u00A7l" + colorize(session.getCrate().getDisplayName() != null
                        ? session.getCrate().getDisplayName() : session.getCrate().getId()));
        session.setInventory(inv);
        AnimationUtil.fillAll(inv, Material.BLACK_STAINED_GLASS_PANE);
        session.getPlayer().openInventory(inv);

        final int[] tick = {0};
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!session.isRunning()) return;

            double progress = (double) tick[0] / TOTAL_TICKS;

            // How many slots to flicker: decreases as progress increases (converges)
            int flickerCount = (int) Math.max(1, FLICKER_SLOTS * (1.0 - progress * progress));

            // Clear all then randomly scatter rewards
            AnimationUtil.fillAll(inv, Material.BLACK_STAINED_GLASS_PANE);
            for (int i = 0; i < flickerCount; i++) {
                int slot = rng.nextInt(FLICKER_SLOTS);
                inv.setItem(slot, AnimationUtil.buildDisplayItem(AnimationUtil.randomReward(pool)));
            }

            // In the last 20% of ticks, force winner into center progressively
            if (progress >= 0.8) {
                inv.setItem(WINNER_SLOT, AnimationUtil.buildDisplayItem(winner));
            }

            AnimationUtil.playTickSound(session.getPlayer(), progress);

            if (tick[0] >= TOTAL_TICKS) {
                finish(session, winner, inv);
                return;
            }
            tick[0]++;
        }, 0L, 1L);

        session.addTask(task);
    }

    private void finish(CrateSession session, Reward winner, Inventory inv) {
        if (!session.isRunning() || session.isForfeited()) return;
        AnimationUtil.fillAll(inv, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(WINNER_SLOT, AnimationUtil.buildDisplayItem(winner));
        AnimationUtil.playWinSound(session.getPlayer());

        BukkitTask close = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (session.isForfeited()) return;
            session.getPlayer().closeInventory();
            plugin.getAnimationManager().completeSession(session);
            plugin.getCrateManager().deliverRewardPublic(session.getPlayer(), session.getResult());
        }, 40L);
        session.addTask(close);
    }

    @Override public void cancel(CrateSession session) { session.cancelAllTasks(); }
    @Override public boolean isRunning(CrateSession session) { return session.isRunning(); }
    private String colorize(String s) { return s.replace("&", "\u00A7"); }
}