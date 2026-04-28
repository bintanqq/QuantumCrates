package me.bintanq.quantumcrates.web;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;
import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.log.CrateLog;
import me.bintanq.quantumcrates.model.Crate;
import me.bintanq.quantumcrates.model.RarityDefinition;
import me.bintanq.quantumcrates.serializer.GsonProvider;
import me.bintanq.quantumcrates.util.Logger;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebServer — serves dashboard + REST API + WebSocket.
 *
 * New endpoints added for rarity management:
 *   GET  /api/rarities        → list all rarity definitions
 *   POST /api/rarities        → save updated rarity list (triggers reload)
 *   POST /api/rarities/reload → reload rarities.yml from disk
 */
public class WebServer {

    private final QuantumCrates    plugin;
    private final WebTokenManager  tokenManager;
    private       Javalin          app;

    private final ConcurrentHashMap<String, io.javalin.websocket.WsContext> wsSessions
            = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LinkedList<Long>> rateLimiter
            = new ConcurrentHashMap<>();

    private Algorithm   jwtAlgorithm;
    private JWTVerifier jwtVerifier;

    public WebServer(QuantumCrates plugin) {
        this.plugin       = plugin;
        this.tokenManager = new WebTokenManager();
    }

    /* ─────────────────────── Lifecycle ─────────────────────── */

    public void start() {
        String secret    = plugin.getConfig().getString("web.secret-token",
                UUID.randomUUID().toString());
        int    port      = plugin.getConfig().getInt("web.port", 7420);
        String cors      = plugin.getConfig().getString("web.cors-origins", "*");
        int    rateLimit = plugin.getConfig().getInt("web.rate-limit", 120);
        boolean logReq   = plugin.getConfig().getBoolean("web.log-requests", false);

        jwtAlgorithm = Algorithm.HMAC256(secret);
        jwtVerifier  = JWT.require(jwtAlgorithm).withIssuer("quantumcrates").build();

        app = Javalin.create(config -> {
            config.staticFiles.add(sf -> {
                sf.hostedPath = "/";
                sf.directory  = "/web";
                sf.location   = Location.CLASSPATH;
            });

            config.bundledPlugins.enableCors(c -> {
                if ("*".equals(cors)) {
                    c.addRule(it -> it.anyHost());
                } else {
                    for (String origin : cors.split(",")) {
                        String o = origin.trim();
                        c.addRule(it -> it.allowHost(o));
                    }
                }
            });

            config.showJavalinBanner = false;
        });

        app.before("/api/*", ctx -> {
            if (logReq) Logger.debug("[Web] " + ctx.method() + " " + ctx.path());

            String path = ctx.path();
            boolean isPublic = path.equals("/api/auth/login") || path.equals("/api/auth/magic");
            if (isPublic) return;

            String ip = ctx.ip();
            LinkedList<Long> ts = rateLimiter.computeIfAbsent(ip, k -> new LinkedList<>());
            long now = System.currentTimeMillis();
            synchronized (ts) {
                ts.removeIf(t -> now - t > 60_000);
                if (ts.size() >= rateLimit) {
                    ctx.status(429).json(err("Rate limit exceeded. Slow down."));
                    return;
                }
                ts.add(now);
            }

            String jwt = ctx.cookie("qc_jwt");
            if (jwt == null) {
                String auth = ctx.header("Authorization");
                if (auth != null && auth.startsWith("Bearer ")) jwt = auth.substring(7);
            }

            if (jwt == null) { ctx.status(401).json(err("Not authenticated.")); return; }
            try {
                jwtVerifier.verify(jwt);
            } catch (JWTVerificationException e) {
                ctx.status(401).json(err("Invalid or expired session."));
            }
        });

        registerMagicLinkRoute();
        registerAuthRoutes();
        registerCrateRoutes();
        registerKeyRoutes();
        registerKeyConfigRoute();
        registerLogRoutes();
        registerPlayerRoutes();
        registerServerRoutes();
        registerMessagesRoutes();
        registerRarityRoutes();
        registerWebSocket();

        app.exception(Exception.class, (e, ctx) -> {
            Logger.warn("[Web] Error on " + ctx.path() + ": " + e.getMessage());
            ctx.status(500).json(err("Internal error: " + e.getMessage()));
        });

        Thread t = new Thread(() -> {
            try {
                app.start(port);
                Logger.info("&aWeb Dashboard running on port &e" + port);
                Logger.info("&7Use &b/qc web &7in-game to get your access link.");
            } catch (Exception e) {
                Logger.severe("Web Server failed to start: " + e.getMessage());
            }
        }, "QuantumCrates-WebServer");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        if (app != null) { app.stop(); Logger.info("Web Server stopped."); }
    }

    /* ─────────────────────── Magic Link ─────────────────────── */

    private void registerMagicLinkRoute() {
        app.get("/api/auth/magic", ctx -> {
            String token = ctx.queryParam("token");
            if (token == null || !tokenManager.consumeToken(token)) {
                ctx.status(401).html(buildErrorPage(
                        "Link Invalid or Expired",
                        "This dashboard link has expired or already been used.",
                        "Use <b>/qc web</b> in-game to generate a new link."
                ));
                return;
            }

            String jwt = JWT.create()
                    .withIssuer("quantumcrates")
                    .withIssuedAt(new Date())
                    .withExpiresAt(new Date(System.currentTimeMillis() + 86_400_000L))
                    .sign(jwtAlgorithm);

            ctx.header("Set-Cookie", "qc_jwt=" + jwt +
                    "; Path=/; Max-Age=86400; HttpOnly; SameSite=Lax");
            ctx.status(302);
            ctx.header("Location", "/");
        });
    }

    /* ─────────────────────── Auth ─────────────────────── */

    private void registerAuthRoutes() {
        app.post("/api/auth/login", ctx -> {
            Map<?,?> body = ctx.bodyAsClass(Map.class);
            String inputToken = (String) body.get("token");
            String secret     = plugin.getConfig().getString("web.secret-token", "");

            if (!secret.equals(inputToken)) {
                ctx.status(401).json(err("Invalid token."));
                return;
            }

            String jwt = JWT.create()
                    .withIssuer("quantumcrates")
                    .withIssuedAt(new Date())
                    .withExpiresAt(new Date(System.currentTimeMillis() + 86_400_000L))
                    .sign(jwtAlgorithm);

            ctx.cookie("qc_jwt", jwt, 86400);
            ctx.json(ok("Login successful", Map.of("jwt", jwt)));
        });

        app.get("/api/auth/check", ctx -> ctx.json(ok("Session valid")));

        app.post("/api/auth/logout", ctx -> {
            ctx.removeCookie("qc_jwt");
            ctx.json(ok("Logged out"));
        });
    }

    /* ─────────────────────── Crates ─────────────────────── */

    private void registerCrateRoutes() {
        app.get("/api/crates", ctx ->
                ctx.result(GsonProvider.getGson().toJson(
                        Map.of("data", plugin.getCrateManager().getAllCrates())
                ))
        );

        app.get("/api/crates/{id}", ctx -> {
            String id = ctx.pathParam("id");
            Crate c = plugin.getCrateManager().getCrate(id);
            if (c == null) { ctx.status(404).json(err("Crate not found: " + id)); return; }
            ctx.result(GsonProvider.getGson().toJson(c));
        });

        app.post("/api/crates/{id}", ctx -> {
            try {
                Crate crate = GsonProvider.getGson().fromJson(ctx.body(), Crate.class);
                if (crate.getId() == null) crate.setId(ctx.pathParam("id"));

                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getCrateManager().registerCrate(crate);
                    broadcast(WebSocketBridge.EventType.CRATE_UPDATE,
                            Map.of("crateId", crate.getId()));
                });

                ctx.json(ok("Crate saved: " + crate.getId()));
            } catch (Exception e) {
                ctx.status(400).json(err("Invalid crate JSON: " + e.getMessage()));
            }
        });

        app.delete("/api/crates/{id}", ctx -> {
            String id = ctx.pathParam("id");
            if (plugin.getCrateManager().getCrate(id) == null) {
                ctx.status(404).json(err("Crate not found: " + id)); return;
            }
            Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.getCrateManager().removeCrate(id));
            ctx.json(ok("Crate deleted: " + id));
        });

        app.post("/api/crates/reload", ctx -> {
            Bukkit.getScheduler().runTask(plugin, () ->
                    me.bintanq.quantumcrates.util.ReloadUtil.reloadAll(plugin));
            ctx.json(ok("Reload triggered."));
        });
    }

    /* ─────────────────────── Rarities (NEW) ─────────────────────── */

    private void registerRarityRoutes() {
        // GET /api/rarities — returns all rarity definitions ordered by tier
        app.get("/api/rarities", ctx ->
                ctx.result(GsonProvider.getGson().toJson(
                        Map.of("data", plugin.getRarityManager().getAll())
                ))
        );

        // POST /api/rarities — save a full updated list of rarities
        app.post("/api/rarities", ctx -> {
            try {
                com.google.gson.JsonObject root = GsonProvider.getGson()
                        .fromJson(ctx.body(), com.google.gson.JsonObject.class);

                if (!root.has("rarities") || !root.get("rarities").isJsonArray()) {
                    ctx.status(400).json(err("Body must be { rarities: [...] }"));
                    return;
                }

                java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<
                        List<RarityDefinition>>(){}.getType();
                List<RarityDefinition> updated = GsonProvider.getGson()
                        .fromJson(root.get("rarities"), listType);

                if (updated.isEmpty()) {
                    ctx.status(400).json(err("Cannot save empty rarity list."));
                    return;
                }

                // Validate: each rarity must have an id
                for (RarityDefinition def : updated) {
                    if (def.getId() == null || def.getId().isBlank()) {
                        ctx.status(400).json(err("Each rarity must have an 'id' field."));
                        return;
                    }
                }

                // Uppercase all IDs for consistency
                updated.forEach(def -> def.setId(def.getId().toUpperCase()));

                plugin.getRarityManager().saveRarities(updated);

                // Broadcast to all WS clients so dashboard updates live
                WebSocketBridge.getInstance().broadcastRaritiesUpdate(plugin.getRarityManager().getAll());

                ctx.json(ok("Rarities saved. " + updated.size() + " tiers active."));
            } catch (Exception e) {
                ctx.status(400).json(err("Invalid rarities JSON: " + e.getMessage()));
            }
        });

        // POST /api/rarities/reload — reload from disk without changing content
        app.post("/api/rarities/reload", ctx -> {
            plugin.getRarityManager().reload();
            WebSocketBridge.getInstance().broadcastRaritiesUpdate(plugin.getRarityManager().getAll());
            ctx.json(ok("Rarities reloaded from rarities.yml."));
        });
    }

    /* ─────────────────────── Keys ─────────────────────── */

    private void registerKeyRoutes() {
        app.get("/api/keys", ctx -> {
            ctx.json(Map.of(
                    "mode",    plugin.getKeyManager().getGlobalMode().name(),
                    "knownIds", plugin.getKeyManager().getKnownKeyIds()
            ));
        });

        app.get("/api/keys/{keyId}/balance/{uuid}", ctx -> {
            try {
                java.util.UUID uuid = java.util.UUID.fromString(ctx.pathParam("uuid"));
                int balance = plugin.getDatabaseManager()
                        .getVirtualKeys(uuid, ctx.pathParam("keyId")).get();
                ctx.json(Map.of("balance", balance, "keyId", ctx.pathParam("keyId")));
            } catch (Exception e) { ctx.status(400).json(err(e.getMessage())); }
        });

        app.post("/api/keys/give", ctx -> {
            try {
                Map<?,?> body  = ctx.bodyAsClass(Map.class);
                String keyId   = (String) body.get("keyId");
                int    amount  = ((Number) body.get("amount")).intValue();
                String uuidStr = (String) body.get("uuid");
                java.util.UUID uuid = java.util.UUID.fromString(uuidStr);

                org.bukkit.entity.Player online = Bukkit.getPlayer(uuid);
                if (online != null) {
                    plugin.getKeyManager().giveKey(online, keyId, amount);
                } else {
                    plugin.getDatabaseManager().addVirtualKeys(uuid, keyId, amount).get();
                }
                ctx.json(ok("Given " + amount + "x " + keyId));
            } catch (Exception e) { ctx.status(400).json(err(e.getMessage())); }
        });

        app.post("/api/keys/config/mode", ctx -> {
            try {
                com.google.gson.JsonObject body = me.bintanq.quantumcrates.serializer.GsonProvider
                        .getGson().fromJson(ctx.body(), com.google.gson.JsonObject.class);

                if (!body.has("mode")) {
                    ctx.status(400).json(err("Body must include 'mode' field (virtual or physical)"));
                    return;
                }

                String mode = body.get("mode").getAsString().toLowerCase();
                if (!mode.equals("virtual") && !mode.equals("physical")) {
                    ctx.status(400).json(err("Mode must be 'virtual' or 'physical'"));
                    return;
                }

                plugin.getConfig().set("keys.mode", mode);
                plugin.saveConfig();

                org.bukkit.Bukkit.getScheduler().runTask(plugin,
                        () -> plugin.getKeyManager().reload());

                me.bintanq.quantumcrates.util.Logger.info(
                        "[Web] Key mode changed to: &b" + mode.toUpperCase());

                ctx.json(ok("Key mode saved: " + mode.toUpperCase() +
                        ". Run /qc reload in-game if needed."));

            } catch (Exception e) {
                ctx.status(400).json(err("Invalid request: " + e.getMessage()));
            }
        });

        app.get("/api/keys/config/mode", ctx -> {
            String mode = plugin.getConfig().getString("keys.mode", "virtual");
            ctx.json(java.util.Map.of(
                    "mode", mode,
                    "knownIds", plugin.getKeyManager().getKnownKeyIds()
            ));
        });

    }

    private void registerKeyConfigRoute() {
        app.post("/api/keys/config/physical", ctx -> {
            try {
                com.google.gson.JsonObject body = me.bintanq.quantumcrates.serializer.GsonProvider
                        .getGson().fromJson(ctx.body(), com.google.gson.JsonObject.class);

                // Validate material
                String material = body.has("material") ? body.get("material").getAsString().toUpperCase() : null;
                if (material != null) {
                    org.bukkit.Material mat = org.bukkit.Material.matchMaterial(material);
                    if (mat == null) {
                        ctx.status(400).json(err("Invalid material: " + material));
                        return;
                    }
                    plugin.getConfig().set("keys.physical.material", material);
                }

                if (body.has("customModelData")) {
                    plugin.getConfig().set("keys.physical.custom-model-data",
                            body.get("customModelData").getAsInt());
                }

                if (body.has("extraLore") && body.get("extraLore").isJsonArray()) {
                    java.util.List<String> lore = new java.util.ArrayList<>();
                    body.get("extraLore").getAsJsonArray()
                            .forEach(el -> lore.add(el.getAsString()));
                    plugin.getConfig().set("keys.physical.extra-lore", lore);
                }

                plugin.saveConfig();

                // Reload KeyManager on main thread so material reference updates safely
                org.bukkit.Bukkit.getScheduler().runTask(plugin,
                        () -> plugin.getKeyManager().reload());

                ctx.json(ok("Physical key config saved and reloaded."));

            } catch (Exception e) {
                ctx.status(400).json(err("Invalid request: " + e.getMessage()));
            }
        });

        // GET /api/keys/config/physical — return current physical key settings
        app.get("/api/keys/config/physical", ctx -> {
            java.util.Map<String, Object> cfg = new java.util.LinkedHashMap<>();
            cfg.put("material",       plugin.getConfig().getString("keys.physical.material", "TRIPWIRE_HOOK"));
            cfg.put("customModelData",plugin.getConfig().getInt("keys.physical.custom-model-data", -1));
            cfg.put("extraLore",      plugin.getConfig().getStringList("keys.physical.extra-lore"));
            ctx.result(me.bintanq.quantumcrates.serializer.GsonProvider.getGson().toJson(cfg));
        });
    }


    /* ─────────────────────── Logs ─────────────────────── */

    private void registerLogRoutes() {
        app.get("/api/logs", ctx -> {
            try {
                int page  = Integer.parseInt(ctx.queryParamAsClass("page",  String.class).getOrDefault("0"));
                int limit = Math.min(Integer.parseInt(ctx.queryParamAsClass("limit", String.class).getOrDefault("50")), 200);
                String crateId  = ctx.queryParam("crate");
                String uuidStr  = ctx.queryParam("uuid");

                List<CrateLog> logs;
                if (uuidStr != null) {
                    logs = plugin.getDatabaseManager()
                            .getPlayerLogs(java.util.UUID.fromString(uuidStr), limit, page * limit).get();
                } else if (crateId != null) {
                    logs = plugin.getDatabaseManager().getCrateLogs(crateId, limit, page * limit).get();
                } else {
                    logs = new ArrayList<>();
                    for (Crate c : plugin.getCrateManager().getAllCrates()) {
                        logs.addAll(plugin.getDatabaseManager().getCrateLogs(c.getId(), limit, 0).get());
                    }
                    logs.sort(Comparator.comparingLong(CrateLog::getTimestamp).reversed());
                    if (logs.size() > limit) logs = logs.subList(0, limit);
                }
                ctx.result(GsonProvider.getGson().toJson(Map.of("data", logs, "count", logs.size())));
            } catch (Exception e) { ctx.status(500).json(err(e.getMessage())); }
        });

        app.get("/api/logs/stats", ctx -> {
            try {
                String crateId = ctx.queryParam("crate");
                Map<String, Object> stats = new LinkedHashMap<>();
                if (crateId != null) {
                    stats.put("crateId",       crateId);
                    stats.put("totalOpenings", plugin.getDatabaseManager().getCrateOpeningCount(crateId).get());
                } else {
                    long total = 0;
                    Map<String, Long> perCrate = new LinkedHashMap<>();
                    for (Crate c : plugin.getCrateManager().getAllCrates()) {
                        long count = plugin.getDatabaseManager().getCrateOpeningCount(c.getId()).get();
                        perCrate.put(c.getId(), count);
                        total += count;
                    }
                    stats.put("totalOpenings", total);
                    stats.put("perCrate",      perCrate);
                }
                ctx.result(GsonProvider.getGson().toJson(stats));
            } catch (Exception e) { ctx.status(500).json(err(e.getMessage())); }
        });
    }

    /* ─────────────────────── Players ─────────────────────── */

    private void registerPlayerRoutes() {
        app.get("/api/players/{uuid}/pity", ctx -> {
            try {
                java.util.UUID uuid = java.util.UUID.fromString(ctx.pathParam("uuid"));
                plugin.getPlayerDataManager().loadPlayer(uuid).thenAccept(data ->
                        ctx.result(GsonProvider.getGson().toJson(Map.of(
                                "uuid", uuid.toString(), "pityData", data.getPityData())))
                ).get();
            } catch (Exception e) { ctx.status(400).json(err(e.getMessage())); }
        });

        app.post("/api/players/{uuid}/pity/reset", ctx -> {
            try {
                java.util.UUID uuid = java.util.UUID.fromString(ctx.pathParam("uuid"));
                String crateId = ctx.queryParam("crate");
                if (crateId != null) {
                    plugin.getPlayerDataManager().resetPity(uuid, crateId);
                } else {
                    for (Crate c : plugin.getCrateManager().getAllCrates())
                        plugin.getPlayerDataManager().resetPity(uuid, c.getId());
                }
                ctx.json(ok("Pity reset for " + uuid));
            } catch (Exception e) { ctx.status(400).json(err(e.getMessage())); }
        });

        app.get("/api/players/lookup", ctx -> {
            String name = ctx.queryParam("name");
            if (name == null || name.isEmpty()) {
                ctx.status(400).json(err("Name is required")); return;
            }
            org.bukkit.entity.Player online = Bukkit.getPlayerExact(name);
            if (online != null) {
                ctx.json(Map.of("uuid", online.getUniqueId().toString(), "name", online.getName()));
                return;
            }
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
            if (offline.hasPlayedBefore()) {
                ctx.json(Map.of(
                        "uuid", offline.getUniqueId().toString(),
                        "name", offline.getName() != null ? offline.getName() : name
                ));
                return;
            }
            ctx.status(404).json(err("Player not found: " + name));
        });
    }

    /* ─────────────────────── Server Status ─────────────────────── */

    private void registerServerRoutes() {
        app.get("/api/server/status", ctx ->
                ctx.result(GsonProvider.getGson().toJson(Map.of(
                        "online",        true,
                        "onlinePlayers", Bukkit.getOnlinePlayers().size(),
                        "maxPlayers",    Bukkit.getMaxPlayers(),
                        "tps",           Math.round(Bukkit.getTPS()[0] * 100.0) / 100.0,
                        "version",       Bukkit.getVersion(),
                        "crateCount",    plugin.getCrateManager().getAllCrates().size(),
                        "rarityCount",   plugin.getRarityManager().getAll().size(),
                        "timestamp",     System.currentTimeMillis()
                )))
        );
    }

    /* ─────────────────────── Messages Config ─────────────────────── */

    private void registerMessagesRoutes() {
        app.get("/api/config/messages", ctx -> {
            var chatSection = plugin.getConfig().getConfigurationSection("messages");
            var guiSection  = plugin.getConfig().getConfigurationSection("gui-messages");

            java.util.Map<String, String> chatMsgs = new java.util.LinkedHashMap<>();
            java.util.Map<String, String> guiMsgs  = new java.util.LinkedHashMap<>();

            if (chatSection != null) chatSection.getKeys(false).forEach(k ->
                    chatMsgs.put(k, chatSection.getString(k, "")));
            if (guiSection != null) guiSection.getKeys(false).forEach(k ->
                    guiMsgs.put(k, guiSection.getString(k, "")));

            ctx.result(GsonProvider.getGson().toJson(java.util.Map.of("chat", chatMsgs, "gui", guiMsgs)));
        });

        app.post("/api/config/messages", ctx -> {
            try {
                com.google.gson.JsonObject root = GsonProvider.getGson()
                        .fromJson(ctx.body(), com.google.gson.JsonObject.class);

                if (root.has("chat") || root.has("gui")) {
                    if (root.has("chat") && root.get("chat").isJsonObject())
                        root.get("chat").getAsJsonObject().entrySet().forEach(e ->
                                plugin.getConfig().set("messages." + e.getKey(), e.getValue().getAsString()));
                    if (root.has("gui") && root.get("gui").isJsonObject())
                        root.get("gui").getAsJsonObject().entrySet().forEach(e ->
                                plugin.getConfig().set("gui-messages." + e.getKey(), e.getValue().getAsString()));
                } else {
                    root.entrySet().forEach(e ->
                            plugin.getConfig().set("messages." + e.getKey(), e.getValue().getAsString()));
                }

                plugin.saveConfig();
                me.bintanq.quantumcrates.util.MessageManager.init(plugin);
                ctx.json(ok("Messages saved and applied."));
            } catch (Exception e) {
                ctx.status(400).json(err(e.getMessage()));
            }
        });
    }

    /* ─────────────────────── WebSocket ─────────────────────── */

    private void registerWebSocket() {
        app.ws("/ws", ws -> {
            ws.onConnect(ctx -> {
                String jwt = ctx.cookie("qc_jwt");
                if (jwt == null) jwt = ctx.queryParam("token");

                if (jwt == null) { ctx.closeSession(4001, "Missing auth"); return; }
                try {
                    jwtVerifier.verify(jwt);
                } catch (JWTVerificationException e) {
                    ctx.closeSession(4001, "Invalid session");
                    return;
                }

                wsSessions.put(ctx.sessionId(), ctx);
                Logger.debug("[WS] Client connected: " + ctx.sessionId());

                sendToSession(ctx, Map.of(
                        "type", "CONNECTED",
                        "message", "Connected to QuantumCrates",
                        "timestamp", System.currentTimeMillis()
                ));

                WebSocketBridge.getInstance().drainEventQueue().forEach(json -> {
                    try { ctx.send(json); } catch (Exception ignored) {}
                });
            });

            ws.onClose(ctx -> {
                wsSessions.remove(ctx.sessionId());
                Logger.debug("[WS] Client disconnected: " + ctx.sessionId());
            });

            ws.onMessage(ctx -> {
                try {
                    Map<?,?> data = GsonProvider.getGson().fromJson(ctx.message(), Map.class);
                    if ("PING".equals(data.get("type"))) {
                        ctx.send("{\"type\":\"PONG\",\"ts\":" + System.currentTimeMillis() + "}");
                    }
                } catch (Exception ignored) {}
            });

            ws.onError(ctx -> wsSessions.remove(ctx.sessionId()));
        });
    }

    /* ─────────────────────── Broadcast API ─────────────────────── */

    public void broadcast(WebSocketBridge.EventType type, Map<String, Object> payload) {
        if (wsSessions.isEmpty()) return;
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type.name());
        event.put("timestamp", System.currentTimeMillis());
        event.putAll(payload);
        String json = GsonProvider.getCompact().toJson(event);
        wsSessions.forEach((id, ctx) -> {
            try { ctx.send(json); }
            catch (Exception e) { wsSessions.remove(id); }
        });
    }

    private void sendToSession(io.javalin.websocket.WsContext ctx, Map<String, Object> data) {
        try { ctx.send(GsonProvider.getCompact().toJson(data)); } catch (Exception ignored) {}
    }

    public int getConnectedClients() { return wsSessions.size(); }
    public WebTokenManager getTokenManager() { return tokenManager; }

    /* ─────────────────────── Error Page ─────────────────────── */

    private String buildErrorPage(String title, String msg, String hint) {
        return """
            <!DOCTYPE html><html><head>
            <meta charset="UTF-8"/><title>QuantumCrates — %s</title>
            <link href="https://fonts.googleapis.com/css2?family=Poppins:wght@400;600;700&display=swap" rel="stylesheet"/>
            <style>
              body{font-family:Poppins,sans-serif;background:#070c12;color:#e4eff9;
                   display:flex;align-items:center;justify-content:center;height:100vh;margin:0}
              .box{text-align:center;max-width:400px;padding:40px;background:#0b1520;
                   border:1px solid #1b2f45;border-radius:16px}
              h2{color:#ff4d6d;margin-bottom:8px;font-size:20px}
              p{color:#829db8;font-size:13px;line-height:1.6;margin-bottom:6px}
              .hint{color:#4a6a85;font-size:12px;margin-top:16px;
                    background:#0f1e2e;padding:10px 14px;border-radius:8px}
            </style></head><body>
            <div class="box">
              <div style="font-size:48px;margin-bottom:16px">⚠️</div>
              <h2>%s</h2>
              <p>%s</p>
              <div class="hint">%s</div>
            </div></body></html>
            """.formatted(title, title, msg, hint);
    }

    /* ─────────────────────── Helpers ─────────────────────── */

    private Map<String, Object> ok(String msg) {
        return Map.of("status", "ok", "message", msg);
    }
    private Map<String, Object> ok(String msg, Map<String, Object> extra) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "ok"); m.put("message", msg); m.putAll(extra);
        return m;
    }
    private Map<String, String> err(String msg) {
        return Map.of("status", "error", "message", msg);
    }
}