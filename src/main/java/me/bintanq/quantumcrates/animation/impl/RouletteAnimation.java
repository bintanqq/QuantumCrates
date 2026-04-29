package me.bintanq.quantumcrates.animation.impl;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.animation.AnimationUtil;
import me.bintanq.quantumcrates.animation.CrateAnimation;
import me.bintanq.quantumcrates.animation.CrateSession;
import me.bintanq.quantumcrates.model.reward.Reward;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public class RouletteAnimation implements CrateAnimation {

    // Slots in inventory row 2 that form the roulette strip
    private static final int[] STRIP_SLOTS  = {10, 11, 12, 13, 14, 15, 16};
    private static final int   WINNER_SLOT  = 13; // center of strip
    private static final int   STRIP_LEN    = STRIP_SLOTS.length;

    // Total virtual "spins" before landing
    private static final int   TOTAL_TICKS  = 60;

    private final QuantumCrates plugin;

    public RouletteAnimation(QuantumCrates plugin) {
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

        // Arrow indicators above/below center
        inv.setItem(4,  AnimationUtil.filler(Material.RED_STAINED_GLASS_PANE));
        inv.setItem(WINNER_SLOT - 9, AnimationUtil.filler(Material.YELLOW_STAINED_GLASS_PANE));
        inv.setItem(WINNER_SLOT + 9, AnimationUtil.filler(Material.YELLOW_STAINED_GLASS_PANE));

        // Build a scroll queue: random rewards + winner guaranteed at position [STRIP_LEN/2] from end
        Deque<Reward> scrollQueue = buildScrollQueue(pool, winner, TOTAL_TICKS + STRIP_LEN);

        session.getPlayer().openInventory(inv);

        final int[] tick = {0};
        final Reward[] currentStrip = new Reward[STRIP_LEN];
        // Prime the strip
        for (int i = 0; i < STRIP_LEN; i++) {
            currentStrip[i] = scrollQueue.isEmpty() ? AnimationUtil.randomReward(pool) : scrollQueue.poll();
        }

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!session.isRunning()) return;

            if (tick[0] >= TOTAL_TICKS) {
                finish(session, winner, inv);
                return;
            }

            // Exponential easing: delay increases as tick approaches TOTAL_TICKS
            // We control speed by skipping ticks via a period that grows
            double progress = (double) tick[0] / TOTAL_TICKS;
            int period = computePeriod(progress);

            if (tick[0] % period == 0) {
                // Shift strip left by 1, pull new item from queue
                System.arraycopy(currentStrip, 1, currentStrip, 0, STRIP_LEN - 1);
                currentStrip[STRIP_LEN - 1] = scrollQueue.isEmpty()
                        ? AnimationUtil.randomReward(pool)
                        : scrollQueue.poll();

                for (int i = 0; i < STRIP_LEN; i++) {
                    inv.setItem(STRIP_SLOTS[i], AnimationUtil.buildDisplayItem(currentStrip[i]));
                }
                AnimationUtil.playTickSound(session.getPlayer(), progress);
            }

            tick[0]++;
        }, 0L, 1L);

        session.addTask(task);
    }

    /** Period in ticks between scroll steps. Exponential: 1 → 8 */
    private int computePeriod(double progress) {
        // progress 0→1, period 1→8 exponentially
        return (int) Math.max(1, Math.pow(2, progress * 3));
    }

    private void finish(CrateSession session, Reward winner, Inventory inv) {
        if (!session.isRunning() || session.isForfeited()) return;

        // Lock center on winner
        AnimationUtil.fillAll(inv, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(WINNER_SLOT - 9, AnimationUtil.filler(Material.LIME_STAINED_GLASS_PANE));
        inv.setItem(WINNER_SLOT + 9, AnimationUtil.filler(Material.LIME_STAINED_GLASS_PANE));
        inv.setItem(WINNER_SLOT,     AnimationUtil.buildDisplayItem(winner));

        AnimationUtil.playWinSound(session.getPlayer());

        BukkitTask closeTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (session.isForfeited()) return;
            session.getPlayer().closeInventory();
            plugin.getAnimationManager().completeSession(session);
            plugin.getCrateManager().deliverRewardPublic(session.getPlayer(), session.getResult());
        }, 40L);

        session.addTask(closeTask);
    }

    private Deque<Reward> buildScrollQueue(List<Reward> pool, Reward winner, int size) {
        Deque<Reward> queue = new ArrayDeque<>(size);
        for (int i = 0; i < size - STRIP_LEN / 2 - 1; i++) {
            queue.add(AnimationUtil.randomReward(pool));
        }
        queue.add(winner);
        for (int i = 0; i < STRIP_LEN / 2; i++) {
            queue.add(AnimationUtil.randomReward(pool));
        }
        return queue;
    }

    @Override public void cancel(CrateSession session) { session.cancelAllTasks(); }
    @Override public boolean isRunning(CrateSession session) { return session.isRunning(); }

    private String colorize(String s) { return s.replace("&", "\u00A7"); }
}