package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

public record ECOSolveBudget(
    long maxExpandedStates,
    int maxDepth,
    int extraBatchChoices,
    long maxDurationNanos
) {
    private static final long DEFAULT_MAX_DURATION_NANOS = 1_000_000_000L;
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

    public static boolean shouldStop(long deadlineNanos) {
        return Thread.currentThread().isInterrupted()
            || (deadlineNanos != Long.MAX_VALUE && System.nanoTime() - deadlineNanos >= 0L);
    }
}
