package me.bintanq.quantumcrates.placeholder;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.manager.CrateManager;
import me.bintanq.quantumcrates.manager.PlayerDataManager;
import me.bintanq.quantumcrates.model.Crate;
import me.bintanq.quantumcrates.util.TimeUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * QuantumPlaceholderExpansion — registers all %quantumcrates_*% placeholders.
 *
 * Available placeholders:
 *  %quantumcrates_keys_<keyId>%              → virtual key balance
 *  %quantumcrates_pity_<crateId>%            → current pity counter
 *  %quantumcrates_pity_max_<crateId>%        → pity threshold
 *  %quantumcrates_cooldown_<crateId>%        → remaining cooldown (formatted)
 *  %quantumcrates_cooldown_raw_<crateId>%    → remaining cooldown in ms
 *  %quantumcrates_open_<crateId>%            → whether crate is currently openable
 *  %quantumcrates_total_<crateId>%           → total weight of crate rewards
 */
public class QuantumPlaceholderExpansion extends PlaceholderExpansion {

    private final QuantumCrates     plugin;
    private final PlayerDataManager playerDataManager;
    private final CrateManager      crateManager;

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
        if (player == null) return "";

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

        // %quantumcrates_lifetime_max_<crateId>%
        if (params.startsWith("lifetime_max_")) {
            String crateId = params.substring(13);
            Crate crate = crateManager.getCrate(crateId);
            if (crate == null) return "0";
            return String.valueOf(crate.getLifetimeOpenLimit());
        }

        // %quantumcrates_lifetime_<crateId>%
        if (params.startsWith("lifetime_")) {
            String crateId = params.substring(9);
            return String.valueOf(plugin.getPlayerDataManager()
                    .getLifetimeOpens(player.getUniqueId(), crateId));
        }

        return null;
    }
}
