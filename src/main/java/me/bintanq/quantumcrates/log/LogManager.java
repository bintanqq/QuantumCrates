package me.bintanq.quantumcrates.log;

import me.bintanq.quantumcrates.database.DatabaseManager;
import me.bintanq.quantumcrates.util.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class LogManager {

    private static final int  BATCH_SIZE     = 100;
    private static final long FLUSH_INTERVAL = 5L;

    private final DatabaseManager db;
    private final Executor asyncExecutor;
    private final ConcurrentLinkedQueue<CrateLog> logQueue = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "QuantumCrates-LogFlusher");
                t.setDaemon(true);
                return t;
            });
    private final Object flushLock = new Object();

    public LogManager(DatabaseManager db, Executor asyncExecutor) {
        this.db = db;
        this.asyncExecutor = asyncExecutor;
        scheduler.scheduleAtFixedRate(this::flushQueue, FLUSH_INTERVAL, FLUSH_INTERVAL, TimeUnit.SECONDS);
    }

    public void log(CrateLog entry) {
        logQueue.add(entry);
        if (logQueue.size() >= BATCH_SIZE * 5)
            CompletableFuture.runAsync(this::flushQueue, asyncExecutor);
    }

    public void flushQueue() {
        if (logQueue.isEmpty()) return;
        synchronized (flushLock) {
            if (logQueue.isEmpty()) return;
            List<CrateLog> batch = new ArrayList<>();
            CrateLog entry;
            while ((entry = logQueue.poll()) != null) {
                batch.add(entry);
                if (batch.size() >= BATCH_SIZE) {
                    db.insertLogBatch(new ArrayList<>(batch));
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) db.insertLogBatch(new ArrayList<>(batch));
        }
        Logger.debug("Log flush: wrote entries to database.");
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        flushQueue();
    }
}