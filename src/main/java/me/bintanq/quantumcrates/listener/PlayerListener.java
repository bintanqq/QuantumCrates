package me.bintanq.quantumcrates.listener;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.manager.PlayerDataManager;
import me.bintanq.quantumcrates.util.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final QuantumCrates     plugin;
    private final PlayerDataManager playerDataManager;

    public PlayerListener(QuantumCrates plugin, PlayerDataManager playerDataManager) {
        this.plugin            = plugin;
        this.playerDataManager = playerDataManager;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        try {
            playerDataManager.loadPlayer(event.getUniqueId()).get();
            plugin.getKeyManager().preloadKeys(event.getUniqueId());
        } catch (Exception e) {
            Logger.debug("Pre-login data load failed for " + event.getUniqueId() + ": " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getAsyncExecutor().execute(() ->
                playerDataManager.unloadPlayer(event.getPlayer().getUniqueId()));
    }
}