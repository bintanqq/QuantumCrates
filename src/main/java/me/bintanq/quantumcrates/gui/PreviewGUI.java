package me.bintanq.quantumcrates.gui;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.model.Crate;
import me.bintanq.quantumcrates.model.reward.Reward;
import me.bintanq.quantumcrates.processor.RewardProcessor;
import me.bintanq.quantumcrates.util.MessageManager;
import me.bintanq.quantumcrates.util.PhysicalKeyItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * PreviewGUI — inventory GUI that displays all crate rewards.
 *
 * All rarity-dependent logic (colors, border materials, ordering)
 * delegates to RarityManager — zero hardcoded tier names.
 */
public class PreviewGUI {

    public static final String TITLE_PREFIX = "\u00A70\u00A7lPreview \u00A78\u00BB ";

    private static final int[] REWARD_SLOTS = buildRewardSlots();
    private static final int REWARD_PER_PAGE = REWARD_SLOTS.length;

    private static final int SLOT_PREV  = 46;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_INFO  = 50;
    private static final int SLOT_NEXT  = 52;

    private final QuantumCrates   plugin;
    private final RewardProcessor rewardProcessor;

    public PreviewGUI(QuantumCrates plugin, RewardProcessor rewardProcessor) {
        this.plugin          = plugin;
        this.rewardProcessor = rewardProcessor;
    }

    /* ─────────────────────── Open ─────────────────────── */

    public void open(Player player, Crate crate) {
        open(player, crate, 0);
    }

    public void open(Player player, Crate crate, int page) {
        Crate.PreviewConfig cfg = crate.getPreview();

        List<Reward> sorted     = sortRewards(crate.getRewards(), cfg.getSortOrder());
        double       totalWeight = crate.getTotalWeight();
        int totalPages = Math.max(1, (int) Math.ceil((double) sorted.size() / REWARD_PER_PAGE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        String crateName = colorize(crate.getDisplayName() != null
                ? crate.getDisplayName() : crate.getId());

        String title;
        if (cfg.getTitle() != null && !cfg.getTitle().isEmpty()) {
            title = colorize(cfg.getTitle())
                    .replace("{crate}", crateName)
                    .replace("{page}", String.valueOf(page + 1))
                    .replace("{pages}", String.valueOf(totalPages));
        } else if (totalPages > 1) {
            title = MessageManager.getGui("preview-title-paged",
                    "{crate}", crateName,
                    "{page}", String.valueOf(page + 1),
                    "{pages}", String.valueOf(totalPages));
        } else {
            title = MessageManager.getGui("preview-title-default",
                    "{crate}", crateName);
        }

        if (title.length() > 32) title = title.substring(0, 32);

        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Border: RarityManager resolves the material
        Material borderMat = resolveBorderMaterial(cfg, crate);
        fillBorder(inv, borderMat);

        int start = page * REWARD_PER_PAGE;
        int end   = Math.min(start + REWARD_PER_PAGE, sorted.size());
        for (int i = start; i < end; i++) {
            inv.setItem(REWARD_SLOTS[i - start],
                    buildRewardItem(sorted.get(i), totalWeight, cfg));
        }

        Material prevMat  = parseMaterial(cfg.getPrevButtonMaterial(),  Material.ARROW);
        Material closeMat = parseMaterial(cfg.getCloseButtonMaterial(), Material.BARRIER);
        Material nextMat  = parseMaterial(cfg.getNextButtonMaterial(),  Material.ARROW);

        if (page > 0) {
            inv.setItem(SLOT_PREV, makeButton(prevMat,
                    MessageManager.getGui("prev-button-name"),
                    List.of(MessageManager.getGui("prev-button-lore-1",
                            "{page}", String.valueOf(page),
                            "{pages}", String.valueOf(totalPages)))));
        } else {
            inv.setItem(SLOT_PREV, makeFiller(Material.GRAY_STAINED_GLASS_PANE));
        }

        inv.setItem(SLOT_CLOSE, makeButton(closeMat,
                MessageManager.getGui("close-button-name"),
                List.of(MessageManager.getGui("close-button-lore-1"))));

        inv.setItem(SLOT_INFO, buildInfoItem(player, crate, cfg, page, totalPages));

        if (page < totalPages - 1) {
            inv.setItem(SLOT_NEXT, makeButton(nextMat,
                    MessageManager.getGui("next-button-name"),
                    List.of(MessageManager.getGui("next-button-lore-1",
                            "{page}", String.valueOf(page + 2),
                            "{pages}", String.valueOf(totalPages)))));
        } else {
            inv.setItem(SLOT_NEXT, makeFiller(Material.GRAY_STAINED_GLASS_PANE));
        }

        player.openInventory(inv);
    }

    /* ─────────────────────── Reward Item ─────────────────────── */

    private ItemStack buildRewardItem(Reward reward, double totalWeight, Crate.PreviewConfig cfg) {
        ItemStack base = null;
        if (cfg.isShowActualItem()) {
            try { base = rewardProcessor.materializeItem(reward); } catch (Exception ignored) {}
        }
        if (base == null || base.getType().isAir()) base = new ItemStack(Material.PAPER);

        ItemStack display = base.clone();
        display.setAmount(Math.max(1, reward.getAmount()));

        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;

        // RarityManager provides color — no hardcoded switch
        String rarityColor = plugin.getRarityManager().getColor(reward.getRarity());
        meta.setDisplayName(reward.getDisplayName() != null
                ? colorize(reward.getDisplayName())
                : rarityColor + reward.getId());

        List<String> lore = new ArrayList<>();

        if (reward.getLore() != null && !reward.getLore().isEmpty()) {
            reward.getLore().forEach(l -> lore.add(colorize(l)));
            String divider = MessageManager.getGui("reward-lore-divider");
            if (!divider.isEmpty()) lore.add(divider);
        }

        double pct    = reward.calculatePercentage(totalWeight);
        String pctStr = formatChance(pct);

        if (cfg.isShowChance()) {
            String line = colorize(cfg.getChanceFormat())
                    .replace("{chance}", pctStr)
                    .replace("{rarity}", rarityColor + plugin.getRarityManager().get(reward.getRarity()).getDisplayName())
                    .replace("{weight}", String.format("%.2f", reward.getWeight()))
                    .replace("{amount}", String.valueOf(reward.getAmount()));
            lore.add(line);
        }

        if (cfg.isShowWeight()) {
            lore.add(MessageManager.getGui("reward-weight-line",
                    "{weight}", String.format("%.2f", reward.getWeight())));
        }

        lore.add(MessageManager.getGui("reward-rarity-line",
                "{rarity}", rarityColor + plugin.getRarityManager().get(reward.getRarity()).getDisplayName()));

        if (reward.getAmount() > 1) {
            lore.add(MessageManager.getGui("reward-amount-line",
                    "{amount}", String.valueOf(reward.getAmount())));
        }

        if (reward.hasCommands()) {
            String tag = MessageManager.getGui("reward-command-tag");
            if (!tag.isEmpty()) lore.add(tag);
        }

        if (reward.isBroadcast()) {
            String tag = MessageManager.getGui("reward-broadcast-tag");
            if (!tag.isEmpty()) lore.add(tag);
        }

        for (String fl : cfg.getRewardFooterLore()) {
            lore.add(colorize(fl)
                    .replace("{chance}", pctStr)
                    .replace("{rarity}", reward.getRarity())
                    .replace("{weight}", String.format("%.2f", reward.getWeight()))
                    .replace("{amount}", String.valueOf(reward.getAmount())));
        }

        meta.setLore(lore);
        display.setItemMeta(meta);
        return display;
    }

    /* ─────────────────────── Info Item ─────────────────────── */

    private ItemStack buildInfoItem(Player player, Crate crate, Crate.PreviewConfig cfg,
                                    int page, int totalPages) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(MessageManager.getGui("info-item-name"));

        List<String> lore = new ArrayList<>();

        String divider = MessageManager.getGui("info-divider");
        if (!divider.isEmpty()) lore.add(divider);

        String crateName = colorize(crate.getDisplayName() != null
                ? crate.getDisplayName() : crate.getId());
        lore.add(MessageManager.getGui("info-crate-label", "{crate}", crateName));
        lore.add(MessageManager.getGui("info-total-rewards",
                "{count}", String.valueOf(crate.getRewards().size())));

        if (totalPages > 1) {
            lore.add(MessageManager.getGui("info-page-label",
                    "{page}", String.valueOf(page + 1),
                    "{pages}", String.valueOf(totalPages)));
        }

        if (cfg.isShowKeyBalance() && !crate.getRequiredKeys().isEmpty()) {
            String keysHeader = MessageManager.getGui("info-keys-header");
            if (!keysHeader.isEmpty()) lore.add(keysHeader);
            lore.add(MessageManager.getGui("info-keys-title"));

            for (Crate.KeyRequirement req : crate.getRequiredKeys()) {
                int balance = getKeyBalance(player, req);
                int needed  = req.getAmount();
                boolean ok  = balance >= needed;
                String entryKey = ok ? "info-key-entry-ok" : "info-key-entry-missing";
                lore.add(MessageManager.getGui(entryKey,
                        "{key}", req.getKeyId(),
                        "{type}", req.getType().name().toLowerCase(),
                        "{balance}", String.valueOf(balance),
                        "{needed}", String.valueOf(needed)));
            }
        }

        if (cfg.isShowPity() && crate.getPity().isEnabled()) {
            int pity = plugin.getPlayerDataManager().getPity(player.getUniqueId(), crate.getId());
            int max  = crate.getPity().getThreshold();
            int soft = crate.getPity().getSoftPityStart();

            String pityHeader = MessageManager.getGui("info-pity-header");
            if (!pityHeader.isEmpty()) lore.add(pityHeader);

            String statusKey;
            if (pity >= max)       statusKey = "info-pity-status-hard";
            else if (pity >= soft) statusKey = "info-pity-status-soft";
            else                   statusKey = "info-pity-status-normal";

            String status = MessageManager.getGui(statusKey);
            lore.add(MessageManager.getGui("info-pity-label",
                    "{pity}", String.valueOf(pity),
                    "{pity_max}", String.valueOf(max))
                    + "  " + status);

            lore.add("  " + buildPityBar(pity, max, soft));
        }

        String controlsDivider = MessageManager.getGui("info-controls-divider");
        if (!controlsDivider.isEmpty()) lore.add(controlsDivider);
        addIfNotEmpty(lore, MessageManager.getGui("info-controls-left"));
        addIfNotEmpty(lore, MessageManager.getGui("info-controls-right"));
        addIfNotEmpty(lore, MessageManager.getGui("info-controls-shift"));

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /* ─────────────────────── Border ─────────────────────── */

    private void fillBorder(Inventory inv, Material mat) {
        ItemStack filler = makeFiller(mat);
        for (int i = 0;  i < 9;  i++) inv.setItem(i, filler);
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);
        for (int row = 1; row <= 4; row++) {
            inv.setItem(row * 9,     filler);
            inv.setItem(row * 9 + 8, filler);
        }
    }

    private Material resolveBorderMaterial(Crate.PreviewConfig cfg, Crate crate) {
        if (cfg.getBorderMaterial() != null && !cfg.getBorderMaterial().isEmpty()) {
            Material m = parseMaterial(cfg.getBorderMaterial(), null);
            if (m != null) return m;
        }
        // Auto: find highest rarity in this crate, ask RarityManager for its border
        String highestRarity = crate.getRewards().stream()
                .map(Reward::getRarity)
                .max(Comparator.comparingInt(r -> plugin.getRarityManager().getOrder(r)))
                .orElse(plugin.getRarityManager().getLowestId());

        return plugin.getRarityManager().getBorderMaterial(highestRarity);
    }

    /* ─────────────────────── Sorting ─────────────────────── */

    private List<Reward> sortRewards(List<Reward> rewards, Crate.PreviewConfig.SortOrder order) {
        List<Reward> sorted = new ArrayList<>(rewards);
        switch (order) {
            case RARITY_DESC -> sorted.sort(Comparator
                    .comparingInt((Reward r) -> plugin.getRarityManager().getOrder(r.getRarity()))
                    .reversed()
                    .thenComparingDouble(Reward::getWeight).reversed());
            case RARITY_ASC  -> sorted.sort(Comparator
                    .comparingInt((Reward r) -> plugin.getRarityManager().getOrder(r.getRarity()))
                    .thenComparingDouble(Reward::getWeight));
            case WEIGHT_DESC -> sorted.sort(Comparator.comparingDouble(Reward::getWeight).reversed());
            case WEIGHT_ASC  -> sorted.sort(Comparator.comparingDouble(Reward::getWeight));
            case CONFIG_ORDER -> { /* unsorted */ }
        }
        return sorted;
    }

    /* ─────────────────────── Key Balance ─────────────────────── */

    private int getKeyBalance(Player player, Crate.KeyRequirement req) {
        return switch (req.getType()) {
            case VIRTUAL  -> plugin.getKeyManager().getVirtualBalance(player, req.getKeyId());
            case PHYSICAL -> {
                int count = 0;
                for (ItemStack item : player.getInventory().getContents()) {
                    if (PhysicalKeyItem.isKey(plugin, item, req.getKeyId()))
                        count += item.getAmount();
                }
                yield count;
            }
            case MMOITEMS -> {
                var h = plugin.getHookManager().getMmoItemsHook();
                yield h != null ? h.countKey(player, req.getKeyId()) : 0;
            }
            case ITEMSADDER -> {
                var h = plugin.getHookManager().getItemsAdderHook();
                yield h != null ? h.countKey(player, req.getKeyId()) : 0;
            }
            case ORAXEN -> {
                var h = plugin.getHookManager().getOraxenHook();
                yield h != null ? h.countKey(player, req.getKeyId()) : 0;
            }
        };
    }

    /* ─────────────────────── Pity Bar ─────────────────────── */

    private String buildPityBar(int current, int max, int soft) {
        int bars     = 10;
        int filled   = max > 0 ? (int) Math.round((double) current / max * bars) : 0;
        int softMark = max > 0 ? (int) Math.round((double) soft / max * bars)    : 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bars; i++) {
            sb.append(i < filled ? (i >= softMark ? "\u00A7c" : "\u00A7e") : "\u00A78").append("\u2588");
        }
        return sb.toString();
    }

    /* ─────────────────────── Item Factories ─────────────────────── */

    private ItemStack makeFiller(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName("\u00A7r"); item.setItemMeta(meta); }
        return item;
    }

    private ItemStack makeButton(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /* ─────────────────────── Slot Index Builder ─────────────────────── */

    private static int[] buildRewardSlots() {
        int[] slots = new int[28];
        int idx = 0;
        for (int row = 1; row <= 4; row++)
            for (int col = 1; col <= 7; col++)
                slots[idx++] = row * 9 + col;
        return slots;
    }

    /* ─────────────────────── Helpers ─────────────────────── */

    private void addIfNotEmpty(List<String> list, String value) {
        if (value != null && !value.isEmpty()) list.add(value);
    }

    private String formatChance(double pct) {
        if (pct == 0)     return "0%";
        if (pct < 0.0001) return "< 0.0001%";
        if (pct < 0.01)   return String.format("%.4f%%", pct);
        return                   String.format("%.2f%%", pct);
    }

    private Material parseMaterial(String name, Material fallback) {
        if (name == null || name.isEmpty()) return fallback;
        Material m = Material.matchMaterial(name.toUpperCase());
        return m != null ? m : fallback;
    }

    private String colorize(String s) {
        return s == null ? "" : s.replace("&", "\u00A7");
    }

    /* ─────────────────────── Static Helpers (used by GUIListener) ─────────────────────── */

    public static boolean isPreviewInventory(String title) {
        return title != null && title.startsWith(TITLE_PREFIX);
    }

    public static int parsePageFromTitle(String title) {
        if (title == null || !title.contains("[")) return 0;
        try {
            int start = title.lastIndexOf('[') + 1;
            int slash = title.indexOf('/', start);
            if (slash < 0) slash = title.indexOf('\u00A7', start);
            String raw = title.substring(start, slash).replaceAll("\u00A7.", "").trim();
            return Integer.parseInt(raw) - 1;
        } catch (Exception e) { return 0; }
    }
}