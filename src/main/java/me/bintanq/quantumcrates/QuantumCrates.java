package me.bintanq.quantumcrates;

import me.bintanq.quantumcrates.command.QuantumCratesCommand;
import me.bintanq.quantumcrates.database.DatabaseManager;
import me.bintanq.quantumcrates.database.impl.MySQLDatabase;
import me.bintanq.quantumcrates.database.impl.SQLiteDatabase;
import me.bintanq.quantumcrates.hologram.HologramManager;
import me.bintanq.quantumcrates.hook.HookManager;
import me.bintanq.quantumcrates.listener.CrateListener;
import me.bintanq.quantumcrates.listener.GUIListener;
import me.bintanq.quantumcrates.listener.PlayerListener;
import me.bintanq.quantumcrates.log.LogManager;
import me.bintanq.quantumcrates.manager.CrateManager;
import me.bintanq.quantumcrates.manager.KeyManager;
import me.bintanq.quantumcrates.manager.PlayerDataManager;
import me.bintanq.quantumcrates.particle.ParticleManager;
import me.bintanq.quantumcrates.placeholder.QuantumPlaceholderExpansion;
import me.bintanq.quantumcrates.processor.RewardProcessor;
import me.bintanq.quantumcrates.serializer.GsonProvider;
import me.bintanq.quantumcrates.util.Logger;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * QuantumCrates — Main plugin entry point.
 * Phase 1: Core backend logic (Database, Crate, Reward, Log).
 *
 * @author bintanq
 * @version 1.0.0
 */
public final class QuantumCrates extends JavaPlugin {

    /* ──────────────────────────────────────────── */
    /*  Singleton                                   */
    /* ──────────────────────────────────────────── */
    private static QuantumCrates instance;

    public static QuantumCrates getInstance() {
        return instance;
    }

    /* ──────────────────────────────────────────── */
    /*  Core Services                               */
    /* ──────────────────────────────────────────── */
    private ExecutorService asyncExecutor;
    private DatabaseManager databaseManager;
    private PlayerDataManager playerDataManager;
    private CrateManager crateManager;
    private KeyManager keyManager;
    private RewardProcessor rewardProcessor;
    private LogManager logManager;
    private HookManager hookManager;
    private HologramManager hologramManager;
    private ParticleManager particleManager;
    private me.bintanq.quantumcrates.web.WebServer webServer;
    private me.bintanq.quantumcrates.web.StatsScheduler statsScheduler;

    /* ──────────────────────────────────────────── */
    /*  Lifecycle                                   */
    /* ──────────────────────────────────────────── */

    @Override
    public void onEnable() {
        instance = this;

        Logger.info("&b=============================");
        Logger.info("&b  QuantumCrates &fv" + getDescription().getVersion());
        Logger.info("&b  By bintanq");
        Logger.info("&b=============================");

        // 1. Config
        saveDefaultConfig();

        // 2. GSON + MessageManager
        GsonProvider.init();
        me.bintanq.quantumcrates.util.MessageManager.init(this);

        // 3. Async thread pool (dedicated for DB ops, NOT Bukkit scheduler)
        int poolSize = Math.max(2, Runtime.getRuntime().availableProcessors());
        asyncExecutor = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "QuantumCrates-Async-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        });
        Logger.info("Async executor started with &e" + poolSize + " &fthreads.");

        // 4. Database
        if (!initDatabase()) {
            Logger.severe("Database initialization failed! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 5. Managers (order matters)
        logManager       = new LogManager(databaseManager, asyncExecutor);
        playerDataManager = new PlayerDataManager(databaseManager, asyncExecutor);
        hookManager      = new HookManager(this);
        rewardProcessor  = new RewardProcessor(this, hookManager);
        keyManager       = new KeyManager(this, playerDataManager);
        crateManager     = new CrateManager(this, playerDataManager, rewardProcessor, logManager, keyManager);

        // 6. Load crate configs from /crates/*.json
        crateManager.loadAllCrates();

        // 7. Hook into soft-dependencies
        hookManager.registerAll();

        // 8. PlaceholderAPI
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new QuantumPlaceholderExpansion(this, playerDataManager, crateManager).register();
            Logger.info("PlaceholderAPI hook &aregistered.");
        }

        // 9. Register event listeners
        var pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerListener(this, playerDataManager), this);
        pm.registerEvents(new CrateListener(this, crateManager), this);
        pm.registerEvents(new GUIListener(this), this);
        Logger.info("Listeners &aregistered.");

        // 10. Register commands
        new QuantumCratesCommand(this);
        Logger.info("Commands &aregistered.");

        // 11. Holograms
        hologramManager = new HologramManager(this);
        hologramManager.spawnAll();

        // 12. Particles
        particleManager = new ParticleManager(this);
        particleManager.startAll();

        // 13. Web Server (Phase 2)
        if (getConfig().getBoolean("web.enabled", true)) {
            webServer = new me.bintanq.quantumcrates.web.WebServer(this);
            webServer.start();
            me.bintanq.quantumcrates.web.WebSocketBridge.getInstance().setWebServer(webServer);
            statsScheduler = new me.bintanq.quantumcrates.web.StatsScheduler(this);
            statsScheduler.start();
            Logger.info("Web Dashboard &aenabled &f— port &e" + getConfig().getInt("web.port", 7420));
        }

        Logger.info("&aQuantumCrates enabled successfully!");
    }

    @Override
    public void onDisable() {
        Logger.info("Shutting down QuantumCrates...");

        if (statsScheduler  != null) statsScheduler.stop();
        if (webServer       != null) webServer.stop();
        if (hologramManager != null) hologramManager.removeAll();
        if (particleManager  != null) particleManager.stopAll();
        if (crateManager     != null) crateManager.shutdown();
        if (playerDataManager != null) playerDataManager.flushAll();
        if (logManager != null) logManager.flushQueue();

        if (asyncExecutor != null) {
            asyncExecutor.shutdown();
        }

        if (databaseManager != null) {
            databaseManager.close();
        }

        Logger.info("&cQuantumCrates disabled.");
    }

    /* ──────────────────────────────────────────── */
    /*  Private Helpers                             */
    /* ──────────────────────────────────────────── */

    private boolean initDatabase() {
        String type = getConfig().getString("database.type", "sqlite");
        try {
            if ("mysql".equalsIgnoreCase(type)) {
                databaseManager = new MySQLDatabase(this);
                Logger.info("Using &bMySQL &fdatabase.");
            } else {
                databaseManager = new SQLiteDatabase(this);
                Logger.info("Using &bSQLite &fdatabase.");
            }
            databaseManager.init();
            return true;
        } catch (Exception e) {
            Logger.severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /* ──────────────────────────────────────────── */
    /*  Getters                                     */
    /* ──────────────────────────────────────────── */

    public ExecutorService getAsyncExecutor() { return asyncExecutor; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public CrateManager getCrateManager() { return crateManager; }
    public KeyManager getKeyManager() { return keyManager; }
    public RewardProcessor getRewardProcessor() { return rewardProcessor; }
    public LogManager getLogManager() { return logManager; }
    public HookManager getHookManager() { return hookManager; }
    public HologramManager getHologramManager() { return hologramManager; }
    public ParticleManager getParticleManager() { return particleManager; }
    public me.bintanq.quantumcrates.web.WebServer getWebServer() { return webServer; }
    public me.bintanq.quantumcrates.web.StatsScheduler getStatsScheduler() { return statsScheduler; }
}
