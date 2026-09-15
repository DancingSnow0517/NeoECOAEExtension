package cn.dancingsnow.neoecoae.api.me;

import cn.dancingsnow.neoecoae.config.NEConfig;

/**
 * Per-tick ordinary crafting budget for an ECO CPU.
 *
 * <p>The budget is deliberately limited to ordinary one-copy provider pushes. ECO FastPath batches are
 * already bounded by the live provider/worker capacity and therefore do not consume this budget.</p>
 *
 * <p>Each tick receives at most 64 ordinary pushes, independently of co-processors and previous ticks.</p>
 */
public final class ECOCraftingDispatchStrategy {
    private static final int HISTORY_SIZE = 3;
    private final int[] usedOperations = new int[HISTORY_SIZE];

    /**
     * Returns the ordinary one-copy operations available for the current tick.
     *
     * @param coProcessors number of CPU co-processors
     * @param configuredLimit ECO's ordinary-path safety limit
     */
    public int beginTick(int coProcessors, int configuredLimit) {
        long cpuLimit = 64L;
        long safeConfiguredLimit = Math.min(
            (long) NEConfig.MAX_ECO_CPU_PUSH_TICK_LIMIT,
            Math.max(0L, configuredLimit)
        );
        long available = Math.min(cpuLimit, safeConfiguredLimit);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, available));
    }

    /** Records only accepted ordinary pushes; FastPath batches must not be passed here. */
    public void finishTick(int acceptedOrdinaryOperations) {
        usedOperations[2] = usedOperations[1];
        usedOperations[1] = usedOperations[0];
        usedOperations[0] = Math.max(0, acceptedOrdinaryOperations);
    }

    /** Clears the transient rolling window after a CPU is restored or a job lifecycle is reset. */
    public void reset() {
        java.util.Arrays.fill(usedOperations, 0);
    }

    int[] usedOperationsForTest() {
        return usedOperations.clone();
    }
}
