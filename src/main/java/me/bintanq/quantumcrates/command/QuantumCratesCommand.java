package me.bintanq.quantumcrates.command;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.model.Crate;
import me.bintanq.quantumcrates.util.MessageManager;
import me.bintanq.quantumcrates.util.ReloadUtil;
import me.bintanq.quantumcrates.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class QuantumCratesCommand implements CommandExecutor, TabCompleter {

    private final QuantumCrates plugin;

    public QuantumCratesCommand(QuantumCrates plugin) {
        this.plugin = plugin;
        var cmd = plugin.getCommand("quantumcrates");
        Objects.requireNonNull(cmd).setExecutor(this);
        Objects.requireNonNull(cmd).setTabCompleter(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }
        switch (args[0].toLowerCase()) {
            case "reload"    -> cmdReload(sender);
            case "give"      -> cmdGive(sender, args);
            case "open"      -> cmdOpen(sender, args);
            case "info"      -> cmdInfo(sender, args);
            case "list"      -> cmdList(sender);
            case "setloc"    -> cmdSetLoc(sender, args);
            case "delloc" -> cmdDelLoc(sender, args);
            case "pity"      -> cmdPity(sender, args);
            case "resetpity" -> cmdResetPity(sender, args);
            case "resetlifetime" -> cmdResetLifetime(sender, args);
            case "keys"      -> cmdCheckKeys(sender, args);
            case "web"       -> new WebCommand(plugin).onCommand(sender, cmd, label,
                    Arrays.copyOfRange(args, 1, args.length));
            default          -> sendHelp(sender);
        }
        return true;
    }

    private void cmdReload(CommandSender sender) {
        if (!sender.hasPermission("quantumcrates.admin")) { MessageManager.sendNoPermission(sender); return; }
        ReloadUtil.reloadAll(plugin);
        MessageManager.send(sender, "reload-success");
    }

    private void cmdGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("quantumcrates.key.give")) {
            MessageManager.sendNoPermission(sender); return;
        }
        if (args.length < 4) { MessageManager.send(sender, "usage-give"); return; }

        String targetInput = args[1];
        String keyId;
        int amount;
        try {
            keyId  = args[2];
            amount = Integer.parseInt(args[3]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            MessageManager.sendInvalidNumber(sender); return;
        }

        Player online = Bukkit.getPlayer(targetInput);
        if (online != null) {
            plugin.getKeyManager().giveKey(online, keyId, amount);
            MessageManager.send(sender, "key-given-sender",
                    "{amount}", String.valueOf(amount), "{key}", keyId, "{player}", online.getName());
            return;
        }

        MessageManager.send(sender, "key-give-resolving", "{player}", targetInput);
        plugin.getKeyManager().giveKeyOffline(targetInput, keyId, amount)
                .thenAcceptAsync(success -> {
                    if (success) {
                        MessageManager.send(sender, "key-given-sender",
                                "{amount}", String.valueOf(amount), "{key}", keyId, "{player}", targetInput);
                    } else {
                        MessageManager.sendPlayerNotFound(sender, targetInput);
                    }
                }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable));
    }

    private void cmdOpen(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { MessageManager.sendPlayerOnly(sender); return; }
        if (!player.hasPermission("quantumcrates.admin")) { MessageManager.sendNoPermission(sender); return; }
        if (args.length < 2) { MessageManager.send(sender, "usage-open"); return; }
        crateManager().openCrate(player, args[1]);
    }

    private void cmdInfo(CommandSender sender, String[] args) {
        if (args.length < 2) { MessageManager.send(sender, "usage-info"); return; }
        Crate crate = crateManager().getCrate(args[1]);
        if (crate == null) { MessageManager.sendCrateNotFound(sender, args[1]); return; }

        String crateName = crate.getDisplayName() != null ? crate.getDisplayName() : crate.getId();
        MessageManager.send(sender, "info-header", "{crate}", crateName);
        MessageManager.send(sender, "info-id", "{crate}", crate.getId());
        MessageManager.send(sender, crate.isEnabled() ? "info-status-on" : "info-status-off");
        MessageManager.send(sender, "info-rewards", "{count}", String.valueOf(crate.getRewards().size()));
        MessageManager.send(sender, "info-total-weight", "{weight}", String.format("%.2f", crate.getTotalWeight()));
        MessageManager.send(sender, "info-cooldown", "{time}",
                crate.getCooldownMs() > 0 ? TimeUtil.formatDuration(crate.getCooldownMs()) : MessageManager.getRaw("cooldown-none"));

        if (crate.getPity().isEnabled()) {
            MessageManager.send(sender, "info-pity-on",
                    "{max}", String.valueOf(crate.getPity().getThreshold()),
                    "{soft}", String.valueOf(crate.getPity().getSoftPityStart()));
        } else {
            MessageManager.send(sender, "info-pity-off");
        }

        MessageManager.send(sender, crate.isMassOpenEnabled() ? "info-massopen-on" : "info-massopen-off",
                "{limit}", crate.getMassOpenLimit() < 0 ? "unlimited" : String.valueOf(crate.getMassOpenLimit()));
        MessageManager.send(sender, "info-schedule",
                "{schedule}", crate.getSchedule() != null ? crate.getSchedule().getNextOpenDescription() : MessageManager.getRaw("schedule-always"));
        MessageManager.send(sender, crate.isCurrentlyOpenable() ? "info-openable" : "info-not-openable");

        if (!crate.getLocations().isEmpty()) {
            MessageManager.send(sender, "info-location-count",
                    "{count}", String.valueOf(crate.getLocations().size()));
            crate.getLocations().forEach(l ->
                    MessageManager.send(sender, "info-location",
                            "{world}", l.world,
                            "{x}", String.valueOf((int) l.x),
                            "{y}", String.valueOf((int) l.y),
                            "{z}", String.valueOf((int) l.z)));
        } else {
            MessageManager.send(sender, "info-no-location", "{crate}", crate.getId());
        }

        if (!crate.getRequiredKeys().isEmpty()) {
            MessageManager.send(sender, "info-keys-header");
            crate.getRequiredKeys().forEach(k -> MessageManager.send(sender, "info-key-entry",
                    "{key}", k.getKeyId(), "{amount}", String.valueOf(k.getAmount()),
                    "{type}", k.getType().name().toLowerCase()));
        }
    }

    private void cmdList(CommandSender sender) {
        Collection<Crate> crates = crateManager().getAllCrates();
        if (crates.isEmpty()) { MessageManager.send(sender, "list-empty"); return; }
        MessageManager.send(sender, "list-header", "{count}", String.valueOf(crates.size()));
        crates.forEach(c -> MessageManager.send(sender, "list-entry",
                "{id}", c.getId(),
                "{name}", c.getDisplayName() != null ? c.getDisplayName() : c.getId(),
                "{status}", c.isEnabled() ? MessageManager.getRaw("list-status-on") : MessageManager.getRaw("list-status-off"),
                "{rewards}", String.valueOf(c.getRewards().size()),
                "{keys}", String.valueOf(c.getRequiredKeys().size())));
    }

    private void cmdSetLoc(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { MessageManager.sendPlayerOnly(sender); return; }
        if (!player.hasPermission("quantumcrates.admin")) { MessageManager.sendNoPermission(sender); return; }
        if (args.length < 2) { MessageManager.send(sender, "usage-setloc"); return; }

        Crate crate = crateManager().getCrate(args[1]);
        if (crate == null) { MessageManager.sendCrateNotFound(sender, args[1]); return; }

        var targeted = player.getTargetBlockExact(5);
        if (targeted == null) { MessageManager.send(sender, "setloc-no-target"); return; }

        var loc = targeted.getLocation();

        // Check no other crate already owns this block
        for (Crate other : crateManager().getAllCrates()) {
            if (other.getId().equals(crate.getId())) continue;
            if (other.hasLocationAt(loc.getWorld().getName(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
                MessageManager.send(sender, "setloc-already-taken", "{crate}", other.getId());
                return;
            }
        }

        Crate.SerializableLocation newLoc = new Crate.SerializableLocation(
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

        boolean added = crate.addLocation(newLoc);
        if (!added) {
            // Already exists — inform the admin
            sender.sendMessage(MessageManager.color("&eThis block is already a location for &6" + crate.getId() + "&e."));
            return;
        }
        crateManager().saveCrate(crate);

        if (plugin.getHologramManager() != null) plugin.getHologramManager().spawnHologram(crate);
        if (plugin.getParticleManager()  != null) plugin.getParticleManager().startIdleParticles(crate);

        MessageManager.send(sender, "setloc-success",
                "{crate}", crate.getId(),
                "{x}", String.valueOf(loc.getBlockX()),
                "{y}", String.valueOf(loc.getBlockY()),
                "{z}", String.valueOf(loc.getBlockZ()),
                "{world}", loc.getWorld().getName());
    }

    private void cmdDelLoc(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { MessageManager.sendPlayerOnly(sender); return; }
        if (!sender.hasPermission("quantumcrates.admin")) { MessageManager.sendNoPermission(sender); return; }
        if (args.length < 2) { MessageManager.send(sender, "usage-delloc"); return; }

        Crate crate = crateManager().getCrate(args[1]);
        if (crate == null) { MessageManager.sendCrateNotFound(sender, args[1]); return; }
        if (crate.getLocations().isEmpty()) {
            MessageManager.send(sender, "delloc-no-location", "{crate}", crate.getId()); return;
        }

        List<Crate.SerializableLocation> locs = crate.getLocations();

        if (locs.size() == 1 && args.length < 3) {
            performDelLoc(player, crate, 0);
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(MessageManager.color("&8&l━━━ &e" + crate.getId() + " &7Locations &8━━━"));
            for (int i = 0; i < locs.size(); i++) {
                Crate.SerializableLocation loc = locs.get(i);
                var component = net.kyori.adventure.text.Component.text()
                        .append(net.kyori.adventure.text.Component.text("  [" + i + "] ")
                                .color(net.kyori.adventure.text.format.NamedTextColor.GOLD))
                        .append(net.kyori.adventure.text.Component.text(
                                        loc.world + " " + (int)loc.x + ", " + (int)loc.y + ", " + (int)loc.z)
                                .color(net.kyori.adventure.text.format.NamedTextColor.GRAY))
                        .append(net.kyori.adventure.text.Component.text(" [Click to remove]")
                                .color(net.kyori.adventure.text.format.NamedTextColor.RED)
                                .decorate(net.kyori.adventure.text.format.TextDecoration.ITALIC)
                                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(
                                        "/qc delloc " + crate.getId() + " " + i))
                                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                        net.kyori.adventure.text.Component.text("Remove location " + i)
                                                .color(net.kyori.adventure.text.format.NamedTextColor.RED))))
                        .build();
                plugin.adventure().player(player).sendMessage(component);
            }
            sender.sendMessage(MessageManager.color("&7Or: &e/qc delloc " + crate.getId() + " all &7to remove all"));
            return;
        }

        String indexArg = args[2];
        if (indexArg.equalsIgnoreCase("all")) {
            crate.setLocations(new java.util.ArrayList<>());
            crateManager().saveCrate(crate);
            if (plugin.getHologramManager() != null) plugin.getHologramManager().removeHologram(crate.getId());
            if (plugin.getParticleManager() != null) plugin.getParticleManager().stopIdleParticles(crate.getId());
            MessageManager.send(sender, "delloc-success", "{crate}", crate.getId());
            return;
        }

        int idx;
        try { idx = Integer.parseInt(indexArg); }
        catch (NumberFormatException e) {
            MessageManager.send(sender, "delloc-index-invalid"); return;
        }
        performDelLoc(player, crate, idx);
    }

    private void performDelLoc(Player player, Crate crate, int idx) {
        if (!crate.removeLocation(idx)) {
            MessageManager.send(player, "delloc-index-invalid"); return;
        }
        crateManager().saveCrate(crate);
        if (plugin.getHologramManager() != null) plugin.getHologramManager().removeHologram(crate.getId());
        if (plugin.getParticleManager() != null) plugin.getParticleManager().stopIdleParticles(crate.getId());
        if (!crate.getLocations().isEmpty()) {
            if (plugin.getHologramManager() != null) plugin.getHologramManager().spawnHologram(crate);
            if (plugin.getParticleManager() != null) plugin.getParticleManager().startIdleParticles(crate);
        }
        MessageManager.send(player, "delloc-success", "{crate}", crate.getId());
    }

    private void cmdPity(CommandSender sender, String[] args) {
        if (!sender.hasPermission("quantumcrates.admin")) { MessageManager.sendNoPermission(sender); return; }
        if (args.length < 3) { MessageManager.send(sender, "usage-pity"); return; }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { MessageManager.sendPlayerNotFound(sender, args[1]); return; }

        String crateId = args[2];
        Crate crate = crateManager().getCrate(crateId);
        if (crate == null) { MessageManager.sendCrateNotFound(sender, crateId); return; }

        int pity = plugin.getPlayerDataManager().getPity(target.getUniqueId(), crateId);
        int max  = crate.getPity().getThreshold();
        int soft = crate.getPity().getSoftPityStart();
        boolean softActive = pity >= soft;
        boolean hardActive = pity >= max;

        MessageManager.send(sender, "pity-info",
                "{player}", target.getName(),
                "{crate}", crateId,
                "{current}", String.valueOf(pity),
                "{max}", String.valueOf(max),
                "{soft}", String.valueOf(soft),
                "{status}", hardActive
                        ? MessageManager.getRaw("pity-status-hard")
                        : softActive
                        ? MessageManager.getRaw("pity-status-soft")
                        : MessageManager.getRaw("pity-status-normal"));
    }

    private void cmdResetPity(CommandSender sender, String[] args) {
        if (!sender.hasPermission("quantumcrates.admin")) { MessageManager.sendNoPermission(sender); return; }
        if (args.length < 3) { MessageManager.send(sender, "usage-resetpity"); return; }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { MessageManager.sendPlayerNotFound(sender, args[1]); return; }

        String crateId = args[2];
        if (crateManager().getCrate(crateId) == null) { MessageManager.sendCrateNotFound(sender, crateId); return; }

        plugin.getPlayerDataManager().resetPity(target.getUniqueId(), crateId);
        MessageManager.send(sender, "pity-reset-done", "{player}", target.getName(), "{crate}", crateId);
    }

    private void cmdCheckKeys(CommandSender sender, String[] args) {
        if (!sender.hasPermission("quantumcrates.admin")) { MessageManager.sendNoPermission(sender); return; }
        if (args.length < 3) { MessageManager.send(sender, "usage-keys"); return; }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { MessageManager.sendPlayerNotFound(sender, args[1]); return; }

        plugin.getDatabaseManager().getVirtualKeys(target.getUniqueId(), args[2])
                .thenAccept(balance -> MessageManager.send(sender, "keys-balance",
                        "{player}", target.getName(), "{key}", args[2], "{balance}", String.valueOf(balance)));
    }

    private void cmdResetLifetime(CommandSender sender, String[] args) {
        if (!sender.hasPermission("quantumcrates.admin")) { MessageManager.sendNoPermission(sender); return; }
        if (args.length < 3) { MessageManager.send(sender, "usage-resetlifetime"); return; }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { MessageManager.sendPlayerNotFound(sender, args[1]); return; }

        String crateId = args[2];
        if (crateManager().getCrate(crateId) == null) { MessageManager.sendCrateNotFound(sender, crateId); return; }

        int before = plugin.getPlayerDataManager().getLifetimeOpens(target.getUniqueId(), crateId);
        plugin.getPlayerDataManager().resetLifetimeOpens(target.getUniqueId(), crateId);
        MessageManager.send(sender, "lifetime-reset-done",
                "{player}", target.getName(), "{crate}", crateId, "{count}", String.valueOf(before));
    }

    private void sendHelp(CommandSender sender) {
        MessageManager.send(sender, "help-header");
        for (String key : List.of("reload","give","open","info","list",
                "setloc","delloc","pity","resetpity","resetlifetime","keys-cmd","web",
                "controls-header","ctrl-left","ctrl-right","ctrl-shift")) {
            MessageManager.send(sender, "help-" + key);
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1)
            return filter(List.of("reload","give","open","info","list",
                    "setloc","delloc","pity","resetpity","resetlifetime","keys","web"), args[0]);

        return switch (args[0].toLowerCase()) {
            case "open","info","setloc","delloc" ->
                    args.length == 2 ? filter(crateIds(), args[1]) : List.of();
            case "give","pity","resetpity","resetlifetime","keys" ->
                    args.length == 2 ? filter(onlinePlayers(), args[1])
                            : args.length == 3 ? filter(
                            args[0].equalsIgnoreCase("give") ? knownKeyIds() : crateIds(),
                            args[2])
                            : args.length == 4 && args[0].equalsIgnoreCase("give")
                            ? filter(List.of("1","5","10","32","64"), args[3])
                            : List.of();
            default -> List.of();
        };
    }

    private me.bintanq.quantumcrates.manager.CrateManager crateManager() { return plugin.getCrateManager(); }

    private int parseIntSafe(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return fallback; }
    }

    private List<String> filter(List<String> opts, String input) {
        return opts.stream()
                .filter(s -> s.toLowerCase().startsWith(input.toLowerCase()))
                .collect(Collectors.toList());
    }

    private List<String> crateIds()      { return new ArrayList<>(crateManager().getCrateRegistry().keySet()); }
    private List<String> onlinePlayers() { return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()); }
    private List<String> knownKeyIds()   { return new ArrayList<>(plugin.getKeyManager().getKnownKeyIds()); }
}