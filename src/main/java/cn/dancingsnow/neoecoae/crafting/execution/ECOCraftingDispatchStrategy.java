package cn.dancingsnow.neoecoae.crafting.execution;

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
    private static final int ORDINARY_PUSHES_PER_TICK = 64;
    private final int[] usedOperations = new int[HISTORY_SIZE];

    /** Returns the ordinary one-copy operations available for the current tick. */
    public int beginTick() {
        return ORDINARY_PUSHES_PER_TICK;
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
        return usedOperations;
    }
}
