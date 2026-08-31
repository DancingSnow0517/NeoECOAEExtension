package cn.dancingsnow.neoecoae.impl.crafting.planner.cycle;

/** Search accounting, reported for every outcome so budget verdicts stay auditable. */
public record CycleSolveMetrics(
    int relevantKeys,
    int transitions,
    long statesVisited,
    long statesExpanded,
    /** Expanded per-firing witness length; zero means the exact witness is retained only in compact batch form. */
    int witnessLength,
    int seedLadderSteps,
    boolean stateBudgetExhausted,
    boolean firingDepthTruncated,
    boolean amountOverflowTruncated
) {
    public static final CycleSolveMetrics NONE =
        new CycleSolveMetrics(0, 0, 0, 0, 0, 0, false, false, false);

    public CycleSolveMetrics(int relevantKeys, int transitions, long statesVisited, long statesExpanded,
            int witnessLength, int seedLadderSteps, boolean stateBudgetExhausted, boolean firingDepthTruncated) {
        this(relevantKeys, transitions, statesVisited, statesExpanded, witnessLength, seedLadderSteps,
            stateBudgetExhausted, firingDepthTruncated, false);
    }

    /** True when the search stopped early, so no infeasibility claim may be derived from it. */
    public boolean truncated() {
        return stateBudgetExhausted || firingDepthTruncated || amountOverflowTruncated;
    }
}
