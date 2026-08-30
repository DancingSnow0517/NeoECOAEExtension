package cn.dancingsnow.neoecoae.impl.crafting.planner.cycle;

/** One structured explanation entry attached to a {@link CycleSolveResult}. */
public record CycleSolveDiagnostic(Code code, String message) {
    public enum Code {
        /** The required outputs were already covered by relevant stock; no firing was needed. */
        SATISFIED_FROM_STOCK,
        /** A concrete firing order was found. */
        WITNESS_FOUND,
        /**
         * One validated pattern forms the whole cycle on its own and grows its feedback key, so the firing
         * count, seed and external demand were computed as exact integer algebra instead of searched.
         */
        SINGLE_PATTERN_NET_GROWTH,
        /** The single-pattern net growth calculator declined; the bounded search answered instead. */
        NET_GROWTH_NOT_APPLICABLE,
        /** The witness' start-up seed is fully covered by the supplied stock. */
        SEED_COVERED_BY_STOCK,
        /** The witness needs more start-up material than the stock snapshot holds. */
        SEED_SHORTFALL,
        /** A seed ladder point was verified by an explicit search, so the seed figure is sufficient. */
        SEED_LADDER_VERIFIED,
        /** No ladder point verified in budget; the reported seed is a lower bound, not a guarantee. */
        SEED_ESTIMATE_LOWER_BOUND,
        /** The reachable state space was closed without reaching the target at the current stock. */
        PROVEN_INFEASIBLE_AT_CURRENT_STOCK,
        /** The SCC can fire but no firing changes the deficit; it cannot create the required output. */
        NO_PRODUCTIVE_FIRING,
        STATE_BUDGET_EXHAUSTED,
        FIRING_DEPTH_TRUNCATED,
        KEY_LIMIT_EXCEEDED,
        PATTERN_LIMIT_EXCEEDED,
        UNSUPPORTED_PATTERN,
        NO_TRANSITIONS,
        AMOUNT_OVERFLOW,
        EXECUTION_AMOUNT_UNREPRESENTABLE,
        CANCELLED,
        NOT_IMPLEMENTED,
        SEARCH_METRICS
    }
}
