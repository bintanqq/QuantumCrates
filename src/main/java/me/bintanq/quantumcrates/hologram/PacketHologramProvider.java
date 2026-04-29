package me.bintanq.quantumcrates.hologram;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.util.Logger;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

public class PacketHologramProvider implements HologramProvider {

    private static final double LINE_GAP    = 0.28;
    private static final double BASE_OFFSET = 2.5; // height above block

    private final QuantumCrates plugin;

    public PacketHologramProvider(QuantumCrates plugin) {
        this.plugin = plugin;
    }

    @Override
    public Object createHologram(String id, Location location, List<String> lines) {
        List<ArmorStand> stands  = new ArrayList<>();
        // Center on block: +0.5 X/Z, base offset Y
        Location base = location.clone().add(0.0, 0.0, 0.0); // already offset by HologramManager
        Location current = base.clone();

        for (String line : lines) {
            String colored = ChatColor.translateAlternateColorCodes('&', line);
            stands.add(spawnStand(current, colored));
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
                stand.setCustomName(
                        ChatColor.translateAlternateColorCodes('&', newLines.get(i)));
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void deleteHologram(Object handle) {
        if (!(handle instanceof List<?> rawList)) return;
        List<ArmorStand> stands = (List<ArmorStand>) rawList;
        stands.forEach(s -> { if (s.isValid()) s.remove(); });
        stands.clear();
    }

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
        stand.setPersistent(false);
        stand.setInvulnerable(true);
        return stand;
    }
}