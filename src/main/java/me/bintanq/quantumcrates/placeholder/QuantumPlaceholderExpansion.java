package me.bintanq.quantumcrates.placeholder;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.hook.impl.VaultHook;
import me.bintanq.quantumcrates.manager.CrateManager;
import me.bintanq.quantumcrates.manager.PlayerDataManager;
import me.bintanq.quantumcrates.model.Crate;
import me.bintanq.quantumcrates.util.TimeUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QuantumPlaceholderExpansion — registers all %quantumcrates_*% placeholders.
 *
 * <h3>Player-specific placeholders</h3>
 * <ul>
 *   <li>{@code %quantumcrates_keys_<keyId>%} — virtual key balance</li>
 *   <li>{@code %quantumcrates_pity_<crateId>%} — current pity counter</li>
 *   <li>{@code %quantumcrates_pity_max_<crateId>%} — pity threshold</li>
 *   <li>{@code %quantumcrates_pity_remaining_<crateId>%} — opens until hard pity</li>
 *   <li>{@code %quantumcrates_pity_status_<crateId>%} — NORMAL/SOFT/HARD</li>
 *   <li>{@code %quantumcrates_cooldown_<crateId>%} — remaining cooldown (formatted)</li>
 *   <li>{@code %quantumcrates_cooldown_raw_<crateId>%} — remaining cooldown in ms</li>
 *   <li>{@code %quantumcrates_lifetime_<crateId>%} — lifetime opens count</li>
 *   <li>{@code %quantumcrates_lifetime_max_<crateId>%} — lifetime open limit</li>
 *   <li>{@code %quantumcrates_lifetime_remaining_<crateId>%} — remaining lifetime opens</li>
 *   <li>{@code %quantumcrates_can_open_<crateId>%} — true/false full open eligibility</li>
 *   <li>{@code %quantumcrates_balance%} — Vault economy balance (if available)</li>
 * </ul>
 *
 * <h3>Global placeholders (no player required)</h3>
 * <ul>
 *   <li>{@code %quantumcrates_open_<crateId>%} — whether crate is currently openable</li>
 *   <li>{@code %quantumcrates_total_<crateId>%} — total weight of crate rewards</li>
 *   <li>{@code %quantumcrates_rewards_<crateId>%} — reward count in crate</li>
 *   <li>{@code %quantumcrates_crate_count%} — total number of crates</li>
 *   <li>{@code %quantumcrates_crate_list%} — comma-separated crate IDs</li>
 *   <li>{@code %quantumcrates_crate_name_<crateId>%} — crate display name</li>
 *   <li>{@code %quantumcrates_crate_enabled_<crateId>%} — true/false</li>
 *   <li>{@code %quantumcrates_version%} — plugin version</li>
 * </ul>
 */
public class QuantumPlaceholderExpansion extends PlaceholderExpansion {

    private final QuantumCrates     plugin;
    private final PlayerDataManager playerDataManager;
    private final CrateManager      crateManager;

    // Cache for expensive global placeholders (refreshed every 5s)
    private volatile long lastCacheRefresh = 0;
    private volatile int cachedCrateCount = 0;
    private volatile String cachedCrateList = "";

    private static final long CACHE_TTL_MS = 5000L;

    public QuantumPlaceholderExpansion(
            QuantumCrates plugin,
            PlayerDataManager playerDataManager,
            CrateManager crateManager
    ) {
        this.plugin            = plugin;
        this.playerDataManager = playerDataManager;
        this.crateManager      = crateManager;
    }

    @Override public @NotNull String getIdentifier() { return "quantumcrates"; }
    @Override public @NotNull String getAuthor()     { return "bintanq"; }
    @Override public @NotNull String getVersion()    { return plugin.getDescription().getVersion(); }
    @Override public boolean persist()               { return true; }
    @Override public boolean canRegister()           { return true; }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {

        // ─── Global placeholders (no player required) ──────────────────

        if (params.equals("version")) {
            return plugin.getDescription().getVersion();
        }

        if (params.equals("crate_count")) {
            refreshCacheIfNeeded();
            return String.valueOf(cachedCrateCount);
        }

        if (params.equals("crate_list")) {
            refreshCacheIfNeeded();
            return cachedCrateList;
        }

        // %quantumcrates_crate_name_<crateId>%
        if (params.startsWith("crate_name_")) {
            String crateId = params.substring(11);
            Crate crate = crateManager.getCrate(crateId);
            if (crate == null) return "Unknown";
            return crate.getDisplayName() != null ? crate.getDisplayName() : crateId;
        }

        // %quantumcrates_crate_enabled_<crateId>%
        if (params.startsWith("crate_enabled_")) {
            String crateId = params.substring(14);
            Crate crate = crateManager.getCrate(crateId);
            return crate != null ? String.valueOf(crate.isEnabled()) : "false";
        }

        // %quantumcrates_open_<crateId>%
        if (params.startsWith("open_")) {
            String crateId = params.substring(5);
            Crate crate = crateManager.getCrate(crateId);
            if (crate == null) return "false";
            return String.valueOf(crate.isCurrentlyOpenable());
        }

        // %quantumcrates_total_<crateId>%
        if (params.startsWith("total_")) {
            String crateId = params.substring(6);
            Crate crate = crateManager.getCrate(crateId);
            if (crate == null) return "0";
            return String.format("%.2f", crate.getTotalWeight());
        }

        // %quantumcrates_rewards_<crateId>%
        if (params.startsWith("rewards_")) {
            String crateId = params.substring(8);
            Crate crate = crateManager.getCrate(crateId);
            if (crate == null) return "0";
            return String.valueOf(crate.getRewards().size());
        }

        // ─── Player-required placeholders ──────────────────────────────

        if (player == null) return "";

        // %quantumcrates_balance%
        if (params.equals("balance")) {
            VaultHook vault = plugin.getHookManager().getVaultHook();
            if (vault == null || !vault.isEnabled()) return "0";
            return vault.format(vault.getBalance(player));
        }

        // %quantumcrates_keys_<keyId>%
        if (params.startsWith("keys_")) {
            String keyId = params.substring(5);
            return String.valueOf(plugin.getKeyManager().getVirtualBalance(player, keyId));
        }

        // %quantumcrates_pity_max_<crateId>%
        if (params.startsWith("pity_max_")) {
            String crateId = params.substring(9);
            Crate crate = crateManager.getCrate(crateId);
            if (crate == null) return "0";
            return String.valueOf(crate.getPity().getThreshold());
        }

        // %quantumcrates_pity_remaining_<crateId>%
        if (params.startsWith("pity_remaining_")) {
            String crateId = params.substring(15);
            Crate crate = crateManager.getCrate(crateId);
            if (crate == null) return "0";
            int current = playerDataManager.getPity(player.getUniqueId(), crateId);
            int max = crate.getPity().getThreshold();
            return String.valueOf(Math.max(0, max - current));
        }

        // %quantumcrates_pity_status_<crateId>%
        if (params.startsWith("pity_status_")) {
            String crateId = params.substring(12);
            Crate crate = crateManager.getCrate(crateId);
            if (crate == null || !crate.getPity().isEnabled()) return "DISABLED";
            int pity = playerDataManager.getPity(player.getUniqueId(), crateId);
            if (pity >= crate.getPity().getThreshold()) return "HARD";
            if (pity >= crate.getPity().getSoftPityStart()) return "SOFT";
            return "NORMAL";
        }

        // %quantumcrates_pity_<crateId>%
        if (params.startsWith("pity_")) {
            String crateId = params.substring(5);
            return String.valueOf(playerDataManager.getPity(player.getUniqueId(), crateId));
        }

        // %quantumcrates_cooldown_raw_<crateId>%
        if (params.startsWith("cooldown_raw_")) {
            String crateId = params.substring(13);
            Crate crate = crateManager.getCrate(crateId);
            if (crate == null) return "0";
            long remaining = playerDataManager.getRemainingCooldown(
                    player.getUniqueId(), crateId, crate.getCooldownMs());
            return String.valueOf(remaining);
        }

        // %quantumcrates_cooldown_<crateId>%
        if (params.startsWith("cooldown_")) {
            String crateId = params.substring(9);
            Crate crate = crateManager.getCrate(crateId);
            if (crate == null) return "Ready";
            long remaining = playerDataManager.getRemainingCooldown(
                    player.getUniqueId(), crateId, crate.getCooldownMs());
            return remaining > 0 ? TimeUtil.formatDuration(remaining) : "Ready";
        }

        // %quantumcrates_lifetime_max_<crateId>%
        if (params.startsWith("lifetime_max_")) {
            String crateId = params.substring(13);
            Crate crate = crateManager.getCrate(crateId);
            if (crate == null) return "0";
            int limit = crate.getLifetimeOpenLimit();
            return limit <= 0 ? "∞" : String.valueOf(limit);
        }

        // %quantumcrates_lifetime_remaining_<crateId>%
        if (params.startsWith("lifetime_remaining_")) {
            String crateId = params.substring(19);
            Crate crate = crateManager.getCrate(crateId);
            if (crate == null) return "0";
            int limit = crate.getLifetimeOpenLimit();
            if (limit <= 0) return "∞";
            int used = plugin.getPlayerDataManager().getLifetimeOpens(player.getUniqueId(), crateId);
            return String.valueOf(Math.max(0, limit - used));
        }

        // %quantumcrates_lifetime_<crateId>%
        if (params.startsWith("lifetime_")) {
            String crateId = params.substring(9);
            return String.valueOf(plugin.getPlayerDataManager()
                    .getLifetimeOpens(player.getUniqueId(), crateId));
        }

        // %quantumcrates_can_open_<crateId>%
        if (params.startsWith("can_open_")) {
            String crateId = params.substring(9);
            var result = plugin.getCrateManager().canOpen(player, crateId);
            return String.valueOf(result == CrateManager.OpenResult.SUCCESS);
        }

        return "";
    }

    private void refreshCacheIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCacheRefresh < CACHE_TTL_MS) return;
        lastCacheRefresh = now;
        var allCrates = crateManager.getAllCrates();
        cachedCrateCount = allCrates.size();
        cachedCrateList = String.join(", ",
                allCrates.stream().map(Crate::getId).toList());
    }
}
