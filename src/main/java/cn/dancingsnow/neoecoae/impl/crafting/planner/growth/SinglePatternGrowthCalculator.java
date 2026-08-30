package cn.dancingsnow.neoecoae.impl.crafting.planner.growth;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.impl.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveRequest;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.PatternRun;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphEdge;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exact integer solver for the one shape the bounded search is worst at: <em>one</em> validated pattern that
 * closes a cycle on itself and grows its own feedback key.
 *
 * <pre>
 *   9 A + Iron -> 10 A + G          A -> 2 A
 * </pre>
 *
 * <h2>What it computes, and what it refuses</h2>
 * With a single transition there is no interleaving and no ordering choice, so the whole answer is
 * arithmetic over the pattern's validated static contract:
 *
 * <pre>
 *   consumption[k], production[k], remainder[k]        (per firing, from the validated profile)
 *   netDelta[k]      = production[k] + remainder[k] - consumption[k]
 *   K                = the single key with consumption[K] &gt; 0 and gross production[K] &gt; 0
 *   C = consumption[K],  P = gross production[K],  Δ = P - C  (must be &gt; 0)
 *   firings          = max over required r of ceilDiv(required[r] - available[r], netDelta[r])
 *   requiredSeed[K]  = C                                (independent of firings)
 *   externalDemand[x]= (consumption[x] - remainder[x]) · firings   for x ≠ K
 * </pre>
 *
 * The feedback key contributes its <em>net</em> growth {@code Δ}, never its gross production {@code P}:
 * {@code C} of the {@code P} units produced are working capital for the next firing, not deliverable output.
 *
 * <p>Everything outside that shape returns {@link SinglePatternGrowthStatus#NOT_APPLICABLE} with no side
 * effect: two or more transitions, more than one member key, more than one feedback key, zero or negative
 * growth, a pattern without {@link PatternCapability#NET_GROWTH_SAFE}, a required output this pattern does
 * not net-produce. Checked arithmetic that leaves the representable range returns
 * {@link SinglePatternGrowthStatus#OVERFLOW}. In every one of those cases the caller must hand the component
 * back to the bounded cycle solver.
 *
 * <p>Cost depends only on the number of patterns (one) and keys in the contract. It is independent of the
 * firing count: planning a billion firings costs exactly what planning one firing costs.
 */
public final class SinglePatternGrowthCalculator {
    private final PatternProfileValidator validator;

    public SinglePatternGrowthCalculator() {
        this(new PatternProfileValidator());
    }

    public SinglePatternGrowthCalculator(PatternProfileValidator validator) {
        this.validator = validator;
    }

    public PatternProfileValidator validator() {
        return validator;
    }

    /**
     * Structural eligibility of one cycle component, then the algebra. Never throws and never mutates
     * anything the caller owns, so a declined answer costs the caller nothing.
     */
    public SinglePatternGrowthResult evaluate(CycleSolveRequest request) {
        try {
            return eligibleThenEvaluate(request);
        } catch (ArithmeticException overflow) {
            return SinglePatternGrowthResult.declined(SinglePatternGrowthStatus.OVERFLOW,
                SinglePatternGrowthResult.Reason.AMOUNT_OVERFLOW,
                "Checked arithmetic left the representable range: " + overflow.getMessage());
        } catch (RuntimeException failure) {
            return SinglePatternGrowthResult.declined(SinglePatternGrowthStatus.NOT_APPLICABLE,
                SinglePatternGrowthResult.Reason.INTERNAL_ERROR,
                "Contained " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    private SinglePatternGrowthResult eligibleThenEvaluate(CycleSolveRequest request) {
        CycleComponent component = request.component();
        List<CompiledPattern> transitions = distinctTransitions(component.patterns());
        if (transitions.isEmpty()) {
            return declined(SinglePatternGrowthResult.Reason.NO_TRANSITION,
                "The cycle component holds no transition");
        }
        if (transitions.size() > 1) {
            return declined(SinglePatternGrowthResult.Reason.MULTIPLE_PATTERNS,
                "The cycle needs " + transitions.size() + " distinct patterns to close; only a single-pattern "
                    + "self-loop is handled here");
        }
        if (component.members().size() != 1) {
            return declined(SinglePatternGrowthResult.Reason.MULTIPLE_MEMBERS,
                "The component holds " + component.members().size() + " member keys");
        }
        CompiledPattern only = transitions.getFirst();
        AEKey member = component.members().getFirst();
        if (component.internalEdges().isEmpty()) {
            return declined(SinglePatternGrowthResult.Reason.NOT_A_SELF_LOOP,
                "The component records no internal edge");
        }
        for (CraftingGraphEdge edge : component.internalEdges()) {
            if (edge.pattern() == null || !sameTransition(edge.pattern(), only)) {
                return declined(SinglePatternGrowthResult.Reason.FOREIGN_INTERNAL_EDGE,
                    "An internal edge belongs to another pattern");
            }
            if (!member.equals(edge.producer()) || !member.equals(edge.requiredInput())) {
                return declined(SinglePatternGrowthResult.Reason.NOT_A_SELF_LOOP,
                    "An internal edge is not a self-loop of the single member key");
            }
        }

        ValidatedPatternProfile profile = validator.validate(only);
        SinglePatternGrowthResult result =
            evaluate(profile, request.requiredOutputs(), request.availableRelevantStock());
        if (result.feedbackKey() != null && !member.equals(result.feedbackKey())) {
            return declined(SinglePatternGrowthResult.Reason.FEEDBACK_KEY_MISMATCH,
                "The feedback key is not the component member");
        }
        return result;
    }

    /**
     * Pure algebraic entry point. Never throws: checked arithmetic that leaves the representable range comes
     * back as {@link SinglePatternGrowthStatus#OVERFLOW}.
     *
     * @param requiredOutputs absolute on-hand targets per key, exactly as a cycle solve request states them
     * @param availableStock  the relevant stock snapshot the targets are measured against
     */
    public SinglePatternGrowthResult evaluate(ValidatedPatternProfile profile, Map<AEKey, Long> requiredOutputs,
            Map<AEKey, Long> availableStock) {
        try {
            return compute(profile, requiredOutputs, availableStock);
        } catch (ArithmeticException overflow) {
            return SinglePatternGrowthResult.declined(SinglePatternGrowthStatus.OVERFLOW,
                SinglePatternGrowthResult.Reason.AMOUNT_OVERFLOW,
                "Checked arithmetic left the representable range: " + overflow.getMessage());
        }
    }

    private SinglePatternGrowthResult compute(ValidatedPatternProfile profile, Map<AEKey, Long> requiredOutputs,
            Map<AEKey, Long> availableStock) {
        if (profile == null) {
            return declined(SinglePatternGrowthResult.Reason.MISSING_PROFILE, "No validated profile was supplied");
        }
        if (!profile.netGrowthSafe()) {
            return declined(SinglePatternGrowthResult.Reason.PATTERN_NOT_NET_GROWTH_SAFE,
                "The pattern is not tagged NET_GROWTH_SAFE (" + profile.netGrowthRejection() + ")");
        }
        Map<AEKey, Long> required = requiredOutputs == null ? Map.of() : requiredOutputs;
        Map<AEKey, Long> stock = availableStock == null ? Map.of() : availableStock;
        if (required.isEmpty()) {
            return declined(SinglePatternGrowthResult.Reason.NO_REQUIRED_OUTPUT, "No required output was named");
        }

        List<AEKey> feedbackCandidates = profile.selfReferencingKeys();
        if (feedbackCandidates.isEmpty()) {
            return declined(SinglePatternGrowthResult.Reason.NO_FEEDBACK_KEY,
                "The pattern consumes and returns no common key, so it closes no loop on its own");
        }
        if (feedbackCandidates.size() > 1) {
            return declined(SinglePatternGrowthResult.Reason.MULTIPLE_FEEDBACK_KEYS,
                "Stage one handles a single feedback key; this pattern feeds back " + feedbackCandidates.size());
        }
        AEKey feedback = feedbackCandidates.getFirst();
        long consume = profile.consumptionOf(feedback);
        long produce = profile.grossProductionOf(feedback);
        long growth = Math.subtractExact(produce, consume);
        if (growth == 0) {
            return declined(SinglePatternGrowthResult.Reason.ZERO_GROWTH,
                "consume(K) == produce(K) == " + consume + ", which is not net growth");
        }
        if (growth < 0) {
            return declined(SinglePatternGrowthResult.Reason.NEGATIVE_GROWTH,
                "produce(K) = " + produce + " is below consume(K) = " + consume);
        }

        long firings = 0;
        for (Map.Entry<AEKey, Long> entry : required.entrySet()) {
            long want = entry.getValue() == null ? 0L : entry.getValue();
            if (want <= 0) continue;
            long have = Math.max(0L, stock.getOrDefault(entry.getKey(), 0L));
            long outstanding = Math.subtractExact(want, have);
            if (outstanding <= 0) continue;
            long perFiring = profile.netDeltaPerFiring(entry.getKey());
            if (perFiring <= 0) {
                return declined(SinglePatternGrowthResult.Reason.REQUIRED_OUTPUT_NOT_PRODUCED,
                    "Required output " + entry.getKey() + " has no positive net production in this pattern");
            }
            firings = Math.max(firings, Math.ceilDiv(outstanding, perFiring));
        }
        if (firings <= 0) {
            return declined(SinglePatternGrowthResult.Reason.NO_OUTSTANDING_DEMAND,
                "Stock already covers every required output");
        }

        // The loop is self-sustaining from the first firing onwards: starting at C, every firing leaves
        // C + Δ behind. The seed is therefore C, never firings · C.
        long available = Math.max(0L, stock.getOrDefault(feedback, 0L));
        long shortfall = Math.max(0L, Math.subtractExact(consume, available));

        Map<AEKey, Long> externalDemand = new LinkedHashMap<>();
        for (AEKey key : profile.consumption().keySet()) {
            if (feedback.equals(key)) continue;
            long netPerFiring = Math.subtractExact(profile.consumptionOf(key), profile.remainderOf(key));
            if (netPerFiring <= 0) continue;
            externalDemand.put(key, Math.multiplyExact(netPerFiring, firings));
        }

        Map<AEKey, Long> producedOutputs = new LinkedHashMap<>();
        Map<AEKey, Long> netDelta = new LinkedHashMap<>();
        for (AEKey key : profile.touchedKeys()) {
            long gross = profile.grossProductionOf(key);
            if (gross > 0) producedOutputs.put(key, Math.multiplyExact(gross, firings));
            long delta = profile.netDeltaPerFiring(key);
            if (delta != 0) netDelta.put(key, Math.multiplyExact(delta, firings));
        }

        Map<AEKey, Long> deliverable = new LinkedHashMap<>();
        for (AEKey key : required.keySet()) {
            long have = Math.max(0L, stock.getOrDefault(key, 0L));
            deliverable.put(key, Math.addExact(have, Math.multiplyExact(profile.netDeltaPerFiring(key), firings)));
        }

        SinglePatternGrowthStatus status = shortfall > 0
            ? SinglePatternGrowthStatus.INSUFFICIENT_SEED
            : SinglePatternGrowthStatus.SUCCESS;
        String diagnostic = consume + " " + feedback + " -> " + produce + " " + feedback + " (delta +" + growth
            + "), " + firings + " firing(s), seed " + consume
            + (shortfall > 0 ? ", seed shortfall " + shortfall : " covered by stock");
        return new SinglePatternGrowthResult(status, SinglePatternGrowthResult.Reason.NONE, profile, feedback,
            consume, produce, growth, firings, Map.of(feedback, consume),
            shortfall > 0 ? Map.of(feedback, shortfall) : Map.of(), externalDemand, producedOutputs, netDelta,
            deliverable, List.of(new PatternRun(profile.pattern(), firings)), diagnostic);
    }

    private static SinglePatternGrowthResult declined(SinglePatternGrowthResult.Reason reason, String message) {
        return SinglePatternGrowthResult.declined(SinglePatternGrowthStatus.NOT_APPLICABLE, reason, message);
    }

    /**
     * Several compiled views can describe the same physical pattern; the transition count is what decides
     * eligibility, so views are deduplicated exactly as the bounded solver deduplicates them.
     */
    private static List<CompiledPattern> distinctTransitions(List<CompiledPattern> patterns) {
        Map<IPatternDetails, CompiledPattern> byDetails = new LinkedHashMap<>();
        for (CompiledPattern pattern : patterns) {
            if (pattern == null || pattern.details() == null) continue;
            byDetails.putIfAbsent(pattern.details(), pattern);
        }
        Set<CompiledPattern> distinct = new LinkedHashSet<>(byDetails.values());
        return List.copyOf(distinct);
    }

    private static boolean sameTransition(CompiledPattern left, CompiledPattern right) {
        return left.details() != null && left.details().equals(right.details());
    }
}
