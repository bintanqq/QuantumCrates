package me.bintanq.quantumcrates.util;

import me.bintanq.quantumcrates.QuantumCrates;

public final class ReloadUtil {

    private ReloadUtil() {}

    /**
     * Full reload sequence:
     *  1. Stop all particle tasks
     *  2. Remove all holograms
     *  3. Reload rarities.yml → RarityManager
     *  4. Re-load crate JSON files
     *  5. Re-spawn holograms
     *  6. Re-start particles
     */
    public static void reloadAll(QuantumCrates plugin) {
        plugin.reloadConfig();
        MessageManager.init(plugin);
        plugin.getKeyManager().reload();

        // Reload rarities BEFORE crates (crates may reference rarity IDs)
        if (plugin.getRarityManager() != null) {
            plugin.getRarityManager().reload();
        }

        if (plugin.getParticleManager() != null) {
            plugin.getParticleManager().stopAll();
        }

        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().removeAll();
        }

        plugin.getCrateManager().loadAllCrates();

        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().spawnAll();
        }

        if (plugin.getParticleManager() != null) {
            plugin.getParticleManager().startAll();
        }

        me.bintanq.quantumcrates.web.WebSocketBridge.getInstance()
                .broadcastCrateUpdate(null);

        // Broadcast updated rarities to web clients
        me.bintanq.quantumcrates.web.WebSocketBridge.getInstance()
                .broadcastRaritiesUpdate(plugin.getRarityManager().getAll());

        Logger.info("Full reload complete. &e"
                + plugin.getCrateManager().getAllCrates().size() + " &fcrates loaded, &e"
                + plugin.getRarityManager().getAll().size() + " &frarity tiers.");
    }
}