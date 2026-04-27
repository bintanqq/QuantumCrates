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
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * QuantumCratesCommand — /qc command handler.
 * ZERO hardcoded messages — semua via MessageManager.
 *
 *  /qc reload
 *  /qc give <player> <keyId> <amount>   ← satu command, auto virtual/physical dari config
 *  /qc open <crateId>
 *  /qc massopen <crateId> [count]
 *  /qc preview <crateId>
 *  /qc info <crateId>
 *  /qc list
 *  /qc setloc <crateId>
 *  /qc pity <player> <crateId>
 *  /qc resetpity <player> <crateId>
 *  /qc keys <player> <keyId>
 */
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
            case "give"      -> cmdGive(sender, args);       // unified virtual+physical
            case "open"      -> cmdOpen(sender, args);
            case "massopen"  -> cmdMassOpen(sender, args);
            case "preview"   -> cmdPreview(sender, args);
            case "info"      -> cmdInfo(sender, args);
            case "list"      -> cmdList(sender);
            case "setloc"    -> cmdSetLoc(sender, args);
            case "pity"      -> cmdPity(sender, args);
            case "resetpity" -> cmdResetPity(sender, args);
            case "keys"      -> cmdCheckKeys(sender, args);
            case "web"       -> new WebCommand(plugin).onCommand(sender, cmd, label,
                                    Arrays.copyOfRange(args, 1, args.length));
            default          -> sendHelp(sender);
        }
        return true;
    }

    /* ─────────────────────── /qc reload ─────────────────────── */

    private void cmdReload(CommandSender sender) {
        if (!sender.hasPermission("quantumcrates.admin")) { MessageManager.sendNoPermission(sender); return; }
        ReloadUtil.reloadAll(plugin);
        MessageManager.send(sender, "reload-success");
    }

    /* ─────────────────────── /qc give — unified ─────────────────────── */

    /**
     * /qc give <player> <keyId> <amount>
     *
     * Behavior (virtual vs physical) ditentukan oleh config keys.mode.
     * Admin tidak perlu tahu bedanya — satu command, satu sistem.
     */
    private void cmdGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("quantumcrates.key.give")) { MessageManager.sendNoPermission(sender); return; }
        if (args.length < 4) {
            MessageManager.send(sender, "usage-give");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { MessageManager.sendPlayerNotFound(sender, args[1]); return; }

        String keyId;
        int amount;
        try {
            keyId  = args[2];
            amount = Integer.parseInt(args[3]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            MessageManager.sendInvalidNumber(sender);
            return;
        }

        plugin.getKeyManager().giveKey(target, keyId, amount);
        MessageManager.send(sender, "key-given-sender",
                "{amount}", String.valueOf(amount),
                "{key}", keyId,
                "{player}", target.getName());
    }

    /* ─────────────────────── /qc open ─────────────────────── */

    private void cmdOpen(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { MessageManager.sendPlayerOnly(sender); return; }
        if (!player.hasPermission("quantumcrates.admin")) { MessageManager.sendNoPermission(sender); return; }
        if (args.length < 2) { MessageManager.send(sender, "usage-open"); return; }
        crateManager().openCrate(player, args[1]);
    }

    /* ─────────────────────── /qc massopen ─────────────────────── */

    private void cmdMassOpen(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { MessageManager.sendPlayerOnly(sender); return; }
        if (!player.hasPermission("quantumcrates.massopen")) { MessageManager.sendNoPermission(sender); return; }
        if (args.length < 2) { MessageManager.send(sender, "usage-massopen"); return; }
        int count = args.length >= 3 ? parseIntSafe(args[2], -1) : -1;
        crateManager().massOpen(player, args[1], count);
    }

    /* ─────────────────────── /qc preview ─────────────────────── */

    private void cmdPreview(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { MessageManager.sendPlayerOnly(sender); return; }
        if (!player.hasPermission("quantumcrates.preview")) { MessageManager.sendNoPermission(sender); return; }
        if (args.length < 2) { MessageManager.send(sender, "usage-preview"); return; }
        Crate crate = crateManager().getCrate(args[1]);
        if (crate == null) { MessageManager.sendCrateNotFound(sender, args[1]); return; }
        new me.bintanq.quantumcrates.gui.PreviewGUI(plugin, plugin.getRewardProcessor()).open(player, crate);
    }

    /* ─────────────────────── /qc info ─────────────────────── */

    private void cmdInfo(CommandSender sender, String[] args) {
        if (args.length < 2) { MessageManager.send(sender, "usage-info"); return; }
        Crate crate = crateManager().getCrate(args[1]);
        if (crate == null) { MessageManager.sendCrateNotFound(sender, args[1]); return; }

        MessageManager.send(sender, "info-header", "{crate}", crate.getDisplayName() != null ? crate.getDisplayName() : crate.getId());
        MessageManager.send(sender, "info-id", "{crate}", crate.getId());
        MessageManager.send(sender, crate.isEnabled() ? "info-status-on" : "info-status-off");
        MessageManager.send(sender, "info-rewards", "{count}", String.valueOf(crate.getRewards().size()));
        MessageManager.send(sender, "info-total-weight", "{weight}", String.format("%.2f", crate.getTotalWeight()));
        MessageManager.send(sender, "info-cooldown", "{time}", crate.getCooldownMs() > 0 ? TimeUtil.formatDuration(crate.getCooldownMs()) : "Tidak ada");

        if (crate.getPity().isEnabled()) {
            MessageManager.send(sender, "info-pity-on",
                    "{max}", String.valueOf(crate.getPity().getThreshold()),
                    "{soft}", String.valueOf(crate.getPity().getSoftPityStart()));
        } else {
            MessageManager.send(sender, "info-pity-off");
        }

        if (crate.isMassOpenEnabled()) {
            String limit = crate.getMassOpenLimit() < 0 ? "unlimited" : String.valueOf(crate.getMassOpenLimit());
            MessageManager.send(sender, "info-massopen-on", "{limit}", limit);
        } else {
            MessageManager.send(sender, "info-massopen-off");
        }

        MessageManager.send(sender, "info-schedule",
                "{schedule}", crate.getSchedule() != null ? crate.getSchedule().getNextOpenDescription() : "Selalu buka");
        MessageManager.send(sender, crate.isCurrentlyOpenable() ? "info-openable" : "info-not-openable");

        if (crate.getLocation() != null) {
            var l = crate.getLocation();
            MessageManager.send(sender, "info-location",
                    "{world}", l.world,
                    "{x}", String.valueOf((int)l.x),
                    "{y}", String.valueOf((int)l.y),
                    "{z}", String.valueOf((int)l.z));
        } else {
            MessageManager.send(sender, "info-no-location", "{crate}", crate.getId());
        }

        if (!crate.getRequiredKeys().isEmpty()) {
            MessageManager.send(sender, "info-keys-header");
            crate.getRequiredKeys().forEach(k -> MessageManager.send(sender, "info-key-entry",
                    "{key}", k.getKeyId(),
                    "{amount}", String.valueOf(k.getAmount()),
                    "{type}", k.getType().name().toLowerCase()));
        }
    }

    /* ─────────────────────── /qc list ─────────────────────── */

    private void cmdList(CommandSender sender) {
        Collection<Crate> crates = crateManager().getAllCrates();
        if (crates.isEmpty()) {
            MessageManager.send(sender, "list-empty");
            return;
        }
        MessageManager.send(sender, "list-header", "{count}", String.valueOf(crates.size()));
        crates.forEach(c -> MessageManager.send(sender, "list-entry",
                "{id}", c.getId(),
                "{name}", c.getDisplayName() != null ? c.getDisplayName() : c.getId(),
                "{status}", c.isEnabled() ? MessageManager.getRaw("list-status-on") : MessageManager.getRaw("list-status-off"),
                "{rewards}", String.valueOf(c.getRewards().size()),
                "{keys}", String.valueOf(c.getRequiredKeys().size())));
    }

    /* ─────────────────────── /qc setloc ─────────────────────── */

    private void cmdSetLoc(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { MessageManager.sendPlayerOnly(sender); return; }
        if (!player.hasPermission("quantumcrates.admin")) { MessageManager.sendNoPermission(sender); return; }
        if (args.length < 2) { MessageManager.send(sender, "usage-setloc"); return; }

        Crate crate = crateManager().getCrate(args[1]);
        if (crate == null) { MessageManager.sendCrateNotFound(sender, args[1]); return; }

        var targeted = player.getTargetBlockExact(5);
        if (targeted == null) {
            MessageManager.send(sender, "setloc-no-target");
            return;
        }

        var loc = targeted.getLocation();
        crate.setLocation(new Crate.SerializableLocation(
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
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

    /* ─────────────────────── /qc pity ─────────────────────── */

    private void cmdPity(CommandSender sender, String[] args) {
        if (!sender.hasPermission("quantumcrates.admin")) { MessageManager.sendNoPermission(sender); return; }
        if (args.length < 3) { MessageManager.send(sender, "usage-pity"); return; }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { MessageManager.sendPlayerNotFound(sender, args[1]); return; }

        String crateId = args[2];
        Crate crate = crateManager().getCrate(crateId);
        int pity = plugin.getPlayerDataManager().getPity(target.getUniqueId(), crateId);
        int max  = crate != null ? crate.getPity().getThreshold() : 0;

        MessageManager.send(sender, "pity-info",
                "{player}", target.getName(),
                "{crate}", crateId,
                "{current}", String.valueOf(pity),
                "{max}", String.valueOf(max));
    }

    /* ─────────────────────── /qc resetpity ─────────────────────── */

    private void cmdResetPity(CommandSender sender, String[] args) {
        if (!sender.hasPermission("quantumcrates.admin")) { MessageManager.sendNoPermission(sender); return; }
        if (args.length < 3) { MessageManager.send(sender, "usage-resetpity"); return; }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { MessageManager.sendPlayerNotFound(sender, args[1]); return; }

        plugin.getPlayerDataManager().resetPity(target.getUniqueId(), args[2]);
        MessageManager.send(sender, "pity-reset-done",
                "{player}", target.getName(), "{crate}", args[2]);
    }

    /* ─────────────────────── /qc keys ─────────────────────── */

    private void cmdCheckKeys(CommandSender sender, String[] args) {
        if (!sender.hasPermission("quantumcrates.admin")) { MessageManager.sendNoPermission(sender); return; }
        if (args.length < 3) { MessageManager.send(sender, "usage-keys"); return; }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { MessageManager.sendPlayerNotFound(sender, args[1]); return; }

        String keyId = args[2];
        plugin.getDatabaseManager().getVirtualKeys(target.getUniqueId(), keyId)
              .thenAccept(balance -> MessageManager.send(sender, "keys-balance",
                      "{player}", target.getName(),
                      "{key}", keyId,
                      "{balance}", String.valueOf(balance)));
    }

    /* ─────────────────────── Help ─────────────────────── */

    private void sendHelp(CommandSender sender) {
        MessageManager.send(sender, "help-header");
        MessageManager.send(sender, "help-reload");
        MessageManager.send(sender, "help-give");
        MessageManager.send(sender, "help-open");
        MessageManager.send(sender, "help-massopen");
        MessageManager.send(sender, "help-preview");
        MessageManager.send(sender, "help-info");
        MessageManager.send(sender, "help-list");
        MessageManager.send(sender, "help-setloc");
        MessageManager.send(sender, "help-pity");
        MessageManager.send(sender, "help-resetpity");
        MessageManager.send(sender, "help-keys-cmd");
        MessageManager.send(sender, "help-web");
        MessageManager.send(sender, "help-controls-header");
        MessageManager.send(sender, "help-ctrl-left");
        MessageManager.send(sender, "help-ctrl-right");
        MessageManager.send(sender, "help-ctrl-shift");
    }

    /* ─────────────────────── Tab Completion ─────────────────────── */

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1)
            return filter(List.of("reload","give","open","massopen","preview",
                    "info","list","setloc","pity","resetpity","keys","web"), args[0]);

        return switch (args[0].toLowerCase()) {
            case "open","massopen","preview","info","setloc" ->
                    args.length == 2 ? filter(crateIds(), args[1]) : List.of();
            case "give","pity","resetpity","keys" ->
                    args.length == 2 ? filter(onlinePlayers(), args[1])
                  : args.length == 3 ? filter(knownKeyIds(), args[2])
                  : args.length == 4 && args[0].equalsIgnoreCase("give")
                        ? filter(List.of("1","5","10","32","64"), args[3])
                  : List.of();
            default -> List.of();
        };
    }

    /* ─────────────────────── Helpers ─────────────────────── */

    private me.bintanq.quantumcrates.manager.CrateManager crateManager() { return plugin.getCrateManager(); }

    private int parseIntSafe(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return fallback; }
    }

    private List<String> filter(List<String> opts, String input) {
        return opts.stream().filter(s -> s.toLowerCase().startsWith(input.toLowerCase())).collect(Collectors.toList());
    }

    private List<String> crateIds() { return new ArrayList<>(crateManager().getCrateRegistry().keySet()); }
    private List<String> onlinePlayers() { return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()); }
    private List<String> knownKeyIds()   { return new ArrayList<>(plugin.getKeyManager().getKnownKeyIds()); }
}
