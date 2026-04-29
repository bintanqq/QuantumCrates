package me.bintanq.quantumcrates.listener;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.animation.CrateSession;
import me.bintanq.quantumcrates.gui.PreviewGUI;
import me.bintanq.quantumcrates.model.Crate;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

public class GUIListener implements Listener {

    private final QuantumCrates plugin;

    private static final int SLOT_PREV  = 46;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_NEXT  = 52;

    public GUIListener(QuantumCrates plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        CrateSession session = plugin.getAnimationManager().getSession(player.getUniqueId());

        if (session != null) {
            event.setCancelled(true);
            return;
        }

        String title = event.getView().getTitle();
        if (!PreviewGUI.isPreviewInventory(title)) return;

        event.setCancelled(true);

        var clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        int slot = event.getRawSlot();
        switch (slot) {
            case SLOT_CLOSE -> player.closeInventory();
            case SLOT_PREV  -> {
                int page = PreviewGUI.parsePageFromTitle(title);
                if (page > 0) openPreviewPage(player, title, page - 1);
            }
            case SLOT_NEXT -> {
                int page = PreviewGUI.parsePageFromTitle(title);
                openPreviewPage(player, title, page + 1);
            }
            default -> {}
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (plugin.getAnimationManager().hasSession(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        String title = event.getView().getTitle();
        if (PreviewGUI.isPreviewInventory(title)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        CrateSession session = plugin.getAnimationManager().getSession(player.getUniqueId());
        if (session == null) return;

        if (session.isRunning()) {
            session.setForfeited(false); // NOT forfeited — we are delivering
            session.setRunning(false);
            session.cancelAllTasks();
            plugin.getAnimationManager().completeSession(session);
            plugin.getCrateManager().deliverRewardPublic(player, session.getResult());
        } else {

            plugin.getAnimationManager().completeSession(session);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        CrateSession session = plugin.getAnimationManager().getSession(player.getUniqueId());
        if (session == null || !session.isRunning()) return;

        // If the opening inventory is not the animation inventory, cancel it
        if (session.getInventory() != null
                && event.getInventory() != session.getInventory()) {
            event.setCancelled(true);
        }
    }


    private void openPreviewPage(Player player, String title, int targetPage) {
        Crate crate = resolveCrateFromTitle(title);
        if (crate == null) { player.closeInventory(); return; }
        new PreviewGUI(plugin, plugin.getRewardProcessor()).open(player, crate, targetPage);
    }

    private Crate resolveCrateFromTitle(String title) {
        String stripped = title;
        if (stripped.startsWith(PreviewGUI.TITLE_PREFIX))
            stripped = stripped.substring(PreviewGUI.TITLE_PREFIX.length());
        int bracketIdx = stripped.lastIndexOf(" §8[");
        if (bracketIdx < 0) bracketIdx = stripped.lastIndexOf(" [");
        if (bracketIdx >= 0) stripped = stripped.substring(0, bracketIdx);
        String plainName = stripped.replaceAll("§.", "").trim();

        for (Crate crate : plugin.getCrateManager().getAllCrates()) {
            String displayPlain = crate.getDisplayName() != null
                    ? crate.getDisplayName().replaceAll("[&§].", "").trim() : "";
            if (plainName.equalsIgnoreCase(displayPlain)
                    || plainName.equalsIgnoreCase(crate.getId())) return crate;
        }
        return null;
    }
}