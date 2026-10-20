package cn.dancingsnow.neoecoae.crafting.planner.cycle;

/**
 * Optional caller-specified cutoffs, independent of the shared time/work and memory budgets.
 *
 * <p>Every limit is an operational cut-off, never a correctness claim: exceeding one produces
 * {@link CycleSolveStatus#TOO_COMPLEX} or {@link CycleSolveStatus#UNKNOWN_BUDGET}, never a
 * missing-items verdict.
 *
 * @param maxKeys      relevant-key cap, or zero for no structural cutoff
 * @param maxPatterns  distinct physical pattern cap, or zero for no structural cutoff
 * @param maxStates    distinct-marking cap, or zero to use only resource budgets
 * @param maxFirings   search macro-step cap. A macro-step may contain a verified batch of the same pattern, so
 *                     this is no longer a cap on the exact pattern firing counts. It remains an operational cap,
 *                     not a mathematical guarantee; zero disables the depth cutoff.
 * @param maxSeedLadderSteps legacy field retained for source compatibility; seed requirements are now
 *                           calculated from full-order prefix deficits instead of exponential probes
 */
public record CycleSolveLimits(
    int maxKeys,
    int maxPatterns,
    int maxStates,
    int maxFirings,
    int maxSeedLadderSteps
) {
    public static final CycleSolveLimits DEFAULT = new CycleSolveLimits(0, 0, 0, 0, 0);
    /** Compatibility alias; the planner no longer promotes requests into a million-state tier. */
    @Deprecated
    public static final CycleSolveLimits LARGE = DEFAULT;

    /**
     * Compatibility entry point. Graph dimensions do not predict reachability or required work.
     * Actual retained data is charged to a memory budget; checkpoints share ECOPlanningBudget.
     */
    public static CycleSolveLimits forWorkload(int keys, int transitions, int competingChoices) {
        return DEFAULT;
    }

    public CycleSolveLimits {
        if (maxKeys < 0 || maxPatterns < 0 || maxStates < 0 || maxFirings < 0 || maxSeedLadderSteps < 0) {
            throw new IllegalArgumentException("Cycle solver limits must not be negative");
        }
    }

    public CycleSolveLimits withMaxStates(int states) {
        return new CycleSolveLimits(maxKeys, maxPatterns, states, maxFirings, maxSeedLadderSteps);
    }

    public CycleSolveLimits withMaxFirings(int firings) {
        return new CycleSolveLimits(maxKeys, maxPatterns, maxStates, firings, maxSeedLadderSteps);
    }

    public CycleSolveLimits withMaxKeys(int keys) {
        return new CycleSolveLimits(keys, maxPatterns, maxStates, maxFirings, maxSeedLadderSteps);
    }

    public CycleSolveLimits withMaxPatterns(int patterns) {
        return new CycleSolveLimits(maxKeys, patterns, maxStates, maxFirings, maxSeedLadderSteps);
    }
}
