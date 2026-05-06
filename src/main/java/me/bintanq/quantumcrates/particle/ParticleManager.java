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

public class ParticleManager {

    private final QuantumCrates plugin;

    private final Map<String, BukkitTask> idleTasks   = new HashMap<>();
    private final Map<String, Long>       tickCountMap = new HashMap<>();

    private static final Particle FALLBACK_IDLE = resolveParticleSafe(
            "HAPPY_VILLAGER", "VILLAGER_HAPPY", "COMPOSTER");

    private static final Particle FALLBACK_OPEN = resolveParticleSafe(
            "FIREWORKS", "FIREWORK", "FLAME");

    public ParticleManager(QuantumCrates plugin) {
        this.plugin = plugin;
    }

    /* ─── lifecycle ─────────────────────────────────── */

    public void startIdleParticles(Crate crate) {
        stopIdleParticles(crate.getId());
        if (crate.getLocations().isEmpty()) return;

        // Start one task per location
        for (int i = 0; i < crate.getLocations().size(); i++) {
            Crate.SerializableLocation sl = crate.getLocations().get(i);
            Location base = toLocation(sl);
            if (base == null) continue;

            String key = crate.getId() + "_" + i;
            Crate.AnimationConfig cfg   = crate.getIdleAnimation();
            AnimationType         type  = parseType(cfg.getType());
            if (type == AnimationType.NONE) continue;

            Particle particle    = parseParticle(cfg.getParticle(), FALLBACK_IDLE);
            long     baseTickInterval = getTickInterval(type);
            long     tickInterval = Math.max(1L, (long) (baseTickInterval / crate.getParticleAnimationSpeed()));
            int      maxSteps    = getMaxSteps(type);

            tickCountMap.put(key, 0L);

            BukkitTask task = new BukkitRunnable() {
                @Override public void run() {
                    long tick = tickCountMap.getOrDefault(key, 0L);
                    if (tick < 0 || tick % tickInterval != 0) {
                        tickCountMap.put(key, tick + 1); return;
                    }
                    World w = base.getWorld();
                    if (w == null) { cancel(); return; }
                    playEffect(type, base, particle, (int) tick, w);
                    long next = tick + 1;
                    if (next >= maxSteps) next = -10L;
                    tickCountMap.put(key, next);
                }
            }.runTaskTimer(plugin, 0L, 1L);

            idleTasks.put(key, task);
        }
    }

    public void stopIdleParticles(String crateId) {
        idleTasks.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(crateId + "_") || entry.getKey().equals(crateId)) {
                entry.getValue().cancel();
                tickCountMap.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    public void startAll() {
        plugin.getCrateManager().getAllCrates().forEach(c -> {
            if (c.getLocations() != null && c.isEnabled()) startIdleParticles(c);
        });
    }

    public void stopAll() {
        idleTasks.values().forEach(t -> { try { t.cancel(); } catch (Exception ignored) {} });
        idleTasks.clear();
        tickCountMap.clear();
    }

    /* ─── one-shot open effect ───────────────────────── */

    public void playOpenEffect(Crate crate, Location playerLoc) {
        Crate.AnimationConfig cfg   = crate.getOpenAnimation();
        AnimationType         type  = parseType(cfg.getType());
        if (type == AnimationType.NONE) return;

        Particle particle  = parseParticle(cfg.getParticle(), FALLBACK_OPEN);
        int      maxSteps  = getMaxSteps(type);
        long     baseTickInter = getTickInterval(type);
        long     tickInter = Math.max(1L, (long) (baseTickInter / crate.getParticleAnimationSpeed()));

        Location origin = playerLoc.clone().add(0.5, 0, 0.5);

        new BukkitRunnable() {
            long tick = 0;
            @Override public void run() {
                if (tick >= maxSteps * 3L) { cancel(); return; }
                World w = origin.getWorld();
                if (w == null) { cancel(); return; }
                if (tick % tickInter == 0) {
                    playEffect(type, origin, particle, (int)(tick % maxSteps), w);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /* ─── effect dispatcher ─────────────────────────── */

    private void playEffect(AnimationType type, Location origin, Particle particle,
                            int step, World w) {
        switch (type) {
            case HELIX   -> playHelix  (origin, particle, step, w);
            case SPIRAL  -> playSpiral (origin, particle, step, w);
            case SPHERE  -> playSphere (origin, particle, step, w);
            case BEACON  -> playBeacon (origin, particle, step, w);
            case TORNADO -> playTornado(origin, particle, step, w);
            case VORTEX  -> playVortex (origin, particle, step, w);
            case SIMPLE  -> playSimple (origin, particle, w);
            default      -> playSimple (origin, particle, w);
        }
    }

    private void playHelix(Location origin, Particle particle, int step, World w) {
        Location location = origin.clone().add(0, 0.05D * step, 0);
        double x = Math.PI * step;
        double z = step * 0.1 % 2.5;
        double y = 0.75;
        Location left  = getPointOnCircle(location, true, x,           y, z);
        Location right = getPointOnCircle(location, true, x - Math.PI, y, z);
        w.spawnParticle(particle, left,  1, 0, 0, 0, 0);
        w.spawnParticle(particle, right, 1, 0, 0, 0, 0);
    }

    private static final double SPIRAL_RADIUS = 1.0;
    private static final double SPIRAL_VSPACE = 0.1;
    private static final double SPIRAL_START  = 0.0;
    private static final double SPIRAL_END    = 6 * Math.PI;
    private static final int    SPIRAL_POINTS = 50;

    private void playSpiral(Location origin, Particle particle, int step, World w) {
        double delta = (SPIRAL_END - SPIRAL_START) / SPIRAL_POINTS;
        double angle = SPIRAL_START + step * delta;
        double x = SPIRAL_RADIUS * Math.cos(angle);
        double z = SPIRAL_RADIUS * Math.sin(angle);
        double y = SPIRAL_VSPACE * angle;
        Location loc = origin.clone().add(x, y, z);
        w.spawnParticle(particle, loc, 5, 0, 0, 0, 0);
    }

    private static final int    SPHERE_CIRCLES = 8;
    private static final int    SPHERE_POINTS  = 10;
    private static final double SPHERE_DELTA   = Math.PI / 10.0;

    private void playSphere(Location origin, Particle particle, int step, World w) {
        double angle    = step * SPHERE_DELTA;
        double cosAngle = Math.cos(angle);
        double sinAngle = Math.sin(angle);
        for (int j = 0; j < SPHERE_POINTS; j++) {
            double theta = j * 2.0 * Math.PI / SPHERE_POINTS;
            double x = Math.cos(theta) * cosAngle;
            double y = Math.sin(theta) * cosAngle;
            double z = sinAngle;
            Location loc = origin.clone().add(x, z + 0.2, y);
            w.spawnParticle(particle, loc, 1, 0, 0, 0, 0);
        }
    }

    private void playBeacon(Location origin, Particle particle, int step, World w) {
        double x = 2 * Math.PI / 7D * step;
        for (int yStep = step; yStep > Math.max(0, step - 25); --yStep) {
            Location loc = getPointOnCircle(origin, true, x, 0.55, yStep * 0.75);
            w.spawnParticle(particle, loc, 4, 0, 0.15f, 0, 0);
        }
    }

    private static final double TORNADO_Y_OFFSET  = 0.15D;
    private static final float  TORNADO_HEIGHT     = 3.15F;
    private static final float  TORNADO_MAX_RADIUS = 2.25F;
    private static final double TORNADO_DISTANCE   = 0.375D;

    private void playTornado(Location origin, Particle particle, int step, World w) {
        Location loc    = origin.clone().add(0, 0.5D, 0);
        double offset   = 0.25D * (TORNADO_MAX_RADIUS * (2.35D / TORNADO_HEIGHT));
        double vertical = TORNADO_HEIGHT - TORNADO_DISTANCE * step;
        double radius   = Math.min(TORNADO_MAX_RADIUS, offset * vertical);
        double amount   = radius * 64.0D;
        double d2       = 2 * Math.PI / amount;
        for (int i = 0; i < amount; i++) {
            double d3  = i * d2;
            double cos = radius * Math.cos(d3);
            double sin = radius * Math.sin(d3);
            Location l = loc.clone().add(cos, vertical, sin);
            w.spawnParticle(particle, l, 3, 0.1f, 0, 0, 0);
        }
        loc.subtract(0, TORNADO_Y_OFFSET, 0);
    }

    private static final int   VORTEX_STRANDS   = 2;
    private static final int   VORTEX_PARTICLES = 170 / 5;
    private static final float VORTEX_RADIUS    = 1.5F;
    private static final float VORTEX_CURVE     = 2.0F;
    private static final double VORTEX_ROTATION = Math.PI / 4;

    private void playVortex(Location origin, Particle particle, int step, World w) {
        for (int boost = 0; boost < 3; boost++) {
            for (int strand = 1; strand <= VORTEX_STRANDS; strand++) {
                float  progress = step / (float) VORTEX_PARTICLES;
                double point    = VORTEX_CURVE * progress * 2.0f * Math.PI / VORTEX_STRANDS
                        + 2 * Math.PI * strand / VORTEX_STRANDS + VORTEX_ROTATION;
                double addX = Math.cos(point) * progress * VORTEX_RADIUS;
                double addZ = Math.sin(point) * progress * VORTEX_RADIUS;
                double addY = 3.5D - 0.02 * 5 * step;
                Location loc = origin.clone().add(addX, addY, addZ);
                w.spawnParticle(particle, loc, 1, 0.1f, 0, 0, 0);
            }
        }
    }

    private void playSimple(Location origin, Particle particle, World w) {
        Location loc = origin.clone().add(0, 0.5D, 0);
        w.spawnParticle(particle, loc, 30, 0.3f, 0.1f, 0.3f, 0);
    }

    private static Location getPointOnCircle(Location location, boolean doCopy,
                                             double x, double z, double y) {
        return (doCopy ? location.clone() : location)
                .add(Math.cos(x) * z, y, Math.sin(x) * z);
    }

    /* ─── tick intervals & maxSteps per type ────────── */

    private long getTickInterval(AnimationType type) {
        return switch (type) {
            case HELIX, SPIRAL, SPHERE, VORTEX -> 1L;
            case SIMPLE, TORNADO               -> 2L;
            case BEACON                        -> 3L;
            default                            -> 2L;
        };
    }

    private int getMaxSteps(AnimationType type) {
        return switch (type) {
            case HELIX   -> 24;
            case SPIRAL  -> SPIRAL_POINTS;
            case SPHERE  -> SPHERE_CIRCLES;
            case BEACON  -> 40;
            case TORNADO -> 8;
            case VORTEX  -> VORTEX_PARTICLES;
            case SIMPLE  -> 2;
            default      -> 20;
        };
    }

    /* ─── helpers ─────────────────────────────────── */

    public enum AnimationType {
        HELIX, SPIRAL, SPHERE, BEACON, TORNADO, VORTEX, SIMPLE, NONE
    }

    private AnimationType parseType(String name) {
        if (name == null || name.isEmpty()) return AnimationType.SIMPLE;
        try {
            return AnimationType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            Logger.warn("Unknown animation type: '" + name + "', defaulting to SIMPLE.");
            return AnimationType.SIMPLE;
        }
    }

    private Particle parseParticle(String name, Particle fallback) {
        if (name == null || name.isEmpty()) return fallback;

        try {
            return Particle.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ignored) {}

        String alias = getCrossVersionAlias(name.toUpperCase());
        if (alias != null) {
            try {
                return Particle.valueOf(alias);
            } catch (IllegalArgumentException ignored) {}
        }

        Logger.warn("Unknown particle: '" + name + "', using fallback '" + fallback.name() + "'.");
        return fallback;
    }

    private static String getCrossVersionAlias(String name) {
        return switch (name) {
            case "FIREWORK"           -> "FIREWORKS";
            case "SPELL"              -> "EFFECT";
            case "SPELL_MOB"          -> "ENTITY_EFFECT";
            case "SPELL_MOB_AMBIENT"  -> "AMBIENT_ENTITY_EFFECT";
            case "SPELL_WITCH"        -> "WITCH";
            case "SPELL_INSTANT"      -> "INSTANT_EFFECT";
            case "CRIT_MAGIC"         -> "ENCHANTED_HIT";
            case "FIREWORKS"          -> "FIREWORK";
            case "EFFECT"             -> "SPELL";
            case "ENTITY_EFFECT"      -> "SPELL_MOB";
            case "AMBIENT_ENTITY_EFFECT" -> "SPELL_MOB_AMBIENT";
            case "INSTANT_EFFECT"     -> "SPELL_INSTANT";
            case "ENCHANTED_HIT"      -> "CRIT_MAGIC";
            default                   -> null;
        };
    }

    private static Particle resolveParticleSafe(String... candidates) {
        for (String name : candidates) {
            try { return Particle.valueOf(name); } catch (IllegalArgumentException ignored) {}
        }
        return Particle.FLAME;
    }

    private Location toLocation(Crate.SerializableLocation sl) {
        org.bukkit.World world = plugin.getServer().getWorld(sl.world);
        if (world == null) return null;
        return new Location(world, sl.x + 0.5, sl.y, sl.z + 0.5);
    }
}