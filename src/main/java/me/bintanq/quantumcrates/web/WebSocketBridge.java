package me.bintanq.quantumcrates.web;

import me.bintanq.quantumcrates.log.CrateLog;
import me.bintanq.quantumcrates.model.Crate;
import me.bintanq.quantumcrates.model.RarityDefinition;
import me.bintanq.quantumcrates.serializer.GsonProvider;
import me.bintanq.quantumcrates.util.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * WebSocketBridge — event bus between Minecraft plugin and WebServer.
 */
public class WebSocketBridge {

    private static WebSocketBridge instance;
    public static WebSocketBridge getInstance() {
        if (instance == null) instance = new WebSocketBridge();
        return instance;
    }

    public enum EventType {
        CRATE_OPEN, CRATE_UPDATE, CRATE_RELOAD,
        PLAYER_JOIN, PLAYER_QUIT,
        KEY_TRANSACTION, PITY_UPDATE,
        SERVER_STATS,
        RARITIES_UPDATE,
        PING, PONG
    }

    private WebServer webServer;
    private final ConcurrentLinkedQueue<String> buffer = new ConcurrentLinkedQueue<>();

    private WebSocketBridge() {}

    public void setWebServer(WebServer ws) { this.webServer = ws; }

    /* ─────────────────────── Broadcast Events ─────────────────────── */

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

    public void broadcastKeyTransaction(java.util.UUID uuid, String keyId, int delta, int balance) {
        dispatch(EventType.KEY_TRANSACTION, Map.of(
                "uuid",    uuid.toString(),
                "keyId",   keyId,
                "delta",   delta,
                "balance", balance
        ));
    }

    public void broadcastPityUpdate(java.util.UUID uuid, String crateId, int newPity, boolean wasReset) {
        dispatch(EventType.PITY_UPDATE, Map.of(
                "uuid",     uuid.toString(),
                "crateId",  crateId,
                "pity",     newPity,
                "wasReset", wasReset
        ));
    }

    public void broadcastServerStats(int online, double tps, long openingsToday) {
        dispatch(EventType.SERVER_STATS, Map.of(
                "onlinePlayers", online,
                "tps",           Math.round(tps * 100.0) / 100.0,
                "openingsToday", openingsToday
        ));
    }

    /** Broadcast updated rarity definitions to all web clients. */
    public void broadcastRaritiesUpdate(List<RarityDefinition> rarities) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("rarities", GsonProvider.getGson().toJsonTree(rarities));
        dispatch(EventType.RARITIES_UPDATE, p);
    }

    /* ─────────────────────── Internal Dispatch ─────────────────────── */

    private void dispatch(EventType type, Map<String, Object> payload) {
        if (webServer != null) {
            webServer.broadcast(type, payload);
        } else {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", type.name());
            event.put("timestamp", System.currentTimeMillis());
            event.putAll(payload);
            buffer.offer(GsonProvider.getCompact().toJson(event));
            while (buffer.size() > 500) buffer.poll();
        }
    }

    public ConcurrentLinkedQueue<String> drainEventQueue() {
        ConcurrentLinkedQueue<String> snap = new ConcurrentLinkedQueue<>(buffer);
        buffer.clear();
        return snap;
    }
}