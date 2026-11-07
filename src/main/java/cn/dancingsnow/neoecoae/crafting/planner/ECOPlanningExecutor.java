package cn.dancingsnow.neoecoae.crafting.planner;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Bounded daemon executor for explicit ECO planning requests. */
public final class ECOPlanningExecutor {
    private static final int MAX_WORKERS = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
        MAX_WORKERS, MAX_WORKERS, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32),
        runnable -> {
            Thread thread = new Thread(runnable, "ECO Planning Worker");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());

    static {
        EXECUTOR.allowCoreThreadTimeOut(true);
    }

    private ECOPlanningExecutor() {
    }

    public static <T> Future<T> submit(Callable<T> task) {
        EXECUTOR.purge();
        return EXECUTOR.submit(task);
    }
}
