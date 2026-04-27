package me.bintanq.quantumcrates.log;

import me.bintanq.quantumcrates.database.DatabaseManager;
import me.bintanq.quantumcrates.util.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * LogManager — high-throughput, buffered crate opening log writer.
 *
 * Instead of writing every log entry immediately to the DB (expensive on
 * mass-opens), we buffer entries in a thread-safe queue and flush them
 * in batches on a configurable schedule, and also on plugin disable.
 *
 * This pattern keeps TPS stable during burst events (e.g. 100 mass-opens
 * happening simultaneously).
 */
public class LogManager {

    private static final int  BATCH_SIZE     = 100;
    private static final long FLUSH_INTERVAL = 5L;   // seconds

    private final DatabaseManager db;
    private final Executor         asyncExecutor;

    /** Thread-safe log queue. */
    private final ConcurrentLinkedQueue<CrateLog> logQueue = new ConcurrentLinkedQueue<>();

    /** Scheduled executor dedicated to periodic flushing. */
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "QuantumCrates-LogFlusher");
                t.setDaemon(true);
                return t;
            });

    public LogManager(DatabaseManager db, Executor asyncExecutor) {
        this.db            = db;
        this.asyncExecutor = asyncExecutor;

        // Schedule periodic batch flush
        scheduler.scheduleAtFixedRate(
                this::flushQueue,
                FLUSH_INTERVAL,
                FLUSH_INTERVAL,
                TimeUnit.SECONDS
        );
    }

    /* ─────────────────────── Public API ─────────────────────── */

    /**
     * Enqueues a log entry. Returns immediately — never blocks.
     * Thread-safe, callable from any thread including main.
     */
    public void log(CrateLog entry) {
        logQueue.add(entry);
        // Eagerly flush if buffer is getting large (burst protection)
        if (logQueue.size() >= BATCH_SIZE * 5) {
            CompletableFuture.runAsync(this::flushQueue, asyncExecutor);
        }
    }

    /**
     * Forces all queued entries to flush synchronously.
     * Called on plugin disable to prevent log loss.
     */
    public void flushQueue() {
        if (logQueue.isEmpty()) return;

        List<CrateLog> batch = new ArrayList<>();
        CrateLog entry;
        while ((entry = logQueue.poll()) != null) {
            batch.add(entry);
            if (batch.size() >= BATCH_SIZE) {
                List<CrateLog> toInsert = new ArrayList<>(batch);
                db.insertLogBatch(toInsert);
                batch.clear();
            }
        }
        // Flush remaining
        if (!batch.isEmpty()) {
            db.insertLogBatch(new ArrayList<>(batch));
        }

        Logger.debug("Log flush: wrote entries to database.");
    }

    /**
     * Shuts down the scheduler. Call on plugin disable after flushQueue().
     */
    public void shutdown() {
        flushQueue();
        scheduler.shutdown();
    }
}
