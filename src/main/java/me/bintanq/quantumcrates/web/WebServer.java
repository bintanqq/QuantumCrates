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
import me.bintanq.quantumcrates.serializer.GsonProvider;
import me.bintanq.quantumcrates.util.Logger;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebServer — serves dashboard + REST API + WebSocket dari dalam JAR.
 *
 * Cara kerja (1 JAR, zero setup):
 *  1. Plugin start → WebServer start di background thread
 *  2. Admin ketik /qc web di game/console
 *  3. Plugin generate magic link: http://ip:port/?token=xxxxx
 *  4. Admin klik link → browser buka → Javalin validasi token → set JWT cookie → redirect ke /
 *  5. Dashboard (HTML/JS/CSS dari dalam JAR) terbuka, langsung terautentikasi
 *
 * Static files di-serve dari /resources/web/ dalam JAR classpath.
 */
public class WebServer {

    private final QuantumCrates    plugin;
    private final WebTokenManager  tokenManager;
    private       Javalin          app;

    /** Active WebSocket sessions */
    private final ConcurrentHashMap<String, io.javalin.websocket.WsContext> wsSessions
            = new ConcurrentHashMap<>();
    /** Rate limiter: IP → request timestamps */
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
                UUID.randomUUID().toString()); // fallback random jika belum diganti
        int    port      = plugin.getConfig().getInt("web.port", 7420);
        String cors      = plugin.getConfig().getString("web.cors-origins", "*");
        int    rateLimit = plugin.getConfig().getInt("web.rate-limit", 120);
        boolean logReq   = plugin.getConfig().getBoolean("web.log-requests", false);

        jwtAlgorithm = Algorithm.HMAC256(secret);
        jwtVerifier  = JWT.require(jwtAlgorithm).withIssuer("quantumcrates").build();

        app = Javalin.create(config -> {

            // ── 1. Static files dari dalam JAR ──
            // File di /resources/web/ langsung di-serve.
            // GET / → index.html otomatis
            config.staticFiles.add(sf -> {
                sf.hostedPath = "/";
                sf.directory  = "/web";
                sf.location   = Location.CLASSPATH;
            });

            // ── 2. CORS ──
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

        // ── Before: rate limit + JWT auth (kecuali auth endpoints) ──
        app.before("/api/*", ctx -> {
            if (logReq) Logger.debug("[Web] " + ctx.method() + " " + ctx.path());

            String path = ctx.path();
            boolean isPublic = path.equals("/api/auth/login") || path.equals("/api/auth/magic");
            if (isPublic) return;

            // Rate limit
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

            // JWT dari header atau cookie
            String jwt = null;
            String auth = ctx.header("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                jwt = auth.substring(7);
            } else {
                jwt = ctx.cookie("qc_jwt");
            }

            if (jwt == null) { ctx.status(401).json(err("Not authenticated.")); return; }
            try {
                jwtVerifier.verify(jwt);
            } catch (JWTVerificationException e) {
                ctx.status(401).json(err("Invalid or expired session. Please re-open the link."));
            }
        });

        // ── Routes ──
        registerMagicLinkRoute();
        registerAuthRoutes();
        registerCrateRoutes();
        registerKeyRoutes();
        registerLogRoutes();
        registerPlayerRoutes();
        registerServerRoutes();
        registerMessagesRoutes();
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

    /**
     * GET /api/auth/magic?token=xxxx
     *
     * Dipanggil browser saat admin klik link dari chat.
     * Validasi token → issue JWT cookie → redirect ke dashboard.
     */
    private void registerMagicLinkRoute() {
        app.get("/api/auth/magic", ctx -> {
            String token = ctx.queryParam("token");
            if (token == null || !tokenManager.consumeToken(token)) {
                // Token invalid/expired → tampilkan error page
                ctx.status(401).html(buildErrorPage(
                    "Link Invalid or Expired",
                    "This dashboard link has expired or already been used.",
                    "Use <b>/qc web</b> in-game to generate a new link."
                ));
                return;
            }

            // Token valid → issue JWT (24 jam)
            String jwt = JWT.create()
                    .withIssuer("quantumcrates")
                    .withIssuedAt(new Date())
                    .withExpiresAt(new Date(System.currentTimeMillis() + 86_400_000L))
                    .sign(jwtAlgorithm);

            // Set JWT sebagai HttpOnly cookie + redirect ke dashboard
            ctx.cookie("qc_jwt", jwt, 86400); // expires in 24h
            ctx.header("Set-Cookie", "qc_jwt=" + jwt +
                    "; Path=/; Max-Age=86400; HttpOnly; SameSite=Lax");
            ctx.redirect("/"); // redirect ke index.html
        });
    }

    /* ─────────────────────── Auth ─────────────────────── */

    private void registerAuthRoutes() {
        // POST /api/auth/login — fallback login manual (opsional)
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

        // GET /api/auth/check — cek apakah session masih valid
        app.get("/api/auth/check", ctx -> {
            ctx.json(ok("Session valid"));
        });

        // POST /api/auth/logout
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
                plugin.getCrateManager().registerCrate(crate);
                broadcast(WebSocketBridge.EventType.CRATE_UPDATE,
                        Map.of("crateId", crate.getId()));
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
            plugin.getCrateManager().removeCrate(id);
            ctx.json(ok("Crate deleted: " + id));
        });

        app.post("/api/crates/reload", ctx -> {
            Bukkit.getScheduler().runTask(plugin, () ->
                    me.bintanq.quantumcrates.util.ReloadUtil.reloadAll(plugin));
            ctx.json(ok("Reload triggered."));
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
                "timestamp",     System.currentTimeMillis()
            )))
        );
    }

    /* ─────────────────────── Messages Config ─────────────────────── */

    private void registerMessagesRoutes() {
        // GET /api/config/messages — baca semua messages dari config
        app.get("/api/config/messages", ctx -> {
            var section = plugin.getConfig().getConfigurationSection("messages");
            Map<String, String> msgs = new LinkedHashMap<>();
            if (section != null) {
                section.getKeys(false).forEach(k -> msgs.put(k, section.getString(k, "")));
            }
            ctx.json(msgs);
        });

        // POST /api/config/messages — update messages di config + save
        app.post("/api/config/messages", ctx -> {
            try {
                Map<?,?> body = ctx.bodyAsClass(Map.class);
                body.forEach((k, v) -> plugin.getConfig().set("messages." + k, v.toString()));
                plugin.saveConfig();
                me.bintanq.quantumcrates.util.MessageManager.init(plugin);
                ctx.json(ok("Messages saved and applied."));
            } catch (Exception e) { ctx.status(400).json(err(e.getMessage())); }
        });
    }

    /* ─────────────────────── WebSocket ─────────────────────── */

    private void registerWebSocket() {
        app.ws("/ws", ws -> {
            ws.onConnect(ctx -> {
                // Auth via cookie (set saat magic link) atau query param
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

                // Welcome + drain buffered events
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

    /* ─────────────────────── Error Page HTML ─────────────────────── */

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
