package cn.dancingsnow.neoecoae.impl.crafting.planner.cycle;

/**
 * Outcome of one cycle solve attempt.
 *
 * <p>The distinction between {@link #INSUFFICIENT_EXTERNAL_INPUT} and {@link #UNKNOWN_BUDGET} is the
 * load-bearing one: the former is only ever returned when the reachable state space was closed
 * exhaustively, the latter whenever the search stopped early for any reason. Neither is ever
 * translated into a plain "missing items" verdict by the caller.
 */
public enum CycleSolveStatus {
    /** A firing order exists and its seed is covered by the supplied stock. */
    SUCCESS,
    /**
     * Proven, at the supplied stock snapshot, that no firing order satisfies the required outputs.
     * The result carries the seed / external material that would be needed instead.
     */
    INSUFFICIENT_EXTERNAL_INPUT,
    /** The search stopped on its state or firing-depth budget. Nothing is proven. */
    UNKNOWN_BUDGET,
    /** The component is larger than the stage-one structural limits; no search was attempted. */
    TOO_COMPLEX,
    /** A pattern inside the SCC is not batch-safe (substitutions, remainders, malformed amounts). */
    UNSUPPORTED_PATTERN,
    /** The exact cycle contract is known, but an AE2 long-valued execution field cannot carry it. */
    UNREPRESENTABLE,
    /** Cancellation was observed and converted into a value instead of an exception. */
    CANCELLED,
    /** Reported by the deliberately inert stage-zero solver. */
    NOT_IMPLEMENTED;

    public boolean solved() {
        return this == SUCCESS;
    }

    /** True when the answer is a proof rather than a budget or capability cut-off. */
    public boolean proven() {
        return this == SUCCESS || this == INSUFFICIENT_EXTERNAL_INPUT;
    }
}
