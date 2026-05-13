package me.bintanq.quantumcrates.animation.impl;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.animation.AnimationUtil;
import me.bintanq.quantumcrates.animation.CrateAnimation;
import me.bintanq.quantumcrates.animation.CrateSession;
import me.bintanq.quantumcrates.model.reward.Reward;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FlickerAnimation implements CrateAnimation {

    private static final int WINNER_SLOT = 22;
    private static final int INV_SIZE    = 54;
    private static final long TICK_INTERVAL = 2L; // mirrors Enclosing piston sound timing

    private final QuantumCrates plugin;

    public FlickerAnimation(QuantumCrates plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start(CrateSession session) {
        List<Reward> pool   = session.getCrate().getRewards();
        Reward       winner = session.getResult().getReward();

        Inventory inv = Bukkit.createInventory(null, INV_SIZE,
                "\u00A70\u00A7l" + colorize(session.getCrate().getDisplayName() != null
                        ? session.getCrate().getDisplayName() : session.getCrate().getId()));
        session.setInventory(inv);

        // Phase 1: fill ALL slots with random rewards
        for (int i = 0; i < INV_SIZE; i++) {
            inv.setItem(i, AnimationUtil.buildDisplayItem(AnimationUtil.randomReward(pool), plugin.getHookManager()));
        }
        // Winner always visible at center
        inv.setItem(WINNER_SLOT, AnimationUtil.buildDisplayItem(winner, plugin.getHookManager()));

        session.getPlayer().openInventory(inv);

        // Build shuffled slot list (all except winner) — mirrors Enclosing inward clearing
        List<Integer> slotsToReveal = new ArrayList<>(INV_SIZE - 1);
        for (int i = 0; i < INV_SIZE; i++) {
            if (i != WINNER_SLOT) slotsToReveal.add(i);
        }
        Collections.shuffle(slotsToReveal);

        final int   total  = slotsToReveal.size();
        final int[] cursor = {0};

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!session.isRunning()) return;

            if (cursor[0] >= total) {
                finish(session, winner, inv);
                return;
            }

            // Clear one slot per tick (mirrors Enclosing single piston per step)
            int slot = slotsToReveal.get(cursor[0]);
            inv.setItem(slot, AnimationUtil.filler());
            cursor[0]++;

            // Always keep winner visible
            inv.setItem(WINNER_SLOT, AnimationUtil.buildDisplayItem(winner, plugin.getHookManager()));

            double progress = (double) cursor[0] / total;
            AnimationUtil.playTickSound(session.getPlayer(), progress, session.getCrate().getOpenSound());

        }, 0L, (long) Math.max(1L, 2L / session.getCrate().getGuiAnimationSpeed()));

        session.addTask(task);
    }

    private void finish(CrateSession session, Reward winner, Inventory inv) {
        if (!session.isRunning() || session.isForfeited()) return;
        session.setRunning(false);

        AnimationUtil.fillAll(inv);
        inv.setItem(WINNER_SLOT, AnimationUtil.buildDisplayItem(winner, plugin.getHookManager()));
        AnimationUtil.playWinSound(session.getPlayer(), session.getCrate().getWinSound());

        BukkitTask close = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (session.isForfeited()) return;
            session.getPlayer().closeInventory();
            if (plugin.getAnimationManager().completeSession(session)) {
                plugin.getCrateManager().deliverRewardPublic(session.getPlayer(), session.getResult());
            }
        }, 40L);
        session.addTask(close);
    }

    @Override public void cancel(CrateSession session)       { session.cancelAllTasks(); }
    @Override public boolean isRunning(CrateSession session) { return session.isRunning(); }
    private String colorize(String s)                        { return s.replace("&", "\u00A7"); }
}