package cn.dancingsnow.neoecoae.grid;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** Server-thread lease and shared time budget for pattern migration on one AE2 grid. */
public final class PatternMigrationCoordinator {
    /** Keep one grid's migration below one tenth of a normal 20 TPS tick. */
    public static final long MAX_NANOS_PER_TICK = 4_000_000L;

    private static final Map<Object, PatternMigrationCoordinator> BY_GRID =
            Collections.synchronizedMap(new WeakHashMap<>());

    private Object owner;
    private long budgetGameTime = Long.MIN_VALUE;
    private long budgetDeadlineNanos;
    private long completedSlices;
    private long totalSliceNanos;
    private long lastSliceNanos;
    private long scannedSlots;
    private long insertedPatterns;

    /** Returns the coordinator associated with a grid without retaining that grid. */
    public static PatternMigrationCoordinator forGrid(Object grid) {
        Objects.requireNonNull(grid, "grid");
        synchronized (BY_GRID) {
            return BY_GRID.computeIfAbsent(grid, ignored -> new PatternMigrationCoordinator());
        }
    }

    /** Acquires the one migration lease for this grid. Calls are expected on the server thread. */
    public boolean tryAcquire(Object requester) {
        Objects.requireNonNull(requester, "requester");
        if (owner == null || owner == requester) {
            owner = requester;
            return true;
        }
        return false;
    }

    public boolean isOwner(Object requester) {
        return owner == requester;
    }

    public void release(Object requester) {
        if (owner == requester) {
            owner = null;
        }
    }

    /** Starts or returns this tick's shared budget; zero means the requester does not hold the lease. */
    public long beginSlice(Object requester, long gameTime) {
        if (owner != requester) {
            return 0L;
        }
        if (budgetGameTime != gameTime) {
            budgetGameTime = gameTime;
            budgetDeadlineNanos = System.nanoTime() + MAX_NANOS_PER_TICK;
        }
        return budgetDeadlineNanos;
    }

    /** Records lightweight timing counters for diagnostics. */
    public void recordSlice(long elapsedNanos, int scannedSlotsThisSlice, int insertedPatternsThisSlice) {
        lastSliceNanos = Math.max(0L, elapsedNanos);
        totalSliceNanos += lastSliceNanos;
        completedSlices++;
        scannedSlots += Math.max(0, scannedSlotsThisSlice);
        insertedPatterns += Math.max(0, insertedPatternsThisSlice);
    }

    public long completedSlices() {
        return completedSlices;
    }

    public long totalSliceNanos() {
        return totalSliceNanos;
    }

    public long lastSliceNanos() {
        return lastSliceNanos;
    }

    public long scannedSlots() {
        return scannedSlots;
    }

    public long insertedPatterns() {
        return insertedPatterns;
    }
}
