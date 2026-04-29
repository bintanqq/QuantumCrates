package me.bintanq.quantumcrates.animation;

import me.bintanq.quantumcrates.model.Crate;
import me.bintanq.quantumcrates.model.reward.RewardResult;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public class CrateSession {

    private final Player player;
    private final Crate crate;
    private final RewardResult result;
    private Inventory inventory;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private volatile boolean running = false;
    private volatile boolean forfeited = false;

    public CrateSession(Player player, Crate crate, RewardResult result) {
        this.player = player;
        this.crate = crate;
        this.result = result;
    }

    public void addTask(BukkitTask task) { tasks.add(task); }

    public void cancelAllTasks() {
        tasks.forEach(t -> { try { t.cancel(); } catch (Exception ignored) {} });
        tasks.clear();
    }

    public Player getPlayer()       { return player; }
    public Crate getCrate()         { return crate; }
    public RewardResult getResult() { return result; }
    public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inv) { this.inventory = inv; }
    public boolean isRunning()      { return running; }
    public void setRunning(boolean running) { this.running = running; }
    public boolean isForfeited()    { return forfeited; }
    public void setForfeited(boolean forfeited) { this.forfeited = forfeited; }
}