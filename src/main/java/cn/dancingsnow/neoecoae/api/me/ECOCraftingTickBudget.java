package cn.dancingsnow.neoecoae.api.me;

import java.util.function.LongSupplier;

import cn.dancingsnow.neoecoae.config.NEConfig;

/**
 * Shared per-server-tick scheduling time budget for ECO crafting CPUs.
 *
 * <p>All ECO CPUs of all clusters and grids run their scheduling on the server thread within the
 * same server tick, so a single shared instance forms the ownership boundary for the budget:
 * once the combined scheduling time of this tick is spent, every remaining CPU defers its work
 * to the next tick instead of independently consuming the whole tick.
 *
 * <p>Time is accounted with {@link System#nanoTime()} deltas between {@link #beginWork()} and
 * {@link #endWork()}. While work is in progress, {@link #hasTimeRemaining()} also includes the
 * elapsed time of the current work section, so long-running scheduling passes observe exhaustion
 * as soon as it happens. The budget only ever defers scheduling of new pattern pushes; it is
 * checked between operations, never in the middle of an ownership transfer.
 *
 * <p>Not thread safe. Only ever touched from the server thread.
 */
public final class ECOCraftingTickBudget {
    private static final ECOCraftingTickBudget SHARED = new ECOCraftingTickBudget(
        () -> (long) Math.max(1, NEConfig.ecoCpuTickBudgetMicros) * 1000L,
        System::nanoTime
    );

    private final LongSupplier budgetNanosSupplier;
    private final LongSupplier nanoTimeSource;

    private long activeTickId = Long.MIN_VALUE;
    private long budgetNanos;
    private long consumedNanos;
    private int workDepth;
    private long workStartNanos;

    ECOCraftingTickBudget(LongSupplier budgetNanosSupplier, LongSupplier nanoTimeSource) {
        this.budgetNanosSupplier = budgetNanosSupplier;
        this.nanoTimeSource = nanoTimeSource;
    }

    public static ECOCraftingTickBudget shared() {
        return SHARED;
    }

    /**
     * Marks the given server tick as the accounting window. The first call of a new tick resets
     * the consumed time; repeated calls within the same tick (one per CPU) keep the shared state.
     */
    public void startTick(long tickId) {
        if (tickId == activeTickId) {
            return;
        }
        activeTickId = tickId;
        consumedNanos = 0L;
        // Clamp to at least one nanosecond so a fresh tick always admits the first operation and
        // crafting can never stall permanently, regardless of the configured value.
        budgetNanos = Math.max(1L, budgetNanosSupplier.getAsLong());
        // Defensive: an Error thrown between beginWork/endWork must not poison later ticks.
        workDepth = 0;
    }

    public boolean hasTimeRemaining() {
        return currentConsumedNanos() < budgetNanos;
    }

    public boolean isExhausted() {
        return !hasTimeRemaining();
    }

    /**
     * Starts measuring a scheduling work section. Must be balanced by {@link #endWork()} in a
     * {@code finally} block. Nested sections are tolerated and counted once.
     */
    public void beginWork() {
        if (++workDepth == 1) {
            workStartNanos = nanoTimeSource.getAsLong();
        }
    }

    public void endWork() {
        if (workDepth > 0 && --workDepth == 0) {
            consumedNanos += elapsedWorkNanos();
        }
    }

    private long currentConsumedNanos() {
        return workDepth > 0 ? consumedNanos + elapsedWorkNanos() : consumedNanos;
    }

    private long elapsedWorkNanos() {
        return Math.max(0L, nanoTimeSource.getAsLong() - workStartNanos);
    }
}
