package me.bintanq.quantumcrates.hologram;

import org.bukkit.Location;

import java.util.List;

/**
 * HologramProvider — strategy interface for hologram backends.
 * Implementations: DecentHologramsProvider, PacketHologramProvider.
 */
public interface HologramProvider {

    /**
     * Creates a hologram at the given location with the given lines.
     *
     * @return an opaque handle object (cast inside the impl); null on failure
     */
    Object createHologram(String id, Location location, List<String> lines);

    /**
     * Updates the text lines of an existing hologram.
     *
     * @param handle the handle returned by {@link #createHologram}
     */
    void updateLines(Object handle, List<String> lines);

    /**
     * Permanently removes a hologram.
     *
     * @param handle the handle returned by {@link #createHologram}
     */
    void deleteHologram(Object handle);
}
