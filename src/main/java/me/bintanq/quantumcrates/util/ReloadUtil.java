package me.bintanq.quantumcrates.util;

import me.bintanq.quantumcrates.QuantumCrates;

public final class ReloadUtil {

    private ReloadUtil() {}

    /**
     * Full reload sequence:
     *  1. Stop all particle tasks
     *  2. Remove all holograms
     *  3. Re-load crate JSON files
     *  4. Re-spawn holograms
     *  5. Re-start particles
     */
    public static void reloadAll(QuantumCrates plugin) {
        // 0. Reload config
        plugin.reloadConfig();

        // 0b. Reinit MessageManager (config baru, message baru)
        MessageManager.init(plugin);

        // 0c. Reload key definitions dari config
        plugin.getKeyManager().reload();

        // 1. Stop particles
        if (plugin.getParticleManager() != null) {
            plugin.getParticleManager().stopAll();
        }

        // 2. Remove holograms
        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().removeAll();
        }

        // 3. Reload crates from disk
        plugin.getCrateManager().loadAllCrates();

        // 4. Re-spawn holograms
        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().spawnAll();
        }

        // 5. Re-start particles
        if (plugin.getParticleManager() != null) {
            plugin.getParticleManager().startAll();
        }

        // 6. Notify WebSocket clients (Phase 2)
        me.bintanq.quantumcrates.web.WebSocketBridge.getInstance()
                .broadcastCrateUpdate(null); // null = full reload signal

        Logger.info("Full reload complete. &e"
                + plugin.getCrateManager().getAllCrates().size() + " &fcrates loaded.");
    }
}
