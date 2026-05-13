package me.bintanq.quantumcrates.hook.impl;

import me.bintanq.quantumcrates.hook.ItemHook;
import me.bintanq.quantumcrates.util.Logger;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * VaultHook — soft integration with Vault economy.
 *
 * <p>Used for economy-based reward commands (give money, take money)
 * and economy-based key purchases. Fails gracefully if Vault is absent.</p>
 */
public class VaultHook {

    private Economy economy;
    private boolean enabled;

    public VaultHook() {
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
                enabled = false;
                return;
            }
            RegisteredServiceProvider<Economy> rsp =
                    Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
                enabled = true;
                Logger.info("Vault economy hooked: &a" + economy.getName());
            } else {
                Logger.warn("Vault found but no economy provider registered.");
                enabled = false;
            }
        } catch (Exception e) {
            Logger.warn("Vault hook failed: " + e.getMessage());
            enabled = false;
        }
    }

    /**
     * Checks if Vault economy is available and functional.
     *
     * @return {@code true} if economy calls can be made
     */
    public boolean isEnabled() { return enabled && economy != null; }

    /**
     * Gets the player's economy balance.
     *
     * @param player the player
     * @return the balance, or 0 if economy unavailable
     */
    public double getBalance(Player player) {
        if (!isEnabled()) return 0;
        return economy.getBalance(player);
    }

    /**
     * Checks if the player has at least the specified amount.
     *
     * @param player the player
     * @param amount the amount to check
     * @return {@code true} if the player has enough
     */
    public boolean has(Player player, double amount) {
        if (!isEnabled()) return false;
        return economy.has(player, amount);
    }

    /**
     * Deposits money into the player's account.
     *
     * @param player the player
     * @param amount the amount to deposit
     * @return {@code true} if successful
     */
    public boolean deposit(Player player, double amount) {
        if (!isEnabled() || amount <= 0) return false;
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    /**
     * Withdraws money from the player's account.
     *
     * @param player the player
     * @param amount the amount to withdraw
     * @return {@code true} if successful
     */
    public boolean withdraw(Player player, double amount) {
        if (!isEnabled() || amount <= 0) return false;
        if (!economy.has(player, amount)) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    /**
     * Formats a monetary amount using the economy's formatting rules.
     *
     * @param amount the amount to format
     * @return the formatted string, or the raw number if economy unavailable
     */
    public String format(double amount) {
        if (!isEnabled()) return String.format("%.2f", amount);
        return economy.format(amount);
    }

    /**
     * Gets the economy currency name (singular).
     *
     * @return the currency name
     */
    public String getCurrencyName() {
        if (!isEnabled()) return "coins";
        return economy.currencyNameSingular();
    }

    /**
     * Gets the economy currency name (plural).
     *
     * @return the plural currency name
     */
    public String getCurrencyNamePlural() {
        if (!isEnabled()) return "coins";
        return economy.currencyNamePlural();
    }
}
