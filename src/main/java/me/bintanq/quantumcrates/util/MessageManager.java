package me.bintanq.quantumcrates.util;

import me.bintanq.quantumcrates.QuantumCrates;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * MessageManager — ZERO hardcoded messages.
 *
 * Semua pesan dibaca dari config.yml section "messages".
 * Bisa dioverride dari Web Dashboard (Phase 2) tanpa restart.
 *
 * Usage:
 *   MessageManager.send(player, "reward-received", "{reward}", "Diamond");
 *   MessageManager.get("prefix");
 */
public final class MessageManager {

    private static QuantumCrates plugin;

    private MessageManager() {}

    public static void init(QuantumCrates p) { plugin = p; }

    /* ─────────────────────── Core Send ─────────────────────── */

    /**
     * Kirim pesan ke player/sender dengan placeholder replacement.
     * Placeholder format: pasangan key-value, e.g. "{player}", "Steve", "{amount}", "5"
     */
    public static void send(CommandSender sender, String key, String... placeholders) {
        sender.sendMessage(get(key, placeholders));
    }

    public static void sendRaw(CommandSender sender, String key, String... placeholders) {
        sender.sendMessage(getRaw(key, placeholders));
    }

    /**
     * Ambil pesan dengan prefix.
     */
    public static String get(String key, String... placeholders) {
        String prefix = getRaw("prefix");
        String msg    = getRaw(key, placeholders);
        return color(prefix + msg);
    }

    /**
     * Ambil pesan tanpa prefix.
     */
    public static String getRaw(String key, String... placeholders) {
        String msg = plugin.getConfig().getString("messages." + key, "[MSG NOT FOUND: " + key + "]");
        // Apply placeholders: index 0=key, 1=value, 2=key, 3=value, ...
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            msg = msg.replace(placeholders[i], placeholders[i + 1]);
        }
        return color(msg);
    }

    /**
     * Check apakah key ada di config.
     */
    public static boolean has(String key) {
        return plugin.getConfig().contains("messages." + key);
    }

    /* ─────────────────────── Convenience Senders ─────────────────────── */

    public static void sendNoPermission(CommandSender sender) {
        send(sender, "no-permission");
    }

    public static void sendPlayerOnly(CommandSender sender) {
        send(sender, "player-only");
    }

    public static void sendPlayerNotFound(CommandSender sender, String name) {
        send(sender, "player-not-found", "{player}", name);
    }

    public static void sendCrateNotFound(CommandSender sender, String crateId) {
        send(sender, "crate-not-found", "{crate}", crateId);
    }

    public static void sendInvalidNumber(CommandSender sender) {
        send(sender, "invalid-number");
    }

    public static void sendReloadSuccess(CommandSender sender) {
        send(sender, "reload-success");
    }

    /* ─────────────────────── Broadcast ─────────────────────── */

    public static void broadcast(String key, String... placeholders) {
        plugin.getServer().broadcastMessage(get(key, placeholders));
    }

    /* ─────────────────────── Colorize ─────────────────────── */

    public static String color(String s) {
        return s == null ? "" : s.replace("&", "\u00A7");
    }
}
