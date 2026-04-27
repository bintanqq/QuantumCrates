package me.bintanq.quantumcrates.web;

import me.bintanq.quantumcrates.QuantumCrates;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicLong;

/**
 * StatsScheduler — push server stats ke semua WS clients setiap 30 detik.
 *
 * Menggunakan Bukkit scheduler (main thread) untuk baca TPS dan online players,
 * lalu dispatch ke WebSocketBridge (thread-safe).
 */
public class StatsScheduler {

    private final QuantumCrates plugin;
    private BukkitTask task;
    private final AtomicLong openingsToday = new AtomicLong(0);

    public StatsScheduler(QuantumCrates plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // Setiap 30 detik (600 ticks)
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            double tps = Bukkit.getTPS()[0];
            int online  = Bukkit.getOnlinePlayers().size();

            WebSocketBridge.getInstance().broadcastServerStats(online, tps, openingsToday.get());
        }, 600L, 600L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    /** Dipanggil dari CrateManager setiap kali ada opening berhasil */
    public void incrementOpenings() { openingsToday.incrementAndGet(); }

    /** Reset counter setiap tengah malam (bisa dipanggil via scheduler external) */
    public void resetDailyCounter() { openingsToday.set(0); }
}
