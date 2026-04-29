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

    private static final int[][] COLUMNS = {
            {10, 19, 28, 37}, // left   col 1
            {13, 22, 31, 40}, // center col 4 — winner
            {16, 25, 34, 43}  // right  col 7
    };
    private static final int CENTER_COL  = 1;
    private static final int DISPLAY_ROW = 1; // the "result" row shown to player

    // Per-column stop ticks (in total spins of center column)
    private static final int LEFT_STOP_AT   = 30; // left stops at spin 30
    private static final int RIGHT_STOP_AT  = 40; // right stops at spin 40

    private static final int[][] SPIN_STEPS = {
            {12, 1}, {12, 2}, {12, 3}, {12, 4},
            {5,  6}, {3,  8}, {2, 10}, {1, 12}
    };
    private static final int TOTAL_SPINS = computeTotalSpins();

    private final QuantumCrates plugin;

    public TripleSpinAnimation(QuantumCrates plugin) {
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

        // Separator columns
        for (int row = 0; row < 6; row++) {
            safeSet(inv, row * 9 + 2,  AnimationUtil.filler(Material.GRAY_STAINED_GLASS_PANE));
            safeSet(inv, row * 9 + 6,  AnimationUtil.filler(Material.GRAY_STAINED_GLASS_PANE));
        }

        // Per-column strips (4 rows each)
        Reward[][] strips  = new Reward[3][4];
        boolean[]  stopped = {false, false, false};

        for (int c = 0; c < 3; c++) {
            for (int r = 0; r < 4; r++) {
                strips[c][r] = AnimationUtil.randomReward(pool);
            }
            renderColumn(inv, c, strips[c]);
        }

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

            for (int c = 0; c < 3; c++) {
                if (stopped[c]) continue;

                boolean stopNow = (c == 0 && spin >= LEFT_STOP_AT)
                        || (c == 2 && spin >= RIGHT_STOP_AT)
                        || (c == CENTER_COL && spin >= TOTAL_SPINS);

                if (stopNow) {
                    stopped[c] = true;
                    if (c == CENTER_COL) {
                        // Lock winner in display row
                        for (int r = 0; r < 4; r++) {
                            strips[c][r] = (r == DISPLAY_ROW) ? winner : AnimationUtil.randomReward(pool);
                        }
                    }
                    renderColumn(inv, c, strips[c]);
                    if (c == CENTER_COL) {
                        finish(session, winner, inv);
                        return;
                    }
                    continue;
                }

                // Scroll down: shift [0..2]→[1..3], new random at [0]
                System.arraycopy(strips[c], 0, strips[c], 1, 3);
                strips[c][0] = AnimationUtil.randomReward(pool);
                renderColumn(inv, c, strips[c]);
            }

            double progress = (double) spin / TOTAL_SPINS;
            AnimationUtil.playTickSound(session.getPlayer(), progress);

            session.advanceSpin();
            stepSpins[0]++;

            if (stepIdx[0] < SPIN_STEPS.length
                    && stepSpins[0] >= SPIN_STEPS[stepIdx[0]][0]) {
                stepIdx[0]++;
                stepSpins[0] = 0;
                if (stepIdx[0] < SPIN_STEPS.length)
                    session.setTickInterval(SPIN_STEPS[stepIdx[0]][1]);
            }

            session.advanceTick();
        }, 0L, 1L);

        session.addTask(task);
    }

    /** Render column with isOutOfBounds safety — mirrors AbstractSpinner. */
    private void renderColumn(Inventory inv, int colIdx, Reward[] strip) {
        int[] slots = COLUMNS[colIdx];
        for (int r = 0; r < slots.length; r++) {
            safeSet(inv, slots[r], AnimationUtil.buildDisplayItem(strip[r]));
        }
    }

    /** Mirror AbstractSpinner.isOutOfBounds. */
    private static boolean isOutOfBounds(Inventory inv, int slot) {
        return slot < 0 || slot >= inv.getSize();
    }

    private static void safeSet(Inventory inv, int slot, org.bukkit.inventory.ItemStack item) {
        if (!isOutOfBounds(inv, slot)) inv.setItem(slot, item);
    }

    private void finish(CrateSession session, Reward winner, Inventory inv) {
        if (!session.isRunning() || session.isForfeited()) return;
        session.setRunning(false);

        int[] centerSlots = COLUMNS[CENTER_COL];
        for (int r = 0; r < centerSlots.length; r++) {
            safeSet(inv, centerSlots[r],
                    r == DISPLAY_ROW
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