package cn.dancingsnow.neoecoae.crafting.planner;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import org.jetbrains.annotations.Nullable;

/** Bounded daemon executor for explicit ECO planning requests. */
public final class ECOPlanningExecutor {
    private static final int MAX_WORKERS = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
        0, MAX_WORKERS, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32),
        runnable -> {
            Thread thread = new Thread(runnable, "ECO Planning Worker");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    // Optional work has its own queue and worker so it cannot occupy a foreground calculation slot.
    private static final ThreadPoolExecutor IMPROVEMENTS = new ThreadPoolExecutor(
        1, 1, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16), runnable -> {
            Thread thread = new Thread(runnable, "ECO Plan Improvement Worker");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());

    private ECOPlanningExecutor() {
    }

    public static <T> Future<T> submit(Callable<T> task) {
        return EXECUTOR.submit(task);
    }

    public static <T> @Nullable Future<T> tryImprove(Callable<T> task) {
        IMPROVEMENTS.purge();
        try {
            return IMPROVEMENTS.submit(task);
        } catch (RejectedExecutionException full) {
            return null;
        }
    }
}
