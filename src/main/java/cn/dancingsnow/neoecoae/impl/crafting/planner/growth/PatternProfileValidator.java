package cn.dancingsnow.neoecoae.impl.crafting.planner.growth;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The single place that turns recorded pattern validation evidence into {@link PatternCapability} bits and
 * an exact per-firing static contract.
 *
 * <h2>Where the evidence comes from</h2>
 * Nothing here reads {@code IPatternDetails} again. Every decision is a function of what
 * {@code CraftingNetworkCompiler} carried from its independent sources:
 *
 * <ul>
 *   <li>{@link CompiledPattern#fastSupported()} and {@link CompiledPattern#unsupportedReason()} — the
 *       recorded fast-path verdict and its reason code.</li>
 *   <li>The compiled input, output and determinate remainder vectors.</li>
 *   <li>{@link CompiledPattern#netGrowthValidated()} — capability evidence issued when the smart pattern
 *       bus validates and publishes the decoded pattern.</li>
 *   <li>The recorded output snapshot and the recorded per-pattern input amounts.</li>
 * </ul>
 *
 * <h2>Two independent rule sets</h2>
 * {@link #fastPathSafe} and {@link #netGrowthRejection} are computed separately and neither reads the
 * other's answer, so the two capabilities can differ in both directions. A view whose recorded reason is
 * {@code PRIMARY_OUTPUT_MISMATCH} describes the <em>compiled view</em> (this view was compiled for a key the
 * pattern does not produce) rather than an indeterminacy of the contract, so it blocks the fast path while
 * leaving the algebra perfectly well defined.
 *
 * <h2>Stage-one strictness</h2>
 * {@code NET_GROWTH_SAFE} additionally demands that <em>every</em> input slot be recorded as determinate.
 * Determinate static remainders are retained. Durability state transfer, fuzzy substitutions and dynamic
 * quantities are rejected at the smart-pattern-bus validation boundary before the capability is issued.
 */
public final class PatternProfileValidator {

    /**
     * Recorded reason codes that describe the compiled <em>view</em>, not an indeterminacy of the pattern's
     * static contract. Every other code withholds {@link PatternCapability#NET_GROWTH_SAFE}.
     */
    private static final Set<String> CONTRACT_SAFE_REASONS =
        Set.of("", "PRIMARY_OUTPUT_MISMATCH", "UNSUPPORTED_REMAINDER");

    /** Reads recorded evidence only; never touches the pattern instance. */
    public ValidatedPatternProfile validate(CompiledPattern pattern) {
        if (pattern == null) throw new IllegalArgumentException("Cannot validate a null compiled pattern");

        EnumSet<PatternCapability> capabilities = EnumSet.noneOf(PatternCapability.class);
        if (fastPathSafe(pattern)) capabilities.add(PatternCapability.FAST_PATH_SAFE);

        Map<AEKey, Long> consumption = new LinkedHashMap<>();
        Map<AEKey, Long> production = new LinkedHashMap<>();
        Map<AEKey, Long> remainder = new LinkedHashMap<>();
        NetGrowthRejection rejection = netGrowthRejection(pattern, consumption, production, remainder);
        if (rejection == NetGrowthRejection.NONE) {
            capabilities.add(PatternCapability.NET_GROWTH_SAFE);
        } else {
            // A rejected pattern must not expose numbers anybody could mistake for a verified contract.
            consumption.clear();
            production.clear();
            remainder.clear();
        }
        return ValidatedPatternProfile.trusted(this, pattern, capabilities, consumption, production, remainder,
            rejection);
    }

    /** Recorded fast-path verdict, pattern level and slot level. Independent of the growth rule set. */
    private static boolean fastPathSafe(CompiledPattern pattern) {
        if (pattern.details() == null || !pattern.fastSupported()) return false;
        for (CompiledInput input : pattern.inputs()) {
            if (!input.fastSupported()) return false;
        }
        return true;
    }

    /**
     * Growth rule set. Fills the per-firing vectors as a side effect and returns
     * {@link NetGrowthRejection#NONE} exactly when the capability may be granted.
     */
    private static NetGrowthRejection netGrowthRejection(CompiledPattern pattern, Map<AEKey, Long> consumption,
            Map<AEKey, Long> production, Map<AEKey, Long> remainder) {
        if (pattern.details() == null) return NetGrowthRejection.MISSING_DETAILS;
        if (!pattern.netGrowthValidated()) return NetGrowthRejection.UNSTABLE_STATIC_CONTRACT;

        String reason = pattern.unsupportedReason() == null ? "" : pattern.unsupportedReason();
        if (!CONTRACT_SAFE_REASONS.contains(reason)) return NetGrowthRejection.UNSUPPORTED_BY_COMPILE_EVIDENCE;

        if (pattern.outputs().isEmpty()) return NetGrowthRejection.INDETERMINATE_OUTPUT;
        if (pattern.inputs().isEmpty()) return NetGrowthRejection.NO_INPUTS;

        try {
            for (GenericStack output : pattern.outputs()) {
                if (output == null || output.what() == null || output.amount() <= 0) {
                    return NetGrowthRejection.INDETERMINATE_OUTPUT;
                }
                production.merge(output.what(), output.amount(), Math::addExact);
            }
            for (CompiledInput input : pattern.inputs()) {
                // NET_GROWTH_SAFE is independent of fast execution. The smart-bus verdict already excludes
                // substitution and stateful inputs; this layer only materializes its recorded contract.
                if (input == null || input.key() == null || input.amountPerPattern() <= 0) {
                    return NetGrowthRejection.INDETERMINATE_INPUT;
                }
                consumption.merge(input.key(), input.amountPerPattern(), Math::addExact);
                if (input.remainderKey() != null) {
                    if (input.remainderAmountPerPattern() <= 0) {
                        return NetGrowthRejection.INDETERMINATE_INPUT;
                    }
                    remainder.merge(input.remainderKey(), input.remainderAmountPerPattern(), Math::addExact);
                }
            }
            for (AEKey key : remainder.keySet()) {
                Math.addExact(production.getOrDefault(key, 0L), remainder.get(key));
            }
        } catch (ArithmeticException overflow) {
            return NetGrowthRejection.AMOUNT_OVERFLOW;
        }
        return NetGrowthRejection.NONE;
    }
}
