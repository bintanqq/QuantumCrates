package me.bintanq.quantumcrates.gui;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.model.Crate;
import me.bintanq.quantumcrates.model.reward.Reward;
import me.bintanq.quantumcrates.processor.RewardProcessor;
import me.bintanq.quantumcrates.util.PhysicalKeyItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * PreviewGUI — inventory GUI yang nampilin semua reward crate.
 *
 * Sepenuhnya customizable via Crate.PreviewConfig (field "preview" di crate JSON).
 *
 * Layout 6 baris (54 slot):
 * ┌──────────────────────────────────────┐
 * │ B  B  B  B  B  B  B  B  B          │ row 0 — border atas
 * │ B  r  r  r  r  r  r  r  B          │ row 1 ┐
 * │ B  r  r  r  r  r  r  r  B          │ row 2 │ reward area 28 slot
 * │ B  r  r  r  r  r  r  r  B          │ row 3 │ (4 baris × 7 kolom)
 * │ B  r  r  r  r  r  r  r  B          │ row 4 ┘
 * │ B [◄] B  B [✕] B [INFO] B [►] B   │ row 5 — navigasi
 * └──────────────────────────────────────┘
 *
 * Configurable per-crate (di crate JSON, field "preview"):
 *  - title            : judul GUI custom
 *  - sortOrder        : RARITY_DESC | RARITY_ASC | WEIGHT_DESC | WEIGHT_ASC | CONFIG_ORDER
 *  - borderMaterial   : nama material Bukkit, null = auto dari rarity tertinggi
 *  - showChance       : tampilkan % chance tiap reward
 *  - showWeight       : tampilkan weight numerik
 *  - showPity         : tampilkan pity counter di info item
 *  - showKeyBalance   : tampilkan saldo key di info item
 *  - chanceFormat     : format string "{chance}", "{rarity}", "{weight}", "{amount}"
 *  - rewardFooterLore : lore tambahan di bawah stats tiap reward
 *  - prevButtonMaterial / nextButtonMaterial / closeButtonMaterial
 */
public class PreviewGUI {

    public static final String TITLE_PREFIX = "§0§lPreview §8» ";

    // 28 slot reward area: row 1–4, col 1–7
    private static final int[] REWARD_SLOTS = buildRewardSlots();
    private static final int REWARD_PER_PAGE = REWARD_SLOTS.length; // 28

    // Slot navigasi (row 5)
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

        List<Reward> sorted    = sortRewards(crate.getRewards(), cfg.getSortOrder());
        double       totalWeight = crate.getTotalWeight();
        int totalPages = Math.max(1, (int) Math.ceil((double) sorted.size() / REWARD_PER_PAGE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        // ── Title ────────────────────────────────────────────────────
        String crateName = colorize(crate.getDisplayName() != null
                ? crate.getDisplayName() : crate.getId());
        String title;
        if (cfg.getTitle() != null && !cfg.getTitle().isEmpty()) {
            title = colorize(cfg.getTitle())
                    .replace("{crate}", crateName)
                    .replace("{page}", String.valueOf(page + 1))
                    .replace("{pages}", String.valueOf(totalPages));
        } else {
            title = TITLE_PREFIX + crateName
                    + (totalPages > 1 ? " §8[§7" + (page + 1) + "§8/§7" + totalPages + "§8]" : "");
        }

        Inventory inv = Bukkit.createInventory(null, 54, title);

        // ── Border ───────────────────────────────────────────────────
        Material borderMat = resolveBorderMaterial(cfg, crate);
        fillBorder(inv, borderMat);

        // ── Rewards ──────────────────────────────────────────────────
        int start = page * REWARD_PER_PAGE;
        int end   = Math.min(start + REWARD_PER_PAGE, sorted.size());
        for (int i = start; i < end; i++) {
            inv.setItem(REWARD_SLOTS[i - start], buildRewardItem(sorted.get(i), totalWeight, cfg));
        }

        // ── Navigasi row 5 ───────────────────────────────────────────
        // Prev
        Material prevMat = parseMaterial(cfg.getPrevButtonMaterial(), Material.ARROW);
        if (page > 0) {
            inv.setItem(SLOT_PREV, makeButton(prevMat,
                    "§e§l◄ Sebelumnya",
                    List.of("§7Halaman §e" + page + " §8/ §7" + totalPages)));
        } else {
            inv.setItem(SLOT_PREV, makeFiller(Material.GRAY_STAINED_GLASS_PANE));
        }

        // Close
        Material closeMat = parseMaterial(cfg.getCloseButtonMaterial(), Material.BARRIER);
        inv.setItem(SLOT_CLOSE, makeButton(closeMat, "§c§lTutup",
                List.of("§7Klik untuk menutup preview.")));

        // Info
        inv.setItem(SLOT_INFO, buildInfoItem(player, crate, cfg, page, totalPages));

        // Next
        Material nextMat = parseMaterial(cfg.getNextButtonMaterial(), Material.ARROW);
        if (page < totalPages - 1) {
            inv.setItem(SLOT_NEXT, makeButton(nextMat,
                    "§e§lSelanjutnya ►",
                    List.of("§7Halaman §e" + (page + 2) + " §8/ §7" + totalPages)));
        } else {
            inv.setItem(SLOT_NEXT, makeFiller(Material.GRAY_STAINED_GLASS_PANE));
        }

        player.openInventory(inv);
    }

    /* ─────────────────────── Reward Item ─────────────────────── */

    private ItemStack buildRewardItem(Reward reward, double totalWeight, Crate.PreviewConfig cfg) {
        // Materialize item asli atau fallback PAPER
        ItemStack base = null;
        if (cfg.isShowActualItem()) {
            try { base = rewardProcessor.materializeItem(reward); } catch (Exception ignored) {}
        }
        if (base == null || base.getType().isAir()) base = new ItemStack(Material.PAPER);

        ItemStack display = base.clone();
        display.setAmount(Math.max(1, reward.getAmount()));

        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;

        // Display name
        String rarityColor = rarityColor(reward.getRarity());
        meta.setDisplayName(reward.getDisplayName() != null
                ? colorize(reward.getDisplayName())
                : rarityColor + reward.getId());

        // Lore
        List<String> lore = new ArrayList<>();

        // Lore original dari reward config
        if (reward.getLore() != null && !reward.getLore().isEmpty()) {
            reward.getLore().forEach(l -> lore.add(colorize(l)));
            lore.add("§8──────────────────");
        }

        // Stats sesuai config
        double pct = reward.calculatePercentage(totalWeight);
        String pctStr = formatChance(pct);

        if (cfg.isShowChance()) {
            String line = colorize(cfg.getChanceFormat())
                    .replace("{chance}", pctStr)
                    .replace("{rarity}", rarityColor + reward.getRarity())
                    .replace("{weight}", String.format("%.2f", reward.getWeight()))
                    .replace("{amount}", String.valueOf(reward.getAmount()));
            lore.add(line);
        }

        if (cfg.isShowWeight()) {
            lore.add("§7Weight: §f" + String.format("%.2f", reward.getWeight()));
        }

        lore.add("§7Rarity: " + rarityColor + reward.getRarity());

        if (reward.getAmount() > 1) {
            lore.add("§7Jumlah: §fx" + reward.getAmount());
        }
        if (reward.hasCommands())  lore.add("§7+ §aCommand reward");
        if (reward.isBroadcast())  lore.add("§6✦ §7Broadcast ke server");

        // Footer lore kustom dari config
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

        meta.setDisplayName("§b§lInfo Crate");

        List<String> lore = new ArrayList<>();
        lore.add("§8──────────────────");
        lore.add("§7Crate: §f" + colorize(crate.getDisplayName() != null
                ? crate.getDisplayName() : crate.getId()));
        lore.add("§7Total Reward: §f" + crate.getRewards().size());
        if (totalPages > 1) lore.add("§7Halaman: §e" + (page + 1) + " §8/ §7" + totalPages);

        // Key balance
        if (cfg.isShowKeyBalance() && !crate.getRequiredKeys().isEmpty()) {
            lore.add("");
            lore.add("§7Key kamu:");
            for (Crate.KeyRequirement req : crate.getRequiredKeys()) {
                int balance = getKeyBalance(player, req);
                int needed  = req.getAmount();
                String balColor = balance >= needed ? "§a" : "§c";
                lore.add("  §8▸ §f" + req.getKeyId()
                        + " §8[" + req.getType().name().toLowerCase() + "§8]"
                        + " : " + balColor + balance + "§8/§7" + needed);
            }
        }

        // Pity
        if (cfg.isShowPity() && crate.getPity().isEnabled()) {
            int pity = plugin.getPlayerDataManager().getPity(player.getUniqueId(), crate.getId());
            int max  = crate.getPity().getThreshold();
            int soft = crate.getPity().getSoftPityStart();

            lore.add("");
            String status = pity >= max  ? "§c§lGARANSI RARE!"
                    : pity >= soft        ? "§e+Bonus Chance"
                    : "§7Normal";
            lore.add("§7Pity: §e" + pity + "§8/§e" + max + "  " + status);
            lore.add("  " + buildPityBar(pity, max, soft));
        }

        lore.add("§8──────────────────");
        lore.add("§7§oKlik Kiri  §8— §7§oPreview reward");
        lore.add("§7§oKlik Kanan §8— §7§oBuka crate");
        lore.add("§7§oShift+Kanan§8— §7§oMass open");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /* ─────────────────────── Border ─────────────────────── */

    private void fillBorder(Inventory inv, Material mat) {
        ItemStack filler = makeFiller(mat);
        // Row 0 dan row 5 penuh
        for (int i = 0;  i < 9;  i++) inv.setItem(i, filler);
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);
        // Kolom 0 dan 8 baris tengah
        for (int row = 1; row <= 4; row++) {
            inv.setItem(row * 9,     filler);
            inv.setItem(row * 9 + 8, filler);
        }
    }

    private Material resolveBorderMaterial(Crate.PreviewConfig cfg, Crate crate) {
        // Prioritas 1: konfigurasi eksplisit di PreviewConfig
        if (cfg.getBorderMaterial() != null && !cfg.getBorderMaterial().isEmpty()) {
            Material m = parseMaterial(cfg.getBorderMaterial(), null);
            if (m != null) return m;
        }
        // Prioritas 2: auto dari rarity tertinggi crate
        String highest = "COMMON";
        for (Reward r : crate.getRewards()) {
            if (rarityOrder(r.getRarity()) > rarityOrder(highest)) highest = r.getRarity();
        }
        return switch (highest.toUpperCase()) {
            case "MYTHIC"    -> Material.PURPLE_STAINED_GLASS_PANE;
            case "LEGENDARY" -> Material.ORANGE_STAINED_GLASS_PANE;
            case "EPIC"      -> Material.MAGENTA_STAINED_GLASS_PANE;
            case "RARE"      -> Material.BLUE_STAINED_GLASS_PANE;
            case "UNCOMMON"  -> Material.GREEN_STAINED_GLASS_PANE;
            default          -> Material.GRAY_STAINED_GLASS_PANE;
        };
    }

    /* ─────────────────────── Sorting ─────────────────────── */

    private List<Reward> sortRewards(List<Reward> rewards, Crate.PreviewConfig.SortOrder order) {
        List<Reward> sorted = new ArrayList<>(rewards);
        switch (order) {
            case RARITY_DESC  -> sorted.sort(Comparator.comparingInt((Reward r) ->
                    rarityOrder(r.getRarity())).reversed().thenComparingDouble(Reward::getWeight).reversed());
            case RARITY_ASC   -> sorted.sort(Comparator.comparingInt((Reward r) ->
                    rarityOrder(r.getRarity())).thenComparingDouble(Reward::getWeight));
            case WEIGHT_DESC  -> sorted.sort(Comparator.comparingDouble(Reward::getWeight).reversed());
            case WEIGHT_ASC   -> sorted.sort(Comparator.comparingDouble(Reward::getWeight));
            case CONFIG_ORDER -> { /* tidak di-sort */ }
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
            sb.append(i < filled ? (i >= softMark ? "§c" : "§e") : "§8").append("█");
        }
        return sb.toString();
    }

    /* ─────────────────────── Item Factories ─────────────────────── */

    private ItemStack makeFiller(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName("§r"); item.setItemMeta(meta); }
        return item;
    }

    private ItemStack makeButton(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); meta.setLore(lore); item.setItemMeta(meta); }
        return item;
    }

    /* ─────────────────────── Slot Index Builder ─────────────────────── */

    private static int[] buildRewardSlots() {
        int[] slots = new int[28]; // 4 baris × 7 kolom
        int idx = 0;
        for (int row = 1; row <= 4; row++)
            for (int col = 1; col <= 7; col++)
                slots[idx++] = row * 9 + col;
        return slots;
    }

    /* ─────────────────────── Helpers ─────────────────────── */

    private String formatChance(double pct) {
        if (pct == 0)          return "0%";
        if (pct < 0.0001)      return "< 0.0001%";
        if (pct < 0.01)        return String.format("%.4f%%", pct);
        if (pct < 1)           return String.format("%.2f%%", pct);
        return                        String.format("%.2f%%", pct);
    }

    private String rarityColor(String rarity) {
        if (rarity == null) return "§f";
        return switch (rarity.toUpperCase()) {
            case "COMMON"    -> "§f";
            case "UNCOMMON"  -> "§a";
            case "RARE"      -> "§9";
            case "EPIC"      -> "§5";
            case "LEGENDARY" -> "§6";
            case "MYTHIC"    -> "§d";
            default          -> "§7";
        };
    }

    private int rarityOrder(String rarity) {
        if (rarity == null) return 0;
        return switch (rarity.toUpperCase()) {
            case "UNCOMMON"  -> 1;
            case "RARE"      -> 2;
            case "EPIC"      -> 3;
            case "LEGENDARY" -> 4;
            case "MYTHIC"    -> 5;
            default          -> 0;
        };
    }

    private Material parseMaterial(String name, Material fallback) {
        if (name == null || name.isEmpty()) return fallback;
        Material m = Material.matchMaterial(name.toUpperCase());
        return m != null ? m : fallback;
    }

    private String colorize(String s) { return s == null ? "" : s.replace("&", "\u00A7"); }

    /* ─────────────────────── Static Helpers (GUIListener) ─────────────────────── */

    public static boolean isPreviewInventory(String title) {
        return title != null && title.startsWith(TITLE_PREFIX);
    }

    /** Parse page index (0-based) dari title inventory. */
    public static int parsePageFromTitle(String title) {
        if (title == null || !title.contains("[")) return 0;
        try {
            int start = title.lastIndexOf('[') + 1;
            int slash = title.indexOf('/', start);
            if (slash < 0) slash = title.indexOf('§', start); // handle color codes
            String raw = title.substring(start, slash).replaceAll("§.", "").trim();
            return Integer.parseInt(raw) - 1;
        } catch (Exception e) { return 0; }
    }
}
