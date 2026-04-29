package me.bintanq.quantumcrates.hologram;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.model.Crate;
import me.bintanq.quantumcrates.util.Logger;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;

public class HologramManager {

    private final QuantumCrates plugin;
    private final HologramProvider provider;
    private final Map<String, Object> hologramHandles = new HashMap<>();

    public HologramManager(QuantumCrates plugin) {
        this.plugin = plugin;
        String providerName = plugin.getConfig().getString("holograms.provider", "decentholograms");
        if ("decentholograms".equalsIgnoreCase(providerName)
                && plugin.getServer().getPluginManager().getPlugin("DecentHolograms") != null) {
            this.provider = new DecentHologramsProvider(plugin);
            Logger.info("Hologram provider: &aDecentHolograms");
        } else {
            this.provider = new PacketHologramProvider(plugin);
            Logger.info("Hologram provider: &aPacket (built-in)");
        }
    }

    public void spawnHologram(Crate crate) {
        if (crate.getLocation() == null) return;
        Location loc = toLocation(crate.getLocation());
        if (loc == null) return;
        removeHologram(crate.getId());
        Object handle = provider.createHologram(crate.getId(), loc, crate.getHologramLines());
        if (handle != null) hologramHandles.put(crate.getId(), handle);
    }

    public void removeHologram(String crateId) {
        Object handle = hologramHandles.remove(crateId);
        if (handle != null) provider.deleteHologram(handle);
    }

    public void updateHologram(Crate crate) {
        Object handle = hologramHandles.get(crate.getId());
        if (handle == null) { spawnHologram(crate); return; }
        provider.updateLines(handle, crate.getHologramLines());
    }

    public void removeAll() {
        hologramHandles.values().forEach(provider::deleteHologram);
        hologramHandles.clear();
    }

    public void spawnAll() {
        plugin.getCrateManager().getAllCrates().stream()
                .filter(c -> c.getLocation() != null && !c.getHologramLines().isEmpty())
                .forEach(this::spawnHologram);
        Logger.info("Spawned &e" + hologramHandles.size() + " &fholograms.");
    }

    private Location toLocation(Crate.SerializableLocation sl) {
        World world = plugin.getServer().getWorld(sl.world);
        if (world == null) { Logger.warn("Hologram world not found: " + sl.world); return null; }
        return new Location(world, sl.x + 0.5, sl.y + 2.5, sl.z + 0.5, sl.yaw, sl.pitch);
    }
}