package me.bintanq.quantumcrates.hologram;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.util.Logger;
import org.bukkit.Location;

import java.util.List;

/**
 * DecentHologramsProvider — uses DecentHolograms API for holograms.
 *
 * DecentHolograms handles persistence, visibility, and animation natively.
 * We simply create/update/delete via DHAPI.
 */
public class DecentHologramsProvider implements HologramProvider {

    private final QuantumCrates plugin;

    public DecentHologramsProvider(QuantumCrates plugin) {
        this.plugin = plugin;
    }

    @Override
    public Object createHologram(String id, Location location, List<String> lines) {
        try {
            String holoId = "QC_" + id;
            // Delete if already exists (reload safety)
            if (DHAPI.getHologram(holoId) != null) {
                DHAPI.removeHologram(holoId);
            }
            List<String> colorized = lines.stream()
                    .map(l -> l.replace("&", "\u00A7"))
                    .toList();
            Hologram hologram = DHAPI.createHologram(holoId, location, colorized);
            return hologram;
        } catch (Exception e) {
            Logger.warn("DecentHolograms: Failed to create hologram for " + id + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public void updateLines(Object handle, List<String> lines) {
        if (!(handle instanceof Hologram hologram)) return;
        try {
            List<String> colorized = lines.stream()
                    .map(l -> l.replace("&", "\u00A7"))
                    .toList();
            DHAPI.setHologramLines(hologram, colorized);
        } catch (Exception e) {
            Logger.warn("DecentHolograms: Failed to update hologram: " + e.getMessage());
        }
    }

    @Override
    public void deleteHologram(Object handle) {
        if (!(handle instanceof Hologram hologram)) return;
        try {
            DHAPI.removeHologram(hologram.getName());
        } catch (Exception e) {
            Logger.warn("DecentHolograms: Failed to delete hologram: " + e.getMessage());
        }
    }
}
