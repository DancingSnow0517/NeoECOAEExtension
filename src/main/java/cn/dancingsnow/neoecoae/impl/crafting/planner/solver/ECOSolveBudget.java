package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

public record ECOSolveBudget(long maxExpandedStates, int maxDepth, int extraBatchChoices, long maxDurationNanos) {
    private static final long DEFAULT_MAX_DURATION_NANOS = 4_000_000_000L;
    private static final long DEBUG_MAX_DURATION_NANOS = 30_000_000_000L;
    public static final ECOSolveBudget DEFAULT = new ECOSolveBudget(50_000, 256, 2);

    public ECOSolveBudget(long maxExpandedStates, int maxDepth, int extraBatchChoices) {
        this(maxExpandedStates, maxDepth, extraBatchChoices, DEFAULT_MAX_DURATION_NANOS);
    }

    public ECOSolveBudget {
        if (maxExpandedStates <= 0 || maxDepth <= 0 || extraBatchChoices < 0 || maxDurationNanos <= 0) {
            throw new IllegalArgumentException("Invalid ECO solve budget");
        }
    }

    public long deadlineNanos() {
        long now = System.nanoTime();
        long deadline = now + maxDurationNanos;
        return deadline < now ? Long.MAX_VALUE : deadline;
    }

    /** Expands diagnostic runs without changing normal server planning limits. */
    public ECOSolveBudget forDebug() {
        return new ECOSolveBudget(
                Math.max(maxExpandedStates, 5_000_000L),
                Math.max(maxDepth, 1_024),
                Math.max(extraBatchChoices, 16),
                Math.max(maxDurationNanos, DEBUG_MAX_DURATION_NANOS));
    }

    public boolean extendedForDebug() {
        return maxDurationNanos > DEFAULT_MAX_DURATION_NANOS;
    }

    public static boolean shouldStop(long deadlineNanos) {
        return Thread.currentThread().isInterrupted()
                || (deadlineNanos != Long.MAX_VALUE && System.nanoTime() - deadlineNanos >= 0L);
    }

    static long phaseDeadline(long overallDeadlineNanos, long phaseDurationNanos) {
        if (overallDeadlineNanos == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        long now = System.nanoTime();
        long phaseDeadline = now + phaseDurationNanos;
        if (phaseDeadline < now) {
            phaseDeadline = Long.MAX_VALUE;
        }
        return Math.min(overallDeadlineNanos, phaseDeadline);
    }
}
