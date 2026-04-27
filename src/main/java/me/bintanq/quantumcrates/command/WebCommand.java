package me.bintanq.quantumcrates.command;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.util.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.net.InetAddress;

/**
 * /qc web — generate magic link dan kirim ke pengirim command.
 *
 * Usage:
 *   /qc web              → generate link pakai IP otomatis
 *   /qc web <ip/domain>  → pakai IP/domain custom (jika server di balik proxy)
 *
 * Contoh output di chat:
 *   [QuantumCrates] Dashboard link (valid 5 menit):
 *   http://103.x.x.x:7420/?token=abc123xyz
 *   Klik link di atas untuk membuka web dashboard.
 */
public class WebCommand implements CommandExecutor, TabCompleter {

    private final QuantumCrates plugin;

    public WebCommand(QuantumCrates plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("quantumcrates.admin")) {
            MessageManager.sendNoPermission(sender);
            return true;
        }

        if (plugin.getWebServer() == null) {
            sender.sendMessage(MessageManager.color("&c[QC] Web server tidak aktif. Cek &eweb.enabled &cdi config.yml"));
            return true;
        }

        // Resolve IP/host
        String host = resolveHost(sender, args);
        int    port = plugin.getConfig().getInt("web.port", 7420);

        // Generate magic link token
        String token = plugin.getWebServer().getTokenManager().generateToken();
        String url   = "http://" + host + ":" + port + "/?token=" + token;

        // Kirim ke sender — format yang clickable di chat Minecraft
        sender.sendMessage("");
        sender.sendMessage(MessageManager.color("&8&l━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(MessageManager.color("&b&l  QuantumCrates Dashboard"));
        sender.sendMessage(MessageManager.color("&8&l━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(MessageManager.color("&7Link &f(valid &e5 menit&f, sekali pakai):"));
        sender.sendMessage(MessageManager.color("&b" + url));
        sender.sendMessage(MessageManager.color("&7Klik link di atas untuk membuka dashboard."));
        sender.sendMessage(MessageManager.color("&8&l━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage("");

        // Kalau sender adalah player, kirim juga sebagai clickable text component
        if (sender instanceof Player player) {
            sendClickableLink(player, url);
        }

        return true;
    }

    private String resolveHost(CommandSender sender, String[] args) {
        // Jika ada argumen pertama, pakai itu sebagai host
        if (args.length >= 1) return args[0];

        // Cek config apakah ada web.host yang diset
        String configHost = plugin.getConfig().getString("web.host", "");
        if (!configHost.isEmpty()) return configHost;

        // Auto-detect IP server
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "localhost";
        }
    }

    /**
     * Kirim text component yang bisa diklik langsung di chat Minecraft.
     * Menggunakan net.kyori.adventure (sudah include di Paper).
     */
    private void sendClickableLink(Player player, String url) {
        var msg = net.kyori.adventure.text.Component.text()
            .append(net.kyori.adventure.text.Component.text("  ▶ ")
                .color(net.kyori.adventure.text.format.NamedTextColor.DARK_AQUA))
            .append(net.kyori.adventure.text.Component.text("[Klik untuk buka dashboard]")
                .color(net.kyori.adventure.text.format.NamedTextColor.AQUA)
                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(url))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                    net.kyori.adventure.text.Component.text(url)
                        .color(net.kyori.adventure.text.format.NamedTextColor.GRAY))))
            .build();
        player.sendMessage(msg);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return List.of("<ip/domain>", "localhost");
        return List.of();
    }
}
