package me.bintanq.quantumcrates.hologram;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.util.Logger;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

/**
 * PacketHologramProvider — zero-dependency hologram using invisible ArmorStands.
 *
 * Each line = one invisible ArmorStand with custom name visible.
 * Lines are stacked vertically 0.25 blocks apart.
 *
 * Note: For a production server, packet-level entities (via ProtocolLib or
 * PacketEvents) are preferred to avoid real entities being saved to the world.
 * This implementation uses real ArmorStands for simplicity and compatibility.
 * Remove them cleanly on plugin disable / chunk unload.
 */
public class PacketHologramProvider implements HologramProvider {

    private static final double LINE_GAP = 0.25;

    private final QuantumCrates plugin;

    public PacketHologramProvider(QuantumCrates plugin) {
        this.plugin = plugin;
    }

    @Override
    public Object createHologram(String id, Location location, List<String> lines) {
        List<ArmorStand> stands = new ArrayList<>();
        Location current = location.clone();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).replace("&", "\u00A7");
            ArmorStand stand = spawnStand(current, line);
            stands.add(stand);
            current = current.clone().subtract(0, LINE_GAP, 0);
        }
        return stands;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void updateLines(Object handle, List<String> newLines) {
        if (!(handle instanceof List<?> rawList)) return;
        List<ArmorStand> stands = (List<ArmorStand>) rawList;

        for (int i = 0; i < stands.size() && i < newLines.size(); i++) {
            ArmorStand stand = stands.get(i);
            if (stand.isValid()) {
                stand.setCustomName(newLines.get(i).replace("&", "\u00A7"));
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void deleteHologram(Object handle) {
        if (!(handle instanceof List<?> rawList)) return;
        List<ArmorStand> stands = (List<ArmorStand>) rawList;
        stands.forEach(s -> {
            if (s.isValid()) s.remove();
        });
        stands.clear();
    }

    /* ─────────────────────── Helper ─────────────────────── */

    private ArmorStand spawnStand(Location location, String text) {
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setCanPickupItems(false);
        stand.setCustomName(text);
        stand.setCustomNameVisible(true);
        stand.setSmall(true);
        stand.setArms(false);
        stand.setBasePlate(false);
        stand.setPersistent(false); // don't save to world on restart
        stand.setInvulnerable(true);
        return stand;
    }
}
