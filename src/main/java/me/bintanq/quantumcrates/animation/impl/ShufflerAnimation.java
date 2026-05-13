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

public class ShufflerAnimation implements CrateAnimation {

    private static final int CENTER_SLOT = 13;
    private static final int INV_SIZE    = 27;

    private static final int[][] SPIN_STEPS = {
            {12, 1}, {12, 2}, {12, 3}, {12, 4},
            {5,  6}, {3,  8}, {2, 10}, {1, 12}
    };
    private static final int TOTAL_SPINS = computeTotalSpins();

    private static final int[] RING = {3, 4, 5, 12, 14, 21, 22, 23};

    private final QuantumCrates plugin;

    public ShufflerAnimation(QuantumCrates plugin) {
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
        for (int s : RING) inv.setItem(s, AnimationUtil.filler(Material.YELLOW_STAINED_GLASS_PANE));

        session.getPlayer().openInventory(inv);
        session.setTickInterval((int) Math.max(1, SPIN_STEPS[0][1] / session.getCrate().getGuiAnimationSpeed()));

        final int[] stepIdx   = {0};
        final int[] stepSpins = {0};

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!session.isRunning()) return;

            if (!session.isSpinTime()) {
                session.advanceTick();
                return;
            }

            boolean last = session.getSpinCount() >= TOTAL_SPINS - 1;
            Reward shown = last ? winner : AnimationUtil.randomReward(pool);
            inv.setItem(CENTER_SLOT, AnimationUtil.buildDisplayItem(shown, plugin.getHookManager()));

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
                finish(session, winner, inv);
            }
        }, 0L, 1L);

        session.addTask(task);
    }

    private void finish(CrateSession session, Reward winner, Inventory inv) {
        if (!session.isRunning() || session.isForfeited()) return;
        session.setRunning(false);

        inv.setItem(CENTER_SLOT, AnimationUtil.buildDisplayItem(winner, plugin.getHookManager()));
        for (int s : RING) inv.setItem(s, AnimationUtil.filler(Material.LIME_STAINED_GLASS_PANE));
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

    private static int computeTotalSpins() {
        int t = 0; for (int[] s : SPIN_STEPS) t += s[0]; return t;
    }

    @Override public void cancel(CrateSession session)       { session.cancelAllTasks(); }
    @Override public boolean isRunning(CrateSession session) { return session.isRunning(); }
    private String colorize(String s)                        { return s.replace("&", "\u00A7"); }
}