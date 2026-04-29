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

    // 28 border slots of a 54-slot inventory, clockwise
    private static final int[] BORDER = buildBorder();
    private static final int   WINNER_BORDER_IDX = 4; // slot index 4 = top row center-ish
    private static final int   TOTAL_STEPS = 56; // ~2 full laps

    private final QuantumCrates plugin;

    public BoundaryAnimation(QuantumCrates plugin) {
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
        // Inner fill
        for (int s : new int[]{10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34}) {
            inv.setItem(s, AnimationUtil.filler(Material.GRAY_STAINED_GLASS_PANE));
        }
        // Winner display slot in center
        inv.setItem(22, AnimationUtil.filler(Material.GRAY_STAINED_GLASS_PANE));

        session.getPlayer().openInventory(inv);

        final int[] step = {0};
        final int[] headPos = {0}; // index into BORDER

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!session.isRunning()) return;

            double progress = (double) step[0] / TOTAL_STEPS;
            int period = (int) Math.max(1, Math.pow(2, progress * 2.5));

            if (step[0] % period == 0) {
                // Clear previous head
                inv.setItem(BORDER[headPos[0]], AnimationUtil.filler(Material.BLACK_STAINED_GLASS_PANE));

                headPos[0] = (headPos[0] + 1) % BORDER.length;
                Reward shown = AnimationUtil.randomReward(pool);
                inv.setItem(BORDER[headPos[0]], AnimationUtil.buildDisplayItem(shown));
                AnimationUtil.playTickSound(session.getPlayer(), progress);
            }

            if (step[0] >= TOTAL_STEPS) {
                finish(session, winner, inv, headPos[0]);
                return;
            }
            step[0]++;
        }, 0L, 1L);

        session.addTask(task);
    }

    private void finish(CrateSession session, Reward winner, Inventory inv, int lastPos) {
        if (!session.isRunning() || session.isForfeited()) return;

        // Clear border, show winner in center
        for (int s : BORDER) inv.setItem(s, AnimationUtil.filler(Material.LIME_STAINED_GLASS_PANE));
        inv.setItem(22, AnimationUtil.buildDisplayItem(winner));
        AnimationUtil.playWinSound(session.getPlayer());

        BukkitTask close = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (session.isForfeited()) return;
            session.getPlayer().closeInventory();
            plugin.getAnimationManager().completeSession(session);
            plugin.getCrateManager().deliverRewardPublic(session.getPlayer(), session.getResult());
        }, 40L);
        session.addTask(close);
    }

    private static int[] buildBorder() {
        // Top row: 0-8, right col: 17,26,35,44, bottom row: 53-45, left col: 36,27,18,9
        int[] b = new int[28];
        int idx = 0;
        for (int i = 0; i <= 8;  i++) b[idx++] = i;
        for (int i = 17; i <= 44; i += 9) b[idx++] = i;
        for (int i = 53; i >= 45; i--) b[idx++] = i;
        for (int i = 36; i >= 9;  i -= 9) b[idx++] = i;
        return b;
    }

    @Override public void cancel(CrateSession session) { session.cancelAllTasks(); }
    @Override public boolean isRunning(CrateSession session) { return session.isRunning(); }
    private String colorize(String s) { return s.replace("&", "\u00A7"); }
}