package me.bintanq.quantumcrates.listener;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.manager.PlayerDataManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * PlayerListener — manages PlayerData lifecycle on join/quit.
 *
 * Uses AsyncPlayerPreLoginEvent so data is warm in cache
 * before the player reaches the main thread.
 */
public class PlayerListener implements Listener {

    private final QuantumCrates     plugin;
    private final PlayerDataManager playerDataManager;

    public PlayerListener(QuantumCrates plugin, PlayerDataManager playerDataManager) {
        this.plugin            = plugin;
        this.playerDataManager = playerDataManager;
    }

    /**
     * Pre-load player data asynchronously before they fully join.
     * This ensures the main thread NEVER waits on DB for pity/cooldown reads.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        // This event fires on an async thread — perfect for DB load
        try {
            playerDataManager.loadPlayer(event.getUniqueId()).get();
        } catch (Exception e) {
            // Non-fatal: player gets empty data, will be re-created on save
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // Unload and flush dirty data asynchronously
        plugin.getAsyncExecutor().execute(() ->
                playerDataManager.unloadPlayer(event.getPlayer().getUniqueId()));
    }
}
