package me.bintanq.quantumcrates.hologram;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.model.Crate;
import me.bintanq.quantumcrates.util.Logger;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;

/**
 * HologramManager — creates, updates, and removes holograms above crate blocks.
 *
 * Routes to DecentHolograms API if available, falls back to packet-based
 * implementation otherwise (no hard dependency required).
 *
 * Provider is configured in config.yml under holograms.provider.
 */
public class HologramManager {

    private final QuantumCrates plugin;
    private final HologramProvider provider;

    /** crateId → active hologram handle */
    private final Map<String, Object> hologramHandles = new HashMap<>();

    public HologramManager(QuantumCrates plugin) {
        this.plugin = plugin;

        String providerName = plugin.getConfig().getString("holograms.provider", "decentholograms");
        HologramProvider resolved;
        if ("decentholograms".equalsIgnoreCase(providerName)
                && plugin.getServer().getPluginManager().getPlugin("DecentHolograms") != null) {
            resolved = new DecentHologramsProvider(plugin);
            Logger.info("Hologram provider: &bDecentHolograms");
        } else {
            resolved = new PacketHologramProvider(plugin);
            Logger.info("Hologram provider: &bPacket (built-in)");
        }
        this.provider = resolved;
    }

    /**
     * Spawns or updates the hologram for the given crate at its configured location.
     */
    public void spawnHologram(Crate crate) {
        if (crate.getLocation() == null) return;
        Location loc = toLocation(crate.getLocation());
        if (loc == null) return;

        // Remove existing if present
        removeHologram(crate.getId());

        Object handle = provider.createHologram(crate.getId(), loc, crate.getHologramLines());
        if (handle != null) hologramHandles.put(crate.getId(), handle);
    }

    /**
     * Removes the hologram for the given crateId.
     */
    public void removeHologram(String crateId) {
        Object handle = hologramHandles.remove(crateId);
        if (handle != null) provider.deleteHologram(handle);
    }

    /**
     * Updates the text lines of a hologram without recreating it (reduces flicker).
     */
    public void updateHologram(Crate crate) {
        Object handle = hologramHandles.get(crate.getId());
        if (handle == null) {
            spawnHologram(crate);
            return;
        }
        provider.updateLines(handle, crate.getHologramLines());
    }

    /**
     * Removes ALL active holograms. Called on plugin disable.
     */
    public void removeAll() {
        for (Map.Entry<String, Object> entry : hologramHandles.entrySet()) {
            provider.deleteHologram(entry.getValue());
        }
        hologramHandles.clear();
    }

    /**
     * Spawns holograms for all loaded crates that have a location configured.
     */
    public void spawnAll() {
        plugin.getCrateManager().getAllCrates().forEach(crate -> {
            if (crate.getLocation() != null && !crate.getHologramLines().isEmpty()) {
                spawnHologram(crate);
            }
        });
        Logger.info("Spawned &e" + hologramHandles.size() + " &fholograms.");
    }

    /* ─────────────────────── Helper ─────────────────────── */

    private Location toLocation(Crate.SerializableLocation sl) {
        World world = plugin.getServer().getWorld(sl.world);
        if (world == null) {
            Logger.warn("Hologram world not found: " + sl.world);
            return null;
        }
        return new Location(world, sl.x, sl.y + 2.5, sl.z, sl.yaw, sl.pitch);
    }
}
