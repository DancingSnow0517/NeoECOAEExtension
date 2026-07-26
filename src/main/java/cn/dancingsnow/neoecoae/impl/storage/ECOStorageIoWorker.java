package cn.dancingsnow.neoecoae.impl.storage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Write-behind threads shared by every ECO storage subsystem: one for WAL appends, one for checkpoints.
 *
 * <p>Keeping WAL appends on a single thread is what lets a whole tick's mutations - across every cell and every
 * infinite domain - be amortised into one fsync.
 */
public final class ECOStorageIoWorker {
    private static final Object LOCK = new Object();

    private static ExecutorService walExecutor;
    private static ExecutorService checkpointExecutor;

    private ECOStorageIoWorker() {}

    public static Future<?> submit(Runnable task) {
        synchronized (LOCK) {
            return walExecutor().submit(task);
        }
    }

    public static Future<?> submitCheckpoint(Runnable task) {
        synchronized (LOCK) {
            return checkpointExecutor().submit(task);
        }
    }

    public static void shutdown() {
        ExecutorService currentWal;
        ExecutorService currentCheckpoint;
        synchronized (LOCK) {
            currentWal = walExecutor;
            currentCheckpoint = checkpointExecutor;
            walExecutor = null;
            checkpointExecutor = null;
        }
        shutdown(currentWal);
        shutdown(currentCheckpoint);
    }

    private static ExecutorService walExecutor() {
        if (walExecutor == null) {
            walExecutor = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "NeoECOAE-Storage-WAL");
                thread.setDaemon(true);
                return thread;
            });
        }
        return walExecutor;
    }

    private static ExecutorService checkpointExecutor() {
        if (checkpointExecutor == null) {
            checkpointExecutor = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "NeoECOAE-Storage-Checkpoint");
                thread.setDaemon(true);
                return thread;
            });
        }
        return checkpointExecutor;
    }

    private static void shutdown(ExecutorService executor) {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
