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
            MessageManager.send(sender, "web-disabled");
            return true;
        }

        String host = resolveHost(args);
        int    port = plugin.getConfig().getInt("web.port", 7420);

        String token = plugin.getWebServer().getTokenManager().generateToken();
        String url   = "http://" + host + ":" + port + "/?token=" + token;

        sender.sendMessage("");
        sender.sendMessage(MessageManager.getRaw("web-header-bar"));
        sender.sendMessage(MessageManager.getRaw("web-header-title"));
        sender.sendMessage(MessageManager.getRaw("web-header-bar"));
        sender.sendMessage(MessageManager.getRaw("web-link-label"));
        sender.sendMessage(MessageManager.color("&c" + url));
        sender.sendMessage(MessageManager.getRaw("web-link-hint"));
        sender.sendMessage(MessageManager.getRaw("web-header-bar"));
        sender.sendMessage("");

        if (sender instanceof Player player) {
            sendClickableLink(player, url);
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

    private void sendClickableLink(Player player, String url) {
        try {
            var msg = net.kyori.adventure.text.Component.text()
                    .append(net.kyori.adventure.text.Component.text("  ► "))
                    .append(net.kyori.adventure.text.Component.text("[Open Dashboard]")
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(url))
                            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                    net.kyori.adventure.text.Component.text(url))))
                    .build();
            player.sendMessage(msg);
        } catch (NoClassDefFoundError | Exception e) {
            player.sendMessage(MessageManager.color("&a[Dashboard] &7" + url));
            player.sendMessage(MessageManager.color("&7Click Link Above."));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return List.of("<ip/domain>", "localhost");
        return List.of();
    }
}