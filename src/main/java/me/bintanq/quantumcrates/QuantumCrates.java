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
import me.bintanq.quantumcrates.manager.RarityManager;
import me.bintanq.quantumcrates.particle.ParticleManager;
import me.bintanq.quantumcrates.placeholder.QuantumPlaceholderExpansion;
import me.bintanq.quantumcrates.processor.RewardProcessor;
import me.bintanq.quantumcrates.serializer.GsonProvider;
import me.bintanq.quantumcrates.util.Logger;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class QuantumCrates extends JavaPlugin {

    private static QuantumCrates instance;

    public static QuantumCrates getInstance() {
        return instance;
    }

    private ExecutorService asyncExecutor;
    private DatabaseManager databaseManager;
    private PlayerDataManager playerDataManager;
    private RarityManager rarityManager;
    private CrateManager crateManager;
    private KeyManager keyManager;
    private RewardProcessor rewardProcessor;
    private LogManager logManager;
    private HookManager hookManager;
    private HologramManager hologramManager;
    private ParticleManager particleManager;
    private me.bintanq.quantumcrates.web.WebServer webServer;
    private me.bintanq.quantumcrates.web.StatsScheduler statsScheduler;

    @Override
    public void onEnable() {
        instance = this;

        Logger.info("&b=============================");
        Logger.info("&b  QuantumCrates &fv" + getDescription().getVersion());
        Logger.info("&b  By bintanq");
        Logger.info("&b=============================");

        saveDefaultConfig();

        GsonProvider.init();
        me.bintanq.quantumcrates.util.MessageManager.init(this);

        int poolSize = Math.max(2, Runtime.getRuntime().availableProcessors());
        asyncExecutor = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "QuantumCrates-Async-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        });
        Logger.info("Async executor started with &e" + poolSize + " &fthreads.");

        if (!initDatabase()) {
            Logger.severe("Database initialization failed! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        rarityManager    = new RarityManager(this);

        logManager       = new LogManager(databaseManager, asyncExecutor);
        playerDataManager = new PlayerDataManager(databaseManager, asyncExecutor);
        hookManager      = new HookManager(this);
        rewardProcessor  = new RewardProcessor(this, hookManager);
        keyManager       = new KeyManager(this, playerDataManager);
        crateManager     = new CrateManager(this, playerDataManager, rewardProcessor, logManager, keyManager);

        crateManager.loadAllCrates();
        hookManager.registerAll();

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new QuantumPlaceholderExpansion(this, playerDataManager, crateManager).register();
            Logger.info("PlaceholderAPI hook &aregistered.");
        }

        var pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerListener(this, playerDataManager), this);
        pm.registerEvents(new CrateListener(this, crateManager), this);
        pm.registerEvents(new GUIListener(this), this);
        Logger.info("Listeners &aregistered.");

        new QuantumCratesCommand(this);
        Logger.info("Commands &aregistered.");

        hologramManager = new HologramManager(this);
        hologramManager.spawnAll();

        particleManager = new ParticleManager(this);
        particleManager.startAll();

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

        if (asyncExecutor != null) asyncExecutor.shutdown();
        if (databaseManager != null) databaseManager.close();

        Logger.info("&cQuantumCrates disabled.");
    }

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

    public ExecutorService getAsyncExecutor()          { return asyncExecutor; }
    public DatabaseManager getDatabaseManager()        { return databaseManager; }
    public PlayerDataManager getPlayerDataManager()    { return playerDataManager; }
    public RarityManager getRarityManager()            { return rarityManager; }
    public CrateManager getCrateManager()              { return crateManager; }
    public KeyManager getKeyManager()                  { return keyManager; }
    public RewardProcessor getRewardProcessor()        { return rewardProcessor; }
    public LogManager getLogManager()                  { return logManager; }
    public HookManager getHookManager()                { return hookManager; }
    public HologramManager getHologramManager()        { return hologramManager; }
    public ParticleManager getParticleManager()        { return particleManager; }
    public me.bintanq.quantumcrates.web.WebServer getWebServer()             { return webServer; }
    public me.bintanq.quantumcrates.web.StatsScheduler getStatsScheduler()  { return statsScheduler; }
}