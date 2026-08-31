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
