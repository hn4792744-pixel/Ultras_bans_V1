package com.ultras.bans.database;

import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Single dedicated thread pool for every JDBC call the plugin makes. Sized
 * modestly (4 threads) since SQLite only benefits from one writer at a time
 * anyway and MySQL connections are pooled separately by HikariCP; this just
 * keeps JDBC calls off the Bukkit main thread and off Bukkit's own async
 * scheduler pool (spec section 52: no sync database calls, no duplicate tasks).
 */
public final class DatabaseExecutor implements Executor {

    private final ExecutorService pool;
    private final Logger logger;

    public DatabaseExecutor(Logger logger) {
        this.logger = logger;
        this.pool = new ThreadPoolExecutor(
                2, 4, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "ULTRASbans-DB-Worker");
                    t.setDaemon(true);
                    return t;
                }
        );
    }

    @Override
    public void execute(Runnable command) {
        pool.execute(command);
    }

    /** Graceful shutdown called from onDisable - waits briefly for in-flight writes to finish before forcing. */
    public void shutdown() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("Database executor did not terminate in time, forcing shutdown.");
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
        }
    }

    public boolean isShutdown() {
        return pool.isShutdown();
    }
}
