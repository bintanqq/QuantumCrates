package me.bintanq.quantumcrates.animation;

import me.bintanq.quantumcrates.model.Crate;
import me.bintanq.quantumcrates.model.reward.RewardResult;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public class CrateSession {

    private final Player      player;
    private final Crate       crate;
    private final RewardResult result;

    private Inventory            inventory;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private volatile boolean running   = false;
    private volatile boolean forfeited = false;

    // SpinStep state — mirrors ExcellentCrates AbstractSpinner
    private long spinCount    = 0;
    private long tickCount    = 0;
    private long tickInterval = 1;

    public CrateSession(Player player, Crate crate, RewardResult result) {
        this.player = player;
        this.crate  = crate;
        this.result = result;
    }

    /* ── SpinStep helpers ── */

    /** Returns true when it's time to perform a spin (mirrors isSpinTime). */
    public boolean isSpinTime() {
        return tickCount == 0 || tickCount % tickInterval == 0L;
    }

    public void advanceTick()  { tickCount = Math.max(0L, tickCount + 1L); }
    public void advanceSpin()  { spinCount++; }
    public long getSpinCount() { return spinCount; }
    public long getTickCount() { return tickCount; }

    public void setTickInterval(long interval) { this.tickInterval = Math.max(1L, interval); }
    public long getTickInterval()              { return tickInterval; }

    /* ── Standard ── */

    public void addTask(BukkitTask task) { tasks.add(task); }

    public void cancelAllTasks() {
        tasks.forEach(t -> { try { t.cancel(); } catch (Exception ignored) {} });
        tasks.clear();
    }

    public Player      getPlayer()       { return player; }
    public Crate       getCrate()        { return crate; }
    public RewardResult getResult()      { return result; }
    public Inventory   getInventory()    { return inventory; }
    public void        setInventory(Inventory inv) { this.inventory = inv; }
    public boolean     isRunning()       { return running; }
    public void        setRunning(boolean v)  { this.running = v; }
    public boolean     isForfeited()     { return forfeited; }
    public void        setForfeited(boolean v) { this.forfeited = v; }
}