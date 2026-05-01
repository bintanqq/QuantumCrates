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
        List<ArmorStand> stands = new ArrayList<>();
        Location current = location.clone();

        if (!org.bukkit.Bukkit.isPrimaryThread()) {
            java.util.concurrent.CompletableFuture<List<ArmorStand>> future = new java.util.concurrent.CompletableFuture<>();
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    future.complete(spawnStands(current, lines));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            try {
                stands = future.get(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                Logger.warn("PacketHologram: Failed to spawn hologram for " + id + " from async thread: " + e.getMessage());
                return new ArrayList<>();
            }
        } else {
            stands = spawnStands(current, lines);
        }
        return stands;
    }

    private List<ArmorStand> spawnStands(Location base, List<String> lines) {
        List<ArmorStand> stands = new ArrayList<>();
        Location current = base.clone();
        for (String line : lines) {
            String colored = org.bukkit.ChatColor.translateAlternateColorCodes('&', line);
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
        Runnable remove = () -> stands.forEach(s -> { if (s.isValid()) s.remove(); });
        if (org.bukkit.Bukkit.isPrimaryThread()) {
            remove.run();
        } else {
            org.bukkit.Bukkit.getScheduler().runTask(plugin, remove);
        }
        stands.clear();
    }

    private ArmorStand spawnStand(Location location, String text) {
        return location.getWorld().spawn(location, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setCanPickupItems(false);
            as.setCustomName(text);
            as.setCustomNameVisible(true);
            as.setSmall(true);
            as.setArms(false);
            as.setBasePlate(false);
            as.setPersistent(false);
            as.setInvulnerable(true);
        });
    }
}