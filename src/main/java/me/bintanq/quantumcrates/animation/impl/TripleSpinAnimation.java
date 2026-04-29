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

public class TripleSpinAnimation implements CrateAnimation {

    // 4 visible rows per column (rows 1-4, skip row 0 and 5)
    private static final int[][] COLUMNS = {
            {10, 19, 28, 37}, // left   (col 1)
            {13, 22, 31, 40}, // center (col 3) — winner
            {16, 25, 34, 43}  // right  (col 5)
    };
    private static final int CENTER_COL    = 1;
    private static final int DISPLAY_ROW   = 1; // row index in each column shown as "result"
    private static final int TOTAL_TICKS   = 80;
    private static final int LEFT_STOP     = 50;
    private static final int RIGHT_STOP    = 60;
    // Center stops at TOTAL_TICKS

    private final QuantumCrates plugin;

    public TripleSpinAnimation(QuantumCrates plugin) {
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

        // Separator columns
        for (int row = 0; row < 6; row++) {
            inv.setItem(row * 9 + 11, AnimationUtil.filler(Material.GRAY_STAINED_GLASS_PANE));
            inv.setItem(row * 9 + 14, AnimationUtil.filler(Material.GRAY_STAINED_GLASS_PANE));
        }

        session.getPlayer().openInventory(inv);

        // Per-column state
        Reward[][] strips = new Reward[3][4];
        for (int c = 0; c < 3; c++)
            for (int r = 0; r < 4; r++)
                strips[c][r] = AnimationUtil.randomReward(pool);

        boolean[] stopped = {false, false, false};
        final int[] tick = {0};

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!session.isRunning()) return;

            double progress = (double) tick[0] / TOTAL_TICKS;
            int period = (int) Math.max(1, Math.pow(2, progress * 3));

            for (int c = 0; c < 3; c++) {
                if (stopped[c]) continue;

                boolean stopNow = (c == 0 && tick[0] >= LEFT_STOP)
                        || (c == 2 && tick[0] >= RIGHT_STOP)
                        || (c == CENTER_COL && tick[0] >= TOTAL_TICKS);

                if (stopNow) {
                    stopped[c] = true;
                    // Lock center column on winner
                    Reward locked = (c == CENTER_COL) ? winner : strips[c][DISPLAY_ROW];
                    for (int r = 0; r < COLUMNS[c].length; r++) {
                        inv.setItem(COLUMNS[c][r], AnimationUtil.buildDisplayItem(
                                r == DISPLAY_ROW ? locked : AnimationUtil.randomReward(pool)));
                    }
                    if (c == CENTER_COL) {
                        finish(session, winner, inv);
                    }
                    continue;
                }

                if (tick[0] % period == 0) {
                    // Scroll column downward
                    Reward last = strips[c][3];
                    System.arraycopy(strips[c], 0, strips[c], 1, 3);
                    strips[c][0] = AnimationUtil.randomReward(pool);

                    for (int r = 0; r < COLUMNS[c].length; r++) {
                        inv.setItem(COLUMNS[c][r], AnimationUtil.buildDisplayItem(strips[c][r]));
                    }
                }
            }

            AnimationUtil.playTickSound(session.getPlayer(), progress);
            tick[0]++;
        }, 0L, 1L);

        session.addTask(task);
    }

    private void finish(CrateSession session, Reward winner, Inventory inv) {
        if (!session.isRunning() || session.isForfeited()) return;
        // Highlight center column
        for (int slot : COLUMNS[CENTER_COL]) {
            if (inv.getItem(slot) != null) continue; // keep winner item
        }
        inv.setItem(COLUMNS[CENTER_COL][DISPLAY_ROW], AnimationUtil.buildDisplayItem(winner));
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