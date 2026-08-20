package cn.dancingsnow.neoecoae.impl.crafting.planner.service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded executor with independent initial-plan and continuation lanes.
 *
 * <p>Initial requests are allowed to backpressure without preventing already admitted
 * continuation work from making progress. Rejection is reported as a value so the caller can
 * use its ordinary AE2 fallback.</p>
 */
public final class ECOPlanningExecutor implements AutoCloseable {
    private final ThreadPoolExecutor initial;
    private final ThreadPoolExecutor continuation;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong queueFull = new AtomicLong();
    private final AtomicLong cancelled = new AtomicLong();
    private final AtomicLong shutdownRejected = new AtomicLong();

    public ECOPlanningExecutor(
            int initialWorkers,
            int continuationWorkers,
            int initialQueueCapacity,
            int continuationQueueCapacity,
            ThreadFactory threadFactory) {
        this.initial = newExecutor(initialWorkers, initialQueueCapacity, threadFactory);
        this.continuation = newExecutor(
                continuationWorkers, continuationQueueCapacity, threadFactory);
    }

    public Submission execute(Lane lane, Runnable task) {
        Objects.requireNonNull(lane, "lane");
        Objects.requireNonNull(task, "task");
        if (closed.get()) {
            shutdownRejected.incrementAndGet();
            return Submission.SHUTDOWN;
        }
        ThreadPoolExecutor executor = executor(lane);
        try {
            executor.execute(task);
            return Submission.ACCEPTED;
        } catch (RejectedExecutionException rejected) {
            if (closed.get() || executor.isShutdown()) {
                shutdownRejected.incrementAndGet();
                return Submission.SHUTDOWN;
            }
            queueFull.incrementAndGet();
            return Submission.QUEUE_FULL;
        }
    }

    /** Removes a cancelled queued task so its bounded lane capacity becomes available again. */
    public void recordCancellation(Runnable task) {
        Objects.requireNonNull(task, "task");
        boolean removed = initial.remove(task) || continuation.remove(task);
        if (removed) {
            cancelled.incrementAndGet();
            initial.purge();
            continuation.purge();
        }
    }

    public Diagnostics diagnostics() {
        return new Diagnostics(
                queueFull.get(),
                cancelled.get(),
                closed.get(),
                shutdownRejected.get()
        );
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        cancelQueued(initial.shutdownNow());
        cancelQueued(continuation.shutdownNow());
    }

    private ThreadPoolExecutor executor(Lane lane) {
        return lane == Lane.INITIAL ? initial : continuation;
    }

    private static ThreadPoolExecutor newExecutor(
            int workers,
            int queueCapacity,
            ThreadFactory threadFactory) {
        if (workers <= 0 || queueCapacity <= 0) {
            throw new IllegalArgumentException("workers and queue capacities must be positive");
        }
        return new ThreadPoolExecutor(
                workers,
                workers,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                Objects.requireNonNull(threadFactory, "threadFactory"),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    private static void cancelQueued(List<Runnable> queued) {
        for (Runnable task : queued) {
            if (task instanceof Future<?> future) {
                future.cancel(false);
            }
        }
    }

    public enum Lane {
        INITIAL,
        CONTINUATION
    }

    public enum Submission {
        ACCEPTED,
        QUEUE_FULL,
        SHUTDOWN
    }

    public record Diagnostics(
            long queueFull,
            long cancelled,
            boolean shutdown,
            long shutdownRejected) {
    }
}
