package cn.dancingsnow.neoecoae.impl.crafting.planner.growth;

/**
 * Why {@link PatternCapability#NET_GROWTH_SAFE} was withheld from a pattern.
 *
 * <p>Every value names a concrete piece of <em>recorded</em> validation evidence that is missing or
 * negative. None of them is derived from a fresh, ad-hoc reading of {@code IPatternDetails}.
 */
public enum NetGrowthRejection {
    /** The capability was granted. */
    NONE,
    /** The compiled view carries no pattern instance at all. */
    MISSING_DETAILS,
    /** The compile-time determinism probe failed: two reads of the static contract disagreed. */
    UNSTABLE_STATIC_CONTRACT,
    /**
     * The recorded compile-time reason code reports an indeterminacy of the contract itself — a
     * substitution set, a remainder, a malformed pattern, an overflowing amount.
     */
    UNSUPPORTED_BY_COMPILE_EVIDENCE,
    /** An output is missing, keyless or non-positive, so production is not a determinate number. */
    INDETERMINATE_OUTPUT,
    /** An input is not recorded as determinate, or its per-pattern amount is not positive. */
    INDETERMINATE_INPUT,
    /** A pattern with no inputs cannot form a self-loop and is not a growth cycle. */
    NO_INPUTS,
    /** Aggregating the recorded per-pattern amounts already leaves the representable range. */
    AMOUNT_OVERFLOW
}
