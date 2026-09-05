package cn.dancingsnow.neoecoae.impl.crafting.planner.growth;

/**
 * Independent capability bits a pattern can earn from the validation evidence recorded for it.
 *
 * <p>The two bits are deliberately <em>not</em> ordered and never imply one another:
 *
 * <ul>
 *   <li>{@link #FAST_PATH_SAFE} answers "may one execution of this pattern take the smart-pattern-bus
 *       virtual/batch fast path?". It is the recorded fast-path verdict.</li>
 *   <li>{@link #NET_GROWTH_SAFE} answers "may this pattern's static input/output contract be used as
 *       exact integer algebra?". It requires a determinate input count, a determinate output count, a
 *       determinate (stage one: provably absent) remainder, no substitution set, no randomized output, no
 *       unmodellable stateful input and no durability/state transfer.</li>
 * </ul>
 *
 * <p>{@code FAST_PATH_SAFE != NET_GROWTH_SAFE} in both directions. A pattern may be planned by the net
 * growth calculator and still execute through the ordinary AE2 provider path, and a pattern may be
 * fast-path eligible while its contract is too weakly determined to plan algebraically.
 */
public enum PatternCapability {
    /** Recorded smart-pattern-bus / fast-path verdict for one pattern execution. */
    FAST_PATH_SAFE,
    /** Recorded verdict that the static input/output contract is exact enough for integer algebra. */
    NET_GROWTH_SAFE
}
