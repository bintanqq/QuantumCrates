package me.bintanq.quantumcrates.command;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.util.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class WebCommand implements CommandExecutor, TabCompleter {

    private final QuantumCrates plugin;

    public WebCommand(QuantumCrates plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("quantumcrates.admin") && !sender.hasPermission("quantumcrates.web")) {
            MessageManager.sendNoPermission(sender);
            return true;
        }

        if (plugin.getWebServer() == null) {
            MessageManager.send(sender, "web-disabled");
            return true;
        }

        String host = resolveHost(args);
        int    port = plugin.getConfig().getInt("web.port", 7420);

        String token = plugin.getWebServer().getTokenManager().generateToken();
        String url   = "http://" + host + ":" + port + "/api/auth/magic?token=" + token;

        sender.sendMessage("");
        sender.sendMessage(MessageManager.getRaw("web-header-bar"));
        sender.sendMessage(MessageManager.getRaw("web-header-title"));
        sender.sendMessage(MessageManager.getRaw("web-header-bar"));
        sender.sendMessage(MessageManager.getRaw("web-link-label"));
        sender.sendMessage(MessageManager.getRaw("web-header-bar"));
        sender.sendMessage("");

        if (sender instanceof Player player) {
            sendClickableLink(player, url);
        } else {
            sender.sendMessage(MessageManager.color("&b" + url));
        }

        return true;
    }

    private String resolveHost(String[] args) {
        if (args.length >= 1) return args[0];
        String configHost = plugin.getConfig().getString("web.hostname", "");
        if (configHost.equalsIgnoreCase("auto")) {
            return plugin.getWebServer().resolveAutoHostname();
        }
        if (!configHost.isEmpty()) return configHost;
        return "localhost";
    }

    /**
     * Sends a clickable link using BOTH Adventure (for Paper) and Spigot BaseComponent
     * (for Spigot/forks). This dual approach ensures the link is clickable on ALL
     * supported server versions.
     *
     * Root cause of the old bug: Adventure's BukkitAudiences adapter on non-Paper
     * servers serializes ClickEvent differently, and the BOLD decoration on the URL
     * text could break URL parsing on some clients. Fix: use UNDERLINED only (no BOLD)
     * on the URL, and add a separate styled button component. Also send via Spigot API
     * as fallback.
     */
    private void sendClickableLink(Player player, String url) {
        // Method 1: Adventure API (works on Paper natively)
        try {
            Component urlLine = Component.text()
                    .append(Component.text("  ► ", NamedTextColor.DARK_AQUA))
                    .append(Component.text(url, NamedTextColor.AQUA)
                            .decorate(TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.openUrl(url))
                            .hoverEvent(HoverEvent.showText(
                                    Component.text("Click to open dashboard", NamedTextColor.GRAY))))
                    .build();

            Component buttonLine = Component.text()
                    .append(Component.text("  "))
                    .append(Component.text("[" + MessageManager.getRaw("web-click-button")
                                    .replace("§", "&")
                                    .replace("&r", "")
                                    .replaceAll("&[0-9a-fk-or]", "")
                                    .replace("[", "").replace("]", "")
                            + "]",
                            NamedTextColor.GREEN)
                            .decorate(TextDecoration.BOLD)
                            .clickEvent(ClickEvent.openUrl(url))
                            .hoverEvent(HoverEvent.showText(
                                    Component.text("Click to open in browser", NamedTextColor.GRAY))))
                    .build();

            plugin.adventure().player(player).sendMessage(urlLine);
            plugin.adventure().player(player).sendMessage(buttonLine);
            return;
        } catch (Exception ignored) {
            // Fall through to Spigot method
        }

        // Method 2: Spigot BungeeCord Chat API (fallback for non-Paper)
        try {
            net.md_5.bungee.api.chat.TextComponent prefix =
                    new net.md_5.bungee.api.chat.TextComponent("  ► ");
            prefix.setColor(net.md_5.bungee.api.ChatColor.DARK_AQUA);

            net.md_5.bungee.api.chat.TextComponent link =
                    new net.md_5.bungee.api.chat.TextComponent(url);
            link.setColor(net.md_5.bungee.api.ChatColor.AQUA);
            link.setUnderlined(true);
            link.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL, url));
            link.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.BaseComponent[]{
                            new net.md_5.bungee.api.chat.TextComponent("Click to open dashboard")
                    }));

            prefix.addExtra(link);
            player.spigot().sendMessage(prefix);

            // Button
            net.md_5.bungee.api.chat.TextComponent button =
                    new net.md_5.bungee.api.chat.TextComponent("  [Open Dashboard]");
            button.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            button.setBold(true);
            button.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL, url));
            button.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.BaseComponent[]{
                            new net.md_5.bungee.api.chat.TextComponent("Click to open in browser")
                    }));
            player.spigot().sendMessage(button);
        } catch (Exception e) {
            // Last resort: plain text
            player.sendMessage(MessageManager.color("&b" + url));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return List.of("<ip/domain>", "localhost");
        return List.of();
    }
}