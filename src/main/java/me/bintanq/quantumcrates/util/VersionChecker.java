package me.bintanq.quantumcrates.util;

import me.bintanq.quantumcrates.QuantumCrates;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * VersionChecker — async version check against SpigotMC API on startup.
 * Notifies console and joining operators if outdated with a clickable link.
 * Toggleable via config key {@code settings.check-updates}.
 */
public class VersionChecker implements Listener {

    // Replace with your actual SpigotMC resource ID
    private static final int SPIGOT_RESOURCE_ID = 134691;
    private static final String SPIGOT_URL = "https://www.spigotmc.org/resources/" + SPIGOT_RESOURCE_ID;
    private static final String API_URL = "https://api.spigotmc.org/legacy/update.php?resource=" + SPIGOT_RESOURCE_ID;

    private final QuantumCrates plugin;
    private volatile String latestVersion = null;
    private volatile boolean isOutdated = false;

    public VersionChecker(QuantumCrates plugin) {
        this.plugin = plugin;
    }

    /**
     * Runs the async version check. Safe to call from any thread.
     */
    public void checkAsync() {
        if (!plugin.getConfig().getBoolean("settings.check-updates", true)) {
            Logger.debug("Update checker disabled in config.");
            return;
        }

        plugin.getAsyncExecutor().execute(() -> {
            try {
                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "QuantumCrates/" + plugin.getDescription().getVersion());

                if (conn.getResponseCode() != 200) return;

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()))) {
                    String response = reader.readLine();
                    if (response == null || response.trim().isEmpty()) return;

                    latestVersion = response.trim();
                    String currentVersion = plugin.getDescription().getVersion();

                    if (!currentVersion.equalsIgnoreCase(latestVersion)) {
                        isOutdated = true;
                        Logger.info("&a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        Logger.info("&aA new version of QuantumCrates is available!");
                        Logger.info("&7Current: &c" + currentVersion + " &7→ Latest: &a" + latestVersion);
                        Logger.info("&7Download: &b" + SPIGOT_URL);
                        Logger.info("&a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    } else {
                        Logger.info("QuantumCrates is &aup to date &f(v" + currentVersion + ").");
                    }
                }
            } catch (Exception e) {
                // Fail silently if no internet
                Logger.debug("Version check failed (no internet?): " + e.getMessage());
            }
        });
    }

    /**
     * Returns whether an update is available.
     *
     * @return true if the plugin is outdated
     */
    public boolean isOutdated() { return isOutdated; }

    /**
     * Gets the latest version string, or null if not checked yet.
     *
     * @return the latest version
     */
    public String getLatestVersion() { return latestVersion; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!isOutdated) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("quantumcrates.admin")) return;

        // Delay slightly so the player sees it
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            Component message = Component.text()
                    .append(Component.text("[QuantumCrates] ", NamedTextColor.DARK_AQUA))
                    .append(Component.text("Update available! ", NamedTextColor.GREEN))
                    .append(Component.text("v" + plugin.getDescription().getVersion(), NamedTextColor.RED))
                    .append(Component.text(" → ", NamedTextColor.GRAY))
                    .append(Component.text("v" + latestVersion, NamedTextColor.GREEN))
                    .append(Component.text(" "))
                    .append(Component.text("[Download]", NamedTextColor.AQUA)
                            .decorate(TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.openUrl(SPIGOT_URL))
                            .hoverEvent(HoverEvent.showText(
                                    Component.text("Click to open SpigotMC page", NamedTextColor.GRAY))))
                    .build();

            plugin.adventure().player(player).sendMessage(message);
        }, 40L);
    }
}
