package me.bintanq.quantumcrates.api;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.api.dto.*;
import me.bintanq.quantumcrates.api.event.*;
import me.bintanq.quantumcrates.model.Crate;
import me.bintanq.quantumcrates.model.reward.Reward;
import me.bintanq.quantumcrates.model.reward.RewardResult;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * QuantumCratesAPI — the stable, public-facing developer API.
 *
 * <p>All methods exposed here use interfaces and DTOs, never internal classes.
 * This API is safe to depend on across plugin updates.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * QuantumCratesAPI api = QuantumCratesAPI.getInstance();
 * api.giveKeys(player, "example_key", 5);
 * }</pre>
 *
 * @since 1.4.0
 */
public final class QuantumCratesAPI {

    private static QuantumCratesAPI instance;

    private final QuantumCrates plugin;

    private QuantumCratesAPI(QuantumCrates plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes the API singleton. Called internally by the plugin.
     *
     * @param plugin the QuantumCrates plugin instance
     */
    public static void init(QuantumCrates plugin) {
        instance = new QuantumCratesAPI(plugin);
    }

    /**
     * Gets the API singleton instance.
     *
     * @return the API instance, or {@code null} if the plugin is not loaded
     */
    public static QuantumCratesAPI getInstance() {
        return instance;
    }

    // ─── Crate Queries ──────────────────────────────────────────────────────

    /**
     * Gets a read-only snapshot of a crate by its ID.
     *
     * @param crateId the crate identifier
     * @return a {@link CrateSnapshot}, or {@code null} if not found
     */
    public CrateSnapshot getCrate(String crateId) {
        Crate c = plugin.getCrateManager().getCrate(crateId);
        return c != null ? CrateSnapshot.from(c) : null;
    }

    /**
     * Gets all registered crate IDs.
     *
     * @return an unmodifiable set of crate IDs
     */
    public Set<String> getCrateIds() {
        return Collections.unmodifiableSet(plugin.getCrateManager().getCrateRegistry().keySet());
    }

    /**
     * Gets snapshots of all registered crates.
     *
     * @return an unmodifiable list of crate snapshots
     */
    public List<CrateSnapshot> getAllCrates() {
        return plugin.getCrateManager().getAllCrates().stream()
                .map(CrateSnapshot::from)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Checks whether a crate is currently openable (schedule check).
     *
     * @param crateId the crate identifier
     * @return {@code true} if the crate is openable right now
     */
    public boolean isCrateOpenable(String crateId) {
        Crate c = plugin.getCrateManager().getCrate(crateId);
        return c != null && c.isEnabled() && c.isCurrentlyOpenable();
    }

    // ─── Key Management ─────────────────────────────────────────────────────

    /**
     * Gets the virtual key balance for an online player.
     *
     * @param player the player to check
     * @param keyId  the key identifier
     * @return the virtual key count, or 0 if unavailable
     */
    public int getKeyBalance(Player player, String keyId) {
        return plugin.getKeyManager().getVirtualBalance(player, keyId);
    }

    /**
     * Gives virtual keys to an online player.
     * Fires a {@link CrateKeyGiveEvent} before executing.
     *
     * @param player the recipient
     * @param keyId  the key identifier
     * @param amount the number of keys to give (must be positive)
     * @return {@code true} if the keys were given successfully
     */
    public boolean giveKeys(Player player, String keyId, int amount) {
        if (amount <= 0) return false;
        CrateKeyGiveEvent event = new CrateKeyGiveEvent(player, keyId, amount);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
        return plugin.getKeyManager().giveKey(player, keyId, event.getAmount());
    }

    /**
     * Takes virtual keys from an online player.
     * Fires a {@link CrateKeyTakeEvent} before executing.
     *
     * @param player the player
     * @param keyId  the key identifier
     * @param amount the number of keys to take (must be positive)
     * @return {@code true} if the keys were successfully removed
     */
    public boolean takeKeys(Player player, String keyId, int amount) {
        if (amount <= 0) return false;
        CrateKeyTakeEvent event = new CrateKeyTakeEvent(player, keyId, amount);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
        return plugin.getDatabaseManager()
                .removeVirtualKeys(player.getUniqueId(), keyId, event.getAmount())
                .join();
    }

    /**
     * Gets all known key IDs across all crates.
     *
     * @return an unmodifiable collection of key IDs
     */
    public Collection<String> getKnownKeyIds() {
        return Collections.unmodifiableCollection(plugin.getKeyManager().getKnownKeyIds());
    }

    // ─── Crate Opening ──────────────────────────────────────────────────────

    /**
     * Attempts to open a crate for a player.
     * Fires a {@link CratePreOpenEvent} before opening and
     * a {@link CrateOpenEvent} after successful opening.
     *
     * @param player  the player opening the crate
     * @param crateId the crate to open
     * @return {@code true} if the crate was opened successfully
     */
    public boolean openCrate(Player player, String crateId) {
        CratePreOpenEvent preEvent = new CratePreOpenEvent(player, crateId);
        Bukkit.getPluginManager().callEvent(preEvent);
        if (preEvent.isCancelled()) return false;
        return plugin.getCrateManager().openCrate(player, crateId);
    }

    /**
     * Mass-opens a crate for a player.
     *
     * @param player  the player
     * @param crateId the crate to open
     * @param count   the number of times to open
     */
    public void massOpen(Player player, String crateId, int count) {
        CratePreOpenEvent preEvent = new CratePreOpenEvent(player, crateId);
        Bukkit.getPluginManager().callEvent(preEvent);
        if (preEvent.isCancelled()) return;
        plugin.getCrateManager().massOpen(player, crateId, count);
    }

    // ─── Player Data ────────────────────────────────────────────────────────

    /**
     * Gets a player's current pity counter for a specific crate.
     *
     * @param player  the player
     * @param crateId the crate identifier
     * @return the pity counter value
     */
    public int getPity(Player player, String crateId) {
        return plugin.getPlayerDataManager().getPity(player.getUniqueId(), crateId);
    }

    /**
     * Resets a player's pity counter for a specific crate.
     *
     * @param player  the player
     * @param crateId the crate identifier
     */
    public void resetPity(Player player, String crateId) {
        plugin.getPlayerDataManager().resetPity(player.getUniqueId(), crateId);
    }

    /**
     * Gets the remaining cooldown (in milliseconds) for a player on a crate.
     *
     * @param player  the player
     * @param crateId the crate identifier
     * @return remaining cooldown in ms, or 0 if no cooldown
     */
    public long getRemainingCooldown(Player player, String crateId) {
        Crate c = plugin.getCrateManager().getCrate(crateId);
        if (c == null || c.getCooldownMs() <= 0) return 0;
        return plugin.getPlayerDataManager()
                .getRemainingCooldown(player.getUniqueId(), crateId, c.getCooldownMs());
    }

    /**
     * Gets a player's lifetime opens for a specific crate.
     *
     * @param player  the player
     * @param crateId the crate identifier
     * @return total number of lifetime opens
     */
    public int getLifetimeOpens(Player player, String crateId) {
        return plugin.getPlayerDataManager().getLifetimeOpens(player.getUniqueId(), crateId);
    }

    // ─── Reward Info ────────────────────────────────────────────────────────

    /**
     * Gets a snapshot of all rewards in a crate.
     *
     * @param crateId the crate identifier
     * @return a list of reward snapshots, or empty if crate not found
     */
    public List<RewardSnapshot> getRewards(String crateId) {
        Crate c = plugin.getCrateManager().getCrate(crateId);
        if (c == null) return Collections.emptyList();
        double totalWeight = c.getTotalWeight();
        return c.getRewards().stream()
                .map(r -> RewardSnapshot.from(r, totalWeight))
                .collect(Collectors.toUnmodifiableList());
    }

    // ─── Vault Economy ──────────────────────────────────────────────────────

    /**
     * Checks whether Vault economy integration is active.
     *
     * @return {@code true} if Vault economy is available
     */
    public boolean isVaultEnabled() {
        return plugin.getHookManager().getVaultHook() != null
                && plugin.getHookManager().getVaultHook().isEnabled();
    }

    // ─── Plugin Info ────────────────────────────────────────────────────────

    /**
     * Gets the plugin version string.
     *
     * @return the current QuantumCrates version
     */
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /**
     * Gets the total number of registered crates.
     *
     * @return crate count
     */
    public int getCrateCount() {
        return plugin.getCrateManager().getAllCrates().size();
    }
}
