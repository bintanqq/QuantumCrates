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

public class SingleSpinAnimation implements CrateAnimation {

    private static final int[][] SPIN_STEPS = {
            {12, 1}, {12, 2}, {12, 3}, {12, 4},
            {5,  6}, {3,  8}, {2, 10}, {1, 12}
    };
    private static final int TOTAL_SPINS = computeTotalSpins();

    private final QuantumCrates plugin;

    public SingleSpinAnimation(QuantumCrates plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start(CrateSession session) {
        List<Reward> pool   = session.getCrate().getRewards();
        Reward       winner = session.getResult().getReward();

        // 45-slot inventory: 5 rows, center column is col index 4 (slots 4,13,22,31,40)
        int[] slots = {4, 13, 22, 31, 40};
        int displayRow = 2; // middle of 5

        Inventory inv = Bukkit.createInventory(null, 45,
                "\u00A70\u00A7l" + colorize(session.getCrate().getDisplayName() != null
                        ? session.getCrate().getDisplayName() : session.getCrate().getId()));
        session.setInventory(inv);
        AnimationUtil.fillAll(inv);

        // Fill non-column slots with gray filler
        for (int i = 0; i < 45; i++) {
            boolean inColumn = false;
            for (int s : slots) if (s == i) { inColumn = true; break; }
            if (!inColumn) inv.setItem(i, AnimationUtil.filler(Material.GRAY_STAINED_GLASS_PANE));
        }

        Reward[] strip = new Reward[5];
        for (int r = 0; r < 5; r++) strip[r] = AnimationUtil.randomReward(pool);

        session.getPlayer().openInventory(inv);
        session.setTickInterval(SPIN_STEPS[0][1]);

        final int[] stepIdx   = {0};
        final int[] stepSpins = {0};

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!session.isRunning()) return;

            if (!session.isSpinTime()) {
                session.advanceTick();
                return;
            }

            long spin = session.getSpinCount();
            boolean last = spin >= TOTAL_SPINS - 1;

            // Scroll down
            System.arraycopy(strip, 0, strip, 1, 4);
            strip[0] = last ? winner : AnimationUtil.randomReward(pool);

            for (int r = 0; r < 5; r++) {
                inv.setItem(slots[r], AnimationUtil.buildDisplayItem(strip[r]));
            }

            double progress = (double) spin / TOTAL_SPINS;
            AnimationUtil.playTickSound(session.getPlayer(), progress);

            session.advanceSpin();
            stepSpins[0]++;

            if (stepIdx[0] < SPIN_STEPS.length && stepSpins[0] >= SPIN_STEPS[stepIdx[0]][0]) {
                stepIdx[0]++;
                stepSpins[0] = 0;
                if (stepIdx[0] < SPIN_STEPS.length)
                    session.setTickInterval(SPIN_STEPS[stepIdx[0]][1]);
            }
            session.advanceTick();

            if (session.getSpinCount() >= TOTAL_SPINS) {
                finish(session, winner, inv, slots, displayRow);
            }
        }, 0L, 1L);

        session.addTask(task);
    }

    private void finish(CrateSession session, Reward winner, Inventory inv, int[] slots, int displayRow) {
        if (!session.isRunning() || session.isForfeited()) return;
        session.setRunning(false);

        for (int r = 0; r < slots.length; r++) {
            inv.setItem(slots[r], r == displayRow
                    ? AnimationUtil.buildDisplayItem(winner)
                    : AnimationUtil.filler(Material.LIME_STAINED_GLASS_PANE));
        }
        AnimationUtil.playWinSound(session.getPlayer());

        BukkitTask close = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (session.isForfeited()) return;
            session.getPlayer().closeInventory();
            plugin.getAnimationManager().completeSession(session);
            plugin.getCrateManager().deliverRewardPublic(session.getPlayer(), session.getResult());
        }, 40L);
        session.addTask(close);
    }

    private static int computeTotalSpins() {
        int t = 0; for (int[] s : SPIN_STEPS) t += s[0]; return t;
    }

    @Override public void cancel(CrateSession session)       { session.cancelAllTasks(); }
    @Override public boolean isRunning(CrateSession session) { return session.isRunning(); }
    private String colorize(String s)                        { return s.replace("&", "\u00A7"); }
}