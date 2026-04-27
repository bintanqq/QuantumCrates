package me.bintanq.quantumcrates.listener;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.gui.PreviewGUI;
import me.bintanq.quantumcrates.util.MessageManager;
import me.bintanq.quantumcrates.manager.CrateManager;
import me.bintanq.quantumcrates.model.Crate;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * CrateListener — semua interaksi player dengan crate block.
 *
 * Controls:
 *  LEFT-CLICK  block         → Preview GUI (lihat semua reward + chance %)
 *  RIGHT-CLICK block         → Buka crate (consume key, roll reward)
 *  SHIFT+RIGHT-CLICK block   → Mass open (buka semua key sekaligus)
 */
public class CrateListener implements Listener {

    private final QuantumCrates plugin;
    private final CrateManager  crateManager;
    private final PreviewGUI    previewGUI;

    public CrateListener(QuantumCrates plugin, CrateManager crateManager) {
        this.plugin       = plugin;
        this.crateManager = crateManager;
        this.previewGUI   = new PreviewGUI(plugin, plugin.getRewardProcessor());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Ignore off-hand duplicate calls
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        Action action = event.getAction();

        // LEFT-CLICK → Preview GUI
        if (action == Action.LEFT_CLICK_BLOCK) {
            if (block == null) return;
            Crate crate = getCrateAtBlock(block);
            if (crate == null) return;
            event.setCancelled(true);
            handlePreview(event.getPlayer(), crate);
            return;
        }

        // RIGHT-CLICK → Open atau Mass Open
        if (action == Action.RIGHT_CLICK_BLOCK) {
            if (block == null) return;
            Crate crate = getCrateAtBlock(block);
            if (crate == null) return;
            event.setCancelled(true);
            Player player = event.getPlayer();
            if (player.isSneaking()) {
                handleMassOpen(player, crate);
            } else {
                crateManager.openCrate(player, crate.getId());
            }
        }
    }

    private void handlePreview(Player player, Crate crate) {
        if (!player.hasPermission("quantumcrates.preview")) {
            MessageManager.sendNoPermission(player);
            return;
        }
        previewGUI.open(player, crate);
    }

    private void handleMassOpen(Player player, Crate crate) {
        if (!player.hasPermission("quantumcrates.massopen")) {
            MessageManager.sendNoPermission(player);
            return;
        }
        if (!crate.isMassOpenEnabled()) {
            MessageManager.send(player, "mass-open-disabled", "{crate}", crate.getId());
            return;
        }
        crateManager.massOpen(player, crate.getId(), -1);
    }

    private Crate getCrateAtBlock(Block block) {
        Location blockLoc = block.getLocation();
        String worldName  = blockLoc.getWorld() != null ? blockLoc.getWorld().getName() : null;
        if (worldName == null) return null;
        for (Crate crate : crateManager.getAllCrates()) {
            Crate.SerializableLocation loc = crate.getLocation();
            if (loc == null) continue;
            if (!worldName.equals(loc.world)) continue;
            if ((int) loc.x == blockLoc.getBlockX()
                    && (int) loc.y == blockLoc.getBlockY()
                    && (int) loc.z == blockLoc.getBlockZ()) {
                return crate;
            }
        }
        return null;
    }

    private String color(String s) { return s.replace("&", "\u00A7"); }
}
