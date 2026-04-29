package me.bintanq.quantumcrates.listener;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.gui.PreviewGUI;
import me.bintanq.quantumcrates.model.Crate;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

/**
 * GUIListener — handle semua interaksi di dalam QuantumCrates GUI.
 *
 * Preview GUI:
 *  - Cancel semua click (tidak bisa ambil item)
 *  - Slot PREV (46)  → buka halaman sebelumnya
 *  - Slot CLOSE (49) → tutup inventory
 *  - Slot NEXT (52)  → buka halaman berikutnya
 *  - Slot INFO (50)  → tidak ada aksi (info only)
 *  - Item reward     → tidak ada aksi (display only)
 */
public class GUIListener implements Listener {

    private final QuantumCrates plugin;

    // Slot navigasi harus sinkron dengan PreviewGUI
    private static final int SLOT_PREV  = 46;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_INFO  = 50;
    private static final int SLOT_NEXT  = 52;

    public GUIListener(QuantumCrates plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!PreviewGUI.isPreviewInventory(title)) return;

        // Selalu cancel dulu — tidak boleh ada item yang bisa diambil
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        int slot = event.getRawSlot();

        switch (slot) {
            case SLOT_CLOSE -> player.closeInventory();

            case SLOT_PREV -> {
                int currentPage = PreviewGUI.parsePageFromTitle(title);
                if (currentPage > 0) {
                    openPreviewPage(player, title, currentPage - 1);
                }
            }

            case SLOT_NEXT -> {
                int currentPage = PreviewGUI.parsePageFromTitle(title);
                openPreviewPage(player, title, currentPage + 1);
            }

            // SLOT_INFO dan reward slots: tidak ada aksi
            default -> { /* display only */ }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (PreviewGUI.isPreviewInventory(title)) {
            event.setCancelled(true);
        }
    }

    /* ─────────────────────── Navigation Helper ─────────────────────── */

    /**
     * Resolve crate dari title GUI lalu buka halaman yang diminta.
     * Title format: "§0§lPreview §8» §bCrate Name [page/total]"
     * Crate name di-extract dengan strip prefix dan suffix page.
     */
    private void openPreviewPage(Player player, String title, int targetPage) {
        Crate crate = resolveCrateFromTitle(title);
        if (crate == null) {
            player.closeInventory();
            return;
        }
        new PreviewGUI(plugin, plugin.getRewardProcessor()).open(player, crate, targetPage);
    }

    /**
     * Cari crate yang sesuai dengan title GUI.
     *
     * Strategi: strip prefix "§0§lPreview §8» ", strip suffix " §8[X/Y]",
     * strip color codes, lalu cocokkan dengan display name atau ID semua crate.
     */
    private Crate resolveCrateFromTitle(String title) {
        // Strip prefix
        String stripped = title;
        if (stripped.startsWith(PreviewGUI.TITLE_PREFIX)) {
            stripped = stripped.substring(PreviewGUI.TITLE_PREFIX.length());
        }

        // Strip suffix " [X/Y]" kalau ada
        int bracketIdx = stripped.lastIndexOf(" §8[");
        if (bracketIdx < 0) bracketIdx = stripped.lastIndexOf(" [");
        if (bracketIdx >= 0) stripped = stripped.substring(0, bracketIdx);

        // Strip semua color codes (§X)
        String plainName = stripped.replaceAll("§.", "").trim();

        // Cocokkan dengan semua crate — cek display name dan ID
        for (Crate crate : plugin.getCrateManager().getAllCrates()) {
            String displayPlain = crate.getDisplayName() != null
                    ? crate.getDisplayName().replaceAll("[&§].", "").trim()
                    : "";
            if (plainName.equalsIgnoreCase(displayPlain)
                    || plainName.equalsIgnoreCase(crate.getId())) {
                return crate;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        plugin.getAnimationManager().onInventoryClose(player);
    }
}
