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

public class BoundaryAnimation implements CrateAnimation {

    // Clockwise border, 26 slots — mirrors ExcellentCrates roulette slots
    private static final int[] BORDER       = buildBorder();
    private static final int   WINNER_SLOT  = 22; // inner center
    private static final int   BORDER_LEN   = BORDER.length;

    private static final int[][] SPIN_STEPS = {
            {12, 1}, {12, 2}, {12, 3}, {12, 4},
            {5,  6}, {3,  8}, {2, 10}, {1, 12}
    };
    private static final int TOTAL_SPINS = computeTotalSpins();

    private final QuantumCrates plugin;

    public BoundaryAnimation(QuantumCrates plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start(CrateSession session) {
        List<Reward> pool   = session.getCrate().getRewards();
        Reward       winner = session.getResult().getReward();

        Inventory inv = Bukkit.createInventory(null, 54,
                "\u00A70\u00A7l" + colorize(session.getCrate().getDisplayName() != null
                        ? session.getCrate().getDisplayName() : session.getCrate().getId()));
        session.setInventory(inv);
        AnimationUtil.fillAll(inv);

        session.getPlayer().openInventory(inv);
        session.setTickInterval((int) Math.max(1, SPIN_STEPS[0][1] / session.getCrate().getGuiAnimationSpeed()));

        final int[] headPos   = {0};
        final int[] stepIdx   = {0};
        final int[] stepSpins = {0};


        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!session.isRunning()) return;

            if (!session.isSpinTime()) {
                session.advanceTick();
                return;
            }

            // Clear previous head
            inv.setItem(BORDER[headPos[0]], AnimationUtil.filler());

            headPos[0] = (headPos[0] + 1) % BORDER_LEN;

            boolean last = session.getSpinCount() >= TOTAL_SPINS - 1;
            Reward shown = last ? winner : AnimationUtil.randomReward(pool);
            inv.setItem(BORDER[headPos[0]], AnimationUtil.buildDisplayItem(shown, plugin.getHookManager()));

            double progress = (double) session.getSpinCount() / TOTAL_SPINS;
            AnimationUtil.playTickSound(session.getPlayer(), progress, session.getCrate().getOpenSound());

            session.advanceSpin();
            stepSpins[0]++;

            if (stepIdx[0] < SPIN_STEPS.length
                    && stepSpins[0] >= SPIN_STEPS[stepIdx[0]][0]) {
                stepIdx[0]++;
                stepSpins[0] = 0;
                if (stepIdx[0] < SPIN_STEPS.length)
                    session.setTickInterval((int) Math.max(1, SPIN_STEPS[stepIdx[0]][1] / session.getCrate().getGuiAnimationSpeed()));
            }

            session.advanceTick();

            if (session.getSpinCount() >= TOTAL_SPINS) {
                finish(session, winner, inv, headPos[0]);
            }
        }, 0L, 1L);

        session.addTask(task);
    }

    private void finish(CrateSession session, Reward winner, Inventory inv, int lastPos) {
        if (!session.isRunning() || session.isForfeited()) return;
        session.setRunning(false);

        for (int s : BORDER) inv.setItem(s, AnimationUtil.filler(Material.LIME_STAINED_GLASS_PANE));
        inv.setItem(WINNER_SLOT, AnimationUtil.buildDisplayItem(winner, plugin.getHookManager()));
        AnimationUtil.playWinSound(session.getPlayer(), session.getCrate().getWinSound());

        BukkitTask close = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (session.isForfeited()) return;
            session.getPlayer().closeInventory();
            plugin.getAnimationManager().completeSession(session);
            plugin.getCrateManager().deliverRewardPublic(session.getPlayer(), session.getResult());
        }, 40L);
        session.addTask(close);
    }

    private static int[] buildBorder() {
        int[] b = new int[26]; int idx = 0;
        for (int i = 0;  i <= 8;  i++)      b[idx++] = i;
        for (int i = 17; i <= 44; i += 9)   b[idx++] = i;
        for (int i = 53; i >= 45; i--)      b[idx++] = i;
        for (int i = 36; i >= 9;  i -= 9)   b[idx++] = i;
        return b;
    }

    private static int computeTotalSpins() {
        int t = 0; for (int[] s : SPIN_STEPS) t += s[0]; return t;
    }

    @Override public void cancel(CrateSession session)       { session.cancelAllTasks(); }
    @Override public boolean isRunning(CrateSession session) { return session.isRunning(); }
    private String colorize(String s)                        { return s.replace("&", "\u00A7"); }
}