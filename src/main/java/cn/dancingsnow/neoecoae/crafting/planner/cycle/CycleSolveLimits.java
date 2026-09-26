package cn.dancingsnow.neoecoae.crafting.planner.cycle;

/**
 * Hard operational budget for the bounded cycle solver, and the single source of truth for its defaults.
 *
 * <p>Every limit is an operational cut-off, never a correctness claim: exceeding one produces
 * {@link CycleSolveStatus#TOO_COMPLEX} or {@link CycleSolveStatus#UNKNOWN_BUDGET}, never a
 * missing-items verdict.
 *
 * @param maxKeys      relevant-key cap for one SCC; above it the component is {@code TOO_COMPLEX}
 * @param maxPatterns  pattern cap for one SCC; above it the component is {@code TOO_COMPLEX}
 * @param maxStates    distinct-marking cap shared by the first search and the whole seed ladder
 * @param maxFirings   search macro-step cap. A macro-step may contain a verified batch of the same pattern, so
 *                     this is no longer a cap on the exact pattern firing counts. It remains an operational cap,
 *                     not a mathematical guarantee: targets that need more interleaving steps remain unknown.
 * @param maxSeedLadderSteps number of doubling steps the seed ladder may verify
 */
public record CycleSolveLimits(
    int maxKeys,
    int maxPatterns,
    int maxStates,
    int maxFirings,
    int maxSeedLadderSteps
) {
    /** Structural safety caps are independent of the search allowance. */
    public static final CycleSolveLimits DEFAULT = new CycleSolveLimits(256, 64, 100_000, 100_000, 12);
    /** Compatibility alias; the planner no longer promotes requests into a million-state tier. */
    @Deprecated
    public static final CycleSolveLimits LARGE = DEFAULT;

    /**
     * Estimate work per marking from its width and outgoing transitions. Competing producers/consumers
     * receive some extra exploration, while wide inventories reduce the number of retained markings.
     * External ingredients affect per-state cost, but never trigger a larger latency tier.
     * The calculation-wide ECOPlanningBudget remains the hard time/work deadline across all attempts.
     */
    public static CycleSolveLimits forWorkload(int keys, int transitions, int competingChoices) {
        long perState = Math.max(4L, keys) * Math.max(1L, transitions);
        long work = 1_600_000L * (1L + Math.min(4, Math.max(0, competingChoices)));
        int states = (int) Math.max(2_048L, Math.min(DEFAULT.maxStates(), work / perState));
        return new CycleSolveLimits(DEFAULT.maxKeys(), DEFAULT.maxPatterns(), states, states,
            DEFAULT.maxSeedLadderSteps());
    }

    public CycleSolveLimits {
        if (maxKeys < 1 || maxPatterns < 1 || maxStates < 1 || maxFirings < 1 || maxSeedLadderSteps < 0) {
            throw new IllegalArgumentException("Cycle solver limits must be positive");
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
