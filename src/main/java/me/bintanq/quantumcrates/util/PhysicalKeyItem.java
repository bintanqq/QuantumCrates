package me.bintanq.quantumcrates.util;

import me.bintanq.quantumcrates.QuantumCrates;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * PhysicalKeyItem — factory + validator untuk physical crate key item.
 *
 * Key diidentifikasi via PersistentDataContainer tag "quantumcrates:qc_key_id".
 * Tidak bisa dipalsukan dengan rename biasa — harus dibuat via factory ini.
 *
 * Semua konfigurasi item (display name, lore, material, cmd) dibaca dari
 * config.yml section "keys.<keyId>" dan di-pass ke factory ini oleh KeyManager.
 */
public final class PhysicalKeyItem {

    private static final String PDC_KEY = "qc_key_id";

    private PhysicalKeyItem() {}

    /* ─────────────────────── Factory ─────────────────────── */

    /**
     * Buat physical key item berdasarkan definisi dari config.
     *
     * @param plugin          Plugin instance untuk NamespacedKey
     * @param keyId           ID key (e.g. "vip_key")
     * @param amount          Jumlah item
     * @param displayName     Display name (& color codes)
     * @param lore            Lore lines (& color codes)
     * @param material        Material item
     * @param customModelData -1 untuk tidak pakai
     */
    public static ItemStack create(
            QuantumCrates plugin,
            String keyId,
            int amount,
            String displayName,
            List<String> lore,
            Material material,
            int customModelData
    ) {
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(amount, 64)));
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(colorize(displayName));

        List<String> coloredLore = new ArrayList<>();
        if (lore != null) lore.forEach(l -> coloredLore.add(colorize(l)));
        // Footer untuk identifikasi cepat
        coloredLore.add("");
        coloredLore.add("§8ID: §7" + keyId);
        meta.setLore(coloredLore);

        if (customModelData > 0) meta.setCustomModelData(customModelData);

        // Brand PDC tag — ini yang bikin key bisa diidentifikasi
        NamespacedKey nsKey = new NamespacedKey(plugin, PDC_KEY);
        meta.getPersistentDataContainer().set(nsKey, PersistentDataType.STRING, keyId);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Overload default: display name dan lore minimal, material TRIPWIRE_HOOK.
     * Digunakan saat key tidak ada di config (fallback).
     */
    public static ItemStack create(QuantumCrates plugin, String keyId, int amount) {
        return create(
                plugin, keyId, amount,
                "&bCrate Key &8[&7" + keyId + "&8]",
                List.of("&7Gunakan untuk membuka crate."),
                Material.TRIPWIRE_HOOK,
                -1
        );
    }

    /* ─────────────────────── Identification ─────────────────────── */

    /**
     * Ambil key ID dari item. Return null jika bukan QC physical key.
     */
    public static String getKeyId(QuantumCrates plugin, ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        var meta = item.getItemMeta();
        if (meta == null) return null;
        NamespacedKey nsKey = new NamespacedKey(plugin, PDC_KEY);
        return meta.getPersistentDataContainer().get(nsKey, PersistentDataType.STRING);
    }

    /**
     * Return true jika item adalah physical key dengan keyId yang cocok.
     */
    public static boolean isKey(QuantumCrates plugin, ItemStack item, String keyId) {
        return keyId != null && keyId.equals(getKeyId(plugin, item));
    }

    private static String colorize(String s) {
        return s == null ? "" : s.replace("&", "\u00A7");
    }
}
