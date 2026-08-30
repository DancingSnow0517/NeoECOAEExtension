package cn.dancingsnow.neoecoae.impl.crafting.planner.growth;

/** Outcome of one single-pattern net growth evaluation. */
public enum SinglePatternGrowthStatus {
    /**
     * The firing count, seed and external demand were computed exactly, and the feedback seed the loop needs
     * to start is covered by the supplied stock.
     */
    SUCCESS,
    /**
     * The algebra succeeded but the stock snapshot cannot cover the start-up seed. The result carries
     * {@code requiredSeed} and {@code seedShortfall}; it is a report, not a plan.
     */
    INSUFFICIENT_SEED,
    /** The exact algebra found a value that cannot be carried by an AE2 long-valued execution field. */
    UNREPRESENTABLE,
    /**
     * This calculator does not apply. The caller must fall back to the bounded cycle solver without any
     * side effect, and must never turn this into a missing-items or unsupported verdict.
     */
    NOT_APPLICABLE,
    /** Legacy status retained for source compatibility; new arithmetic uses {@link #UNREPRESENTABLE}. */
    OVERFLOW;

    /** True only when a plan may be adopted from this result. */
    public boolean solved() {
        return this == SUCCESS;
    }

    /** True when the bounded cycle solver has to answer instead. */
    public boolean requiresBoundedFallback() {
        return this != SUCCESS;
    }
}
