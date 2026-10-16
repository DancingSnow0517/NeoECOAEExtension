package cn.dancingsnow.neoecoae.core.execution;

/** Bounded scheduling arithmetic, independent of a loader, inventory or pattern representation. */
public final class DispatchScheduling {
    public static final int MIN_NORMAL_PROBES = 64;
    public static final int MAX_CANDIDATE_INSPECTIONS = 64;

    private DispatchScheduling() {}

    public static int probeLimit(int operationLimit) {
        return Math.max(MIN_NORMAL_PROBES, operationLimit);
    }

    public static int taskDispatchLimit(int operationLimit) {
        return Math.max(1, Math.min(MAX_CANDIDATE_INSPECTIONS, Math.max(0, operationLimit)));
    }

    public static int addAccepted(int current, long accepted) {
        // Do not add before clamping: accepted may be Long.MAX_VALUE.
        return accepted >= Integer.MAX_VALUE - (long) current ? Integer.MAX_VALUE
            : current + (int) Math.max(0L, accepted);
    }
}
