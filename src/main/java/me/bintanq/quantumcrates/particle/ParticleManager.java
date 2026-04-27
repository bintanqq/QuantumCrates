package me.bintanq.quantumcrates.particle;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.model.Crate;
import me.bintanq.quantumcrates.util.Logger;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

/**
 * ParticleManager — drives both idle ambient and opening burst particle effects.
 *
 * Idle particles run on a repeating task (configurable interval).
 * Opening particles are fired once per crate-open event.
 *
 * All particle names correspond to Bukkit {@link Particle} enum constants.
 */
public class ParticleManager {

    private final QuantumCrates plugin;

    /** crateId → running BukkitTask for idle particles */
    private final Map<String, BukkitTask> idleTasks = new HashMap<>();

    public ParticleManager(QuantumCrates plugin) {
        this.plugin = plugin;
    }

    /* ─────────────────────── Idle Particles ─────────────────────── */

    /**
     * Starts the idle particle loop for a crate.
     * Safe to call multiple times — stops existing task first.
     */
    public void startIdleParticles(Crate crate) {
        stopIdleParticles(crate.getId());
        if (crate.getLocation() == null) return;

        Location loc = toLocation(crate.getLocation());
        if (loc == null) return;

        Particle particle = parseParticle(crate.getIdleParticle(), Particle.HAPPY_VILLAGER);
        long interval = plugin.getConfig().getLong("particles.idle-interval", 5L);

        BukkitTask task = new BukkitRunnable() {
            double angle = 0;

            @Override
            public void run() {
                if (loc.getWorld() == null) {
                    cancel();
                    return;
                }
                // Rotating ring of particles around crate
                for (int i = 0; i < 8; i++) {
                    double theta = angle + (Math.PI * 2 / 8 * i);
                    double x = loc.getX() + 0.5 + Math.cos(theta) * 0.8;
                    double z = loc.getZ() + 0.5 + Math.sin(theta) * 0.8;
                    double y = loc.getY() + 1.1;
                    loc.getWorld().spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0);
                }
                angle += 0.2;
            }
        }.runTaskTimer(plugin, 0L, interval);

        idleTasks.put(crate.getId(), task);
    }

    /**
     * Stops idle particles for a crate.
     */
    public void stopIdleParticles(String crateId) {
        BukkitTask task = idleTasks.remove(crateId);
        if (task != null) task.cancel();
    }

    /**
     * Starts idle particles for ALL loaded crates.
     */
    public void startAll() {
        plugin.getCrateManager().getAllCrates().forEach(crate -> {
            if (crate.getLocation() != null && crate.isEnabled()) {
                startIdleParticles(crate);
            }
        });
    }

    /**
     * Stops ALL running idle particle tasks.
     */
    public void stopAll() {
        idleTasks.values().forEach(BukkitTask::cancel);
        idleTasks.clear();
    }

    /* ─────────────────────── Opening Burst ─────────────────────── */

    /**
     * Fires a one-shot burst of particles when a crate is opened.
     * Called by CrateManager right after a successful open.
     */
    public void playOpenEffect(Crate crate, Location playerLocation) {
        Particle particle = parseParticle(crate.getOpenParticle(), Particle.FIREWORK);
        World world = playerLocation.getWorld();
        if (world == null) return;

        // Burst: 3 rings expanding upward over 10 ticks
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= 10) { cancel(); return; }
                double radius = 0.3 + tick * 0.15;
                double y      = playerLocation.getY() + 1.0 + tick * 0.1;
                for (int i = 0; i < 16; i++) {
                    double theta = Math.PI * 2 / 16 * i;
                    double x = playerLocation.getX() + Math.cos(theta) * radius;
                    double z = playerLocation.getZ() + Math.sin(theta) * radius;
                    world.spawnParticle(particle, x, y, z, 2, 0.05, 0.05, 0.05, 0.02);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /* ─────────────────────── Helpers ─────────────────────── */

    private Particle parseParticle(String name, Particle fallback) {
        if (name == null || name.isEmpty()) return fallback;
        try {
            return Particle.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            Logger.warn("Unknown particle: '" + name + "', using fallback.");
            return fallback;
        }
    }

    private Location toLocation(Crate.SerializableLocation sl) {
        World world = plugin.getServer().getWorld(sl.world);
        if (world == null) return null;
        return new Location(world, sl.x + 0.5, sl.y, sl.z + 0.5);
    }
}
