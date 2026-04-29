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

    private static final int CENTER_SLOT  = 22;
    private static final int TOTAL_CYCLES = 30;

    private final QuantumCrates plugin;

    public ShufflerAnimation(QuantumCrates plugin) {
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
        AnimationUtil.fillAll(inv, Material.GRAY_STAINED_GLASS_PANE);

        // Highlight ring around center
        for (int s : new int[]{12, 13, 14, 21, 23, 30, 31, 32}) {
            inv.setItem(s, AnimationUtil.filler(Material.BLACK_STAINED_GLASS_PANE));
        }

        session.getPlayer().openInventory(inv);

        final int[] cycle = {0};

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!session.isRunning()) return;

            double progress = (double) cycle[0] / TOTAL_CYCLES;
            int period = (int) Math.max(1, progress * progress * 8);

            if (cycle[0] % Math.max(1, period) == 0) {
                Reward shown = cycle[0] >= TOTAL_CYCLES - 1
                        ? winner
                        : AnimationUtil.randomReward(pool);
                inv.setItem(CENTER_SLOT, AnimationUtil.buildDisplayItem(shown));
                AnimationUtil.playTickSound(session.getPlayer(), progress);
            }

            if (cycle[0] >= TOTAL_CYCLES) {
                finish(session, winner, inv);
                return;
            }

            cycle[0]++;
        }, 0L, 1L);

        session.addTask(task);
    }

    private void finish(CrateSession session, Reward winner, Inventory inv) {
        if (!session.isRunning() || session.isForfeited()) return;
        inv.setItem(22, AnimationUtil.buildDisplayItem(winner));
        for (int s : new int[]{12, 13, 14, 21, 23, 30, 31, 32}) {
            inv.setItem(s, AnimationUtil.filler(Material.LIME_STAINED_GLASS_PANE));
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

    @Override public void cancel(CrateSession session) { session.cancelAllTasks(); }
    @Override public boolean isRunning(CrateSession session) { return session.isRunning(); }
    private String colorize(String s) { return s.replace("&", "\u00A7"); }
}