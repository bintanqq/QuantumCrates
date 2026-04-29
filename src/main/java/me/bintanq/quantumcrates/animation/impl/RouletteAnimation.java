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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class RouletteAnimation implements CrateAnimation {

    // 27-slot middle row strip (slots 9-17), winner at center (slot 13)
    private static final int[] STRIP_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17};
    private static final int   WINNER_SLOT = 13;
    private static final int   STRIP_LEN   = STRIP_SLOTS.length;
    private static final int   INV_SIZE    = 27;

    // SpinStep: mirrors ExcellentCrates CSGO { spinsAmount, tickInterval }
    private static final int[][] SPIN_STEPS = {
            {12, 1}, {12, 2}, {12, 3}, {12, 4},
            {5,  6}, {3,  8}, {2, 10}, {1, 12}
    };

    private static final int TOTAL_SPINS = computeTotalSpins();

    private final QuantumCrates plugin;

    public RouletteAnimation(QuantumCrates plugin) {
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
        AnimationUtil.fillAll(inv);

        // Arrow indicators (like ExcellentCrates CSGO arrow_up/arrow_down)
        inv.setItem(4,  AnimationUtil.filler(Material.YELLOW_STAINED_GLASS_PANE));
        inv.setItem(22, AnimationUtil.filler(Material.YELLOW_STAINED_GLASS_PANE));

        // Pre-build scroll queue: winner placed at position such that it lands on WINNER_SLOT
        Deque<Reward> queue = buildScrollQueue(pool, winner);

        // Prime the initial strip
        Reward[] strip = new Reward[STRIP_LEN];
        for (int i = 0; i < STRIP_LEN; i++) {
            strip[i] = queue.isEmpty() ? AnimationUtil.randomReward(pool) : queue.poll();
        }

        session.getPlayer().openInventory(inv);

        // SpinStep cursor
        final int[]  stepIdx   = {0};
        final int[]  stepSpins = {0};

        // Initialise session tick interval to first step
        session.setTickInterval(SPIN_STEPS[0][1]);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!session.isRunning()) return;

            if (!session.isSpinTime()) {
                session.advanceTick();
                return;
            }

            System.arraycopy(strip, 1, strip, 0, STRIP_LEN - 1);
            strip[STRIP_LEN - 1] = queue.isEmpty() ? AnimationUtil.randomReward(pool) : queue.poll();
            for (int i = 0; i < STRIP_LEN; i++) {
                inv.setItem(STRIP_SLOTS[i], AnimationUtil.buildDisplayItem(strip[i]));
            }

            double progress = (double) session.getSpinCount() / TOTAL_SPINS;
            AnimationUtil.playTickSound(session.getPlayer(), progress);

            session.advanceSpin();
            stepSpins[0]++;

            if (stepIdx[0] < SPIN_STEPS.length
                    && stepSpins[0] >= SPIN_STEPS[stepIdx[0]][0]) {
                stepIdx[0]++;
                stepSpins[0] = 0;
                if (stepIdx[0] < SPIN_STEPS.length) {
                    session.setTickInterval(SPIN_STEPS[stepIdx[0]][1]);
                }
            }

            session.advanceTick();

            if (session.getSpinCount() >= TOTAL_SPINS) {
                finish(session, winner, inv);
            }

        }, 0L, 1L);

        session.addTask(task);
    }

    private void finish(CrateSession session, Reward winner, Inventory inv) {
        if (!session.isRunning() || session.isForfeited()) return;
        session.setRunning(false);

        AnimationUtil.fillAll(inv);
        inv.setItem(4,          AnimationUtil.filler(Material.LIME_STAINED_GLASS_PANE));
        inv.setItem(22,         AnimationUtil.filler(Material.LIME_STAINED_GLASS_PANE));
        inv.setItem(WINNER_SLOT, AnimationUtil.buildDisplayItem(winner));
        AnimationUtil.playWinSound(session.getPlayer());

        // Mirrors ExcellentCrates completionPauseTicks (40 ticks) then close & deliver
        BukkitTask close = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (session.isForfeited()) return;
            session.getPlayer().closeInventory();
            plugin.getAnimationManager().completeSession(session);
            // Deliver reward ONLY here (onComplete equivalent)
            plugin.getCrateManager().deliverRewardPublic(session.getPlayer(), session.getResult());
        }, 40L);

        session.addTask(close);
    }

    private Deque<Reward> buildScrollQueue(List<Reward> pool, Reward winner) {
        int winnerOffset = STRIP_LEN - (WINNER_SLOT - STRIP_SLOTS[0]); // = 5
        int queueSize    = TOTAL_SPINS + STRIP_LEN;

        Deque<Reward> queue = new ArrayDeque<>(queueSize);
        for (int i = 0; i < queueSize - winnerOffset; i++) {
            queue.add(AnimationUtil.randomReward(pool));
        }
        queue.add(winner);
        for (int i = 0; i < winnerOffset - 1; i++) {
            queue.add(AnimationUtil.randomReward(pool));
        }
        return queue;
    }

    private static int computeTotalSpins() {
        int total = 0;
        for (int[] step : SPIN_STEPS) total += step[0];
        return total;
    }

    @Override public void cancel(CrateSession session)       { session.cancelAllTasks(); }
    @Override public boolean isRunning(CrateSession session) { return session.isRunning(); }
    private String colorize(String s)                        { return s.replace("&", "\u00A7"); }
}