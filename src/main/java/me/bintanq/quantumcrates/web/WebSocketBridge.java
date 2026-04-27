package me.bintanq.quantumcrates.web;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.log.CrateLog;
import me.bintanq.quantumcrates.model.Crate;
import me.bintanq.quantumcrates.serializer.GsonProvider;
import me.bintanq.quantumcrates.util.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * WebSocketBridge — event bus antara plugin Minecraft dan WebServer.
 *
 * Pattern: singleton, semua manager (CrateManager, KeyManager, dll)
 * memanggil WebSocketBridge.getInstance().broadcastXxx() setelah events.
 * WebSocketBridge meneruskan ke WebServer.broadcast() yang push ke semua WS client.
 *
 * Kalau WebServer belum start (atau belum ada client), events di-buffer
 * di in-memory queue dan di-drain saat client pertama connect.
 */
public class WebSocketBridge {

    /* ─────────────────────── Singleton ─────────────────────── */
    private static WebSocketBridge instance;
    public static WebSocketBridge getInstance() {
        if (instance == null) instance = new WebSocketBridge();
        return instance;
    }

    /* ─────────────────────── Event Types ─────────────────────── */
    public enum EventType {
        CRATE_OPEN,
        CRATE_UPDATE,
        CRATE_RELOAD,
        PLAYER_JOIN,
        PLAYER_QUIT,
        KEY_TRANSACTION,
        PITY_UPDATE,
        SERVER_STATS,
        PING,
        PONG
    }

    /* ─────────────────────── State ─────────────────────── */
    private WebServer webServer; // set saat WebServer start
    private final ConcurrentLinkedQueue<String> buffer = new ConcurrentLinkedQueue<>();

    private WebSocketBridge() {}

    /** Dipanggil dari QuantumCrates.onEnable() setelah WebServer.start() */
    public void setWebServer(WebServer ws) { this.webServer = ws; }

    /* ─────────────────────── Broadcast Events ─────────────────────── */

    /** Broadcast crate opening event — dipanggil dari CrateManager */
    public void broadcastCrateOpen(CrateLog log) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("uuid",          log.getUuid().toString());
        p.put("playerName",    log.getPlayerName());
        p.put("crateId",       log.getCrateId());
        p.put("rewardId",      log.getRewardId());
        p.put("rewardDisplay", log.getRewardDisplay());
        p.put("pityAtOpen",    log.getPityAtOpen());
        p.put("timestamp",     log.getTimestamp());
        p.put("world",         log.getWorld());
        p.put("x", log.getX()); p.put("y", log.getY()); p.put("z", log.getZ());
        dispatch(EventType.CRATE_OPEN, p);
    }

    /** Broadcast crate config update — null = full reload */
    public void broadcastCrateUpdate(Crate crate) {
        Map<String, Object> p = new LinkedHashMap<>();
        if (crate != null) {
            p.put("crateId", crate.getId());
            p.put("crate",   GsonProvider.getGson().toJsonTree(crate));
        } else {
            p.put("fullReload", true);
        }
        dispatch(EventType.CRATE_UPDATE, p);
    }

    /** Broadcast key transaction — dipanggil dari KeyManager.giveKey() */
    public void broadcastKeyTransaction(UUID uuid, String keyId, int delta, int balance) {
        dispatch(EventType.KEY_TRANSACTION, Map.of(
                "uuid",    uuid.toString(),
                "keyId",   keyId,
                "delta",   delta,
                "balance", balance
        ));
    }

    /** Broadcast pity update — dipanggil dari CrateManager setelah roll */
    public void broadcastPityUpdate(UUID uuid, String crateId, int newPity, boolean wasReset) {
        dispatch(EventType.PITY_UPDATE, Map.of(
                "uuid",     uuid.toString(),
                "crateId",  crateId,
                "pity",     newPity,
                "wasReset", wasReset
        ));
    }

    /** Broadcast server stats snapshot — dipanggil dari scheduled task */
    public void broadcastServerStats(int online, double tps, long openingsToday) {
        dispatch(EventType.SERVER_STATS, Map.of(
                "onlinePlayers", online,
                "tps",           Math.round(tps * 100.0) / 100.0,
                "openingsToday", openingsToday
        ));
    }

    /* ─────────────────────── Internal Dispatch ─────────────────────── */

    private void dispatch(EventType type, Map<String, Object> payload) {
        if (webServer != null) {
            webServer.broadcast(type, payload);
        } else {
            // Buffer — drain saat WebServer start
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", type.name());
            event.put("timestamp", System.currentTimeMillis());
            event.putAll(payload);
            buffer.offer(GsonProvider.getCompact().toJson(event));
            while (buffer.size() > 500) buffer.poll(); // bounded
        }
    }

    /** Drain buffer — dipanggil WebServer saat client pertama connect */
    public ConcurrentLinkedQueue<String> drainEventQueue() {
        ConcurrentLinkedQueue<String> snap = new ConcurrentLinkedQueue<>(buffer);
        buffer.clear();
        return snap;
    }
}
