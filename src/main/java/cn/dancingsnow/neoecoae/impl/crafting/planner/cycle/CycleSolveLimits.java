package cn.dancingsnow.neoecoae.impl.crafting.planner.cycle;

/**
 * Hard operational budget for the bounded cycle solver, and the single source of truth for its defaults.
 *
 * <p>Every limit is an operational cut-off, never a correctness claim: exceeding one produces
 * {@link CycleSolveStatus#TOO_COMPLEX} or {@link CycleSolveStatus#UNKNOWN_BUDGET}, never a
 * missing-items verdict.
 *
 * <p>Canonical stage-one defaults, mirrored by {@code planner.maxScc*} in the design document's Appendix B:
 * {@code maxKeys = 8}, {@code maxPatterns = 16}, {@code maxStates = 100000}, {@code maxFirings = 100000},
 * {@code maxSeedLadderSteps = 12}.
 *
 * @param maxKeys      relevant-key cap for one SCC; above it the component is {@code TOO_COMPLEX}
 * @param maxPatterns  pattern cap for one SCC; above it the component is {@code TOO_COMPLEX}
 * @param maxStates    distinct-marking cap shared by the first search and the whole seed ladder
 * @param maxFirings   breadth-first depth cap. This is an operational cap of the current per-firing BFS
 *                     model, not a mathematical guarantee: one firing is one pattern execution, so a target
 *                     of N units costs roughly N depth. Re-evaluate it if batch firing or witness
 *                     compression is ever added.
 * @param maxSeedLadderSteps number of doubling steps the seed ladder may verify
 */
public record CycleSolveLimits(
    int maxKeys,
    int maxPatterns,
    int maxStates,
    int maxFirings,
    int maxSeedLadderSteps
) {
    /** Stage-one defaults: only small, inventory-aware SCCs are attempted. */
    /**
     * The firing budget is sized for the large batch counts used by high-tier storage recipes. The bounded
     * solver still remains finite, while callers that need a tighter latency bound can provide explicit limits.
     */
    public static final CycleSolveLimits DEFAULT = new CycleSolveLimits(8, 16, 100_000, 100_000, 12);

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
