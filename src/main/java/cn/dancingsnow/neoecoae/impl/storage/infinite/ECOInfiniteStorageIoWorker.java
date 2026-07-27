package cn.dancingsnow.neoecoae.impl.storage.infinite;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class ECOInfiniteStorageIoWorker {
    private static final Object LOCK = new Object();

    private static ExecutorService walExecutor;
    private static ExecutorService checkpointExecutor;
    private static ExecutorService loadExecutor;

    private ECOInfiniteStorageIoWorker() {}

    static Future<?> submit(Runnable task) {
        synchronized (LOCK) {
            return walExecutor().submit(task);
        }
    }

    static Future<?> submitCheckpoint(Runnable task) {
        synchronized (LOCK) {
            return checkpointExecutor().submit(task);
        }
    }

    static <T> Future<T> submitLoad(Callable<T> task) {
        synchronized (LOCK) {
            return loadExecutor().submit(task);
        }
    }

    static void shutdown() {
        ExecutorService currentWal;
        ExecutorService currentCheckpoint;
        ExecutorService currentLoad;
        synchronized (LOCK) {
            currentWal = walExecutor;
            currentCheckpoint = checkpointExecutor;
            currentLoad = loadExecutor;
            walExecutor = null;
            checkpointExecutor = null;
            loadExecutor = null;
        }
        shutdown(currentLoad);
        shutdown(currentWal);
        shutdown(currentCheckpoint);
    }

    private static ExecutorService walExecutor() {
        if (walExecutor == null) {
            walExecutor = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "NeoECOAE-InfiniteStorage-WAL");
                thread.setDaemon(true);
                return thread;
            });
        }
        return walExecutor;
    }

    private static ExecutorService checkpointExecutor() {
        if (checkpointExecutor == null) {
            checkpointExecutor = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "NeoECOAE-InfiniteStorage-Checkpoint");
                thread.setDaemon(true);
                return thread;
            });
        }
        return checkpointExecutor;
    }

    private static ExecutorService loadExecutor() {
        if (loadExecutor == null) {
            loadExecutor = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "NeoECOAE-InfiniteStorage-Load");
                thread.setDaemon(true);
                return thread;
            });
        }
        return loadExecutor;
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
