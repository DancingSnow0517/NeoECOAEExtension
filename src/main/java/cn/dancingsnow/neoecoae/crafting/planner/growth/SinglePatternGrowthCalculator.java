package cn.dancingsnow.neoecoae.crafting.planner.growth;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveRequest;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.PatternRun;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphEdge;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import java.util.ArrayList;
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
 * not net-produce. Numeric quantities retain exact values across all three arithmetic tiers;
 * both the firing vector and compact witness remain available beyond the ordinary AE2 range.
 * Other declined shapes are handed to the bounded cycle solver.
 *
 * <p>The number of algebraic operations depends on the keys in the contract, without iterating over the
 * firing count. Only the cost of exact integer arithmetic grows with the number of digits.
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
            return SinglePatternGrowthResult.declined(SinglePatternGrowthStatus.NOT_APPLICABLE,
                SinglePatternGrowthResult.Reason.INTERNAL_ERROR,
                "Exact growth calculation could not be completed: " + overflow.getMessage());
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
        CompiledPattern only = cn.dancingsnow.neoecoae.platform.JdkCompat.first(transitions);
        AEKey member = cn.dancingsnow.neoecoae.platform.JdkCompat.first(component.members());
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
            compute(profile, request.plannerRequiredOutputs(), request.plannerRelevantStock());
        if (result.feedbackKey() != null && !member.equals(result.feedbackKey())) {
            return declined(SinglePatternGrowthResult.Reason.FEEDBACK_KEY_MISMATCH,
                "The feedback key is not the component member");
        }
        return result;
    }

    /**
     * Compatibility entry point for long-valued requests; the calculation and its result stay exact.
     * Wider internal quantities are promoted without discarding the execution witness.
     *
     * @param requiredOutputs absolute on-hand targets per key, exactly as a cycle solve request states them
     * @param availableStock  the relevant stock snapshot the targets are measured against
     */
    public SinglePatternGrowthResult evaluate(ValidatedPatternProfile profile, Map<AEKey, Long> requiredOutputs,
            Map<AEKey, Long> availableStock) {
        try {
            Map<AEKey, PlannerAmount> exact = new LinkedHashMap<>();
            if (requiredOutputs != null) requiredOutputs.forEach((key, amount) ->
                exact.put(key, PlannerAmount.of(amount == null ? 0L : amount)));
            Map<AEKey, PlannerAmount> stock = new LinkedHashMap<>();
            if (availableStock != null) availableStock.forEach((key, amount) -> stock.put(key, PlannerAmount.of(amount)));
            return compute(profile, exact, stock);
        } catch (ArithmeticException overflow) {
            return SinglePatternGrowthResult.declined(SinglePatternGrowthStatus.NOT_APPLICABLE,
                SinglePatternGrowthResult.Reason.INTERNAL_ERROR,
                "Exact growth calculation could not be completed: " + overflow.getMessage());
        }
    }

    private SinglePatternGrowthResult compute(ValidatedPatternProfile profile, Map<AEKey, PlannerAmount> requiredOutputs,
            Map<AEKey, PlannerAmount> availableStock) {
        if (profile == null) {
            return declined(SinglePatternGrowthResult.Reason.MISSING_PROFILE, "No validated profile was supplied");
        }
        if (!profile.netGrowthSafe()) {
            return declined(SinglePatternGrowthResult.Reason.PATTERN_NOT_NET_GROWTH_SAFE,
                "The pattern is not tagged NET_GROWTH_SAFE (" + profile.netGrowthRejection() + ")");
        }
        Map<AEKey, PlannerAmount> required = requiredOutputs == null ? Map.of() : requiredOutputs;
        Map<AEKey, PlannerAmount> stock = availableStock == null ? Map.of() : availableStock;
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
        AEKey feedback = cn.dancingsnow.neoecoae.platform.JdkCompat.first(feedbackCandidates);
        PlannerAmount consume = profile.plannerConsumptionOf(feedback);
        PlannerAmount produce = profile.plannerGrossProductionOf(feedback);
        PlannerAmount growth = produce.subtract(consume);
        if (growth.isZero()) {
            return declined(SinglePatternGrowthResult.Reason.ZERO_GROWTH,
                "consume(K) == produce(K) == " + consume + ", which is not net growth");
        }
        if (growth.signum() < 0) {
            return declined(SinglePatternGrowthResult.Reason.NEGATIVE_GROWTH,
                "produce(K) = " + produce + " is below consume(K) = " + consume);
        }

        PlannerAmount firings = PlannerAmount.ZERO;
        for (Map.Entry<AEKey, PlannerAmount> entry : required.entrySet()) {
            PlannerAmount want = entry.getValue() == null ? PlannerAmount.ZERO : entry.getValue();
            if (want.signum() <= 0) continue;
            PlannerAmount have = stock.getOrDefault(entry.getKey(), PlannerAmount.ZERO);
            PlannerAmount outstanding = want.subtract(have);
            if (outstanding.signum() <= 0) continue;
            PlannerAmount perFiring = profile.plannerNetDeltaPerFiring(entry.getKey());
            if (perFiring.signum() <= 0) {
                return declined(SinglePatternGrowthResult.Reason.REQUIRED_OUTPUT_NOT_PRODUCED,
                    "Required output " + entry.getKey() + " has no positive net production in this pattern");
            }
            firings = firings.max(outstanding.ceilDiv(perFiring));
        }
        if (firings.signum() <= 0) {
            return declined(SinglePatternGrowthResult.Reason.NO_OUTSTANDING_DEMAND,
                "Stock already covers every required output");
        }

        // The loop is self-sustaining from the first firing onwards: starting at C, every firing leaves
        // C + Δ behind. The seed is therefore C, never firings · C.
        PlannerAmount available = stock.getOrDefault(feedback, PlannerAmount.ZERO);
        PlannerAmount shortfall = consume.subtract(available).max(PlannerAmount.ZERO);

        Map<AEKey, PlannerAmount> exactExternalDemand = new LinkedHashMap<>();
        for (AEKey key : profile.touchedKeys()) {
            if (feedback.equals(key)) continue;
            PlannerAmount netPerFiring = profile.plannerConsumptionOf(key).subtract(profile.plannerRemainderOf(key));
            if (netPerFiring.signum() <= 0) continue;
            exactExternalDemand.put(key, netPerFiring.multiply(firings));
        }

        Map<AEKey, PlannerAmount> exactProducedOutputs = new LinkedHashMap<>();
        Map<AEKey, PlannerAmount> exactNetDelta = new LinkedHashMap<>();
        for (AEKey key : profile.touchedKeys()) {
            PlannerAmount gross = profile.plannerGrossProductionOf(key);
            if (gross.signum() > 0) exactProducedOutputs.put(key, gross.multiply(firings));
            PlannerAmount delta = profile.plannerNetDeltaPerFiring(key);
            if (!delta.isZero()) exactNetDelta.put(key, delta.multiply(firings));
        }

        Map<AEKey, PlannerAmount> exactDeliverable = new LinkedHashMap<>();
        for (AEKey key : required.keySet()) {
            PlannerAmount have = stock.getOrDefault(key, PlannerAmount.ZERO);
            exactDeliverable.put(key, have.add(
                profile.plannerNetDeltaPerFiring(key).multiply(firings)));
        }

        SinglePatternGrowthStatus status = shortfall.signum() > 0
            ? SinglePatternGrowthStatus.INSUFFICIENT_SEED
            : SinglePatternGrowthStatus.SUCCESS;
        String diagnostic = consume + " " + feedback + " -> " + produce + " " + feedback + " (delta +" + growth
            + "), " + firings + " firing(s), seed " + consume
            + (shortfall.signum() > 0 ? ", seed shortfall " + shortfall : " covered by stock");
        SinglePatternGrowthResult.Reason reason = SinglePatternGrowthResult.Reason.NONE;
        return new SinglePatternGrowthResult(status, reason, profile, feedback,
            consume.longValueSaturated(), produce.longValueSaturated(), growth.longValueSaturated(), firings.longValueSaturated(),
            Map.of(feedback, consume.longValueSaturated()),
            shortfall.signum() > 0 ? Map.of(feedback, shortfall.longValueSaturated()) : Map.of(),
            boundedMap(exactExternalDemand), boundedMap(exactProducedOutputs),
            boundedMap(exactNetDelta), boundedMap(exactDeliverable),
            List.of(new PatternRun(profile.pattern(), firings)), diagnostic,
            firings, Map.of(feedback, consume),
            shortfall.signum() > 0 ? Map.of(feedback, shortfall) : Map.of(), exactExternalDemand,
            exactProducedOutputs, exactNetDelta, exactDeliverable);
    }

    private static Map<AEKey, Long> boundedMap(Map<AEKey, PlannerAmount> amounts) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        amounts.forEach((key, amount) -> {
            result.put(key, amount.longValueSaturated());
        });
        return result;
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
        return new ArrayList<>(distinct);
    }

    private static boolean sameTransition(CompiledPattern left, CompiledPattern right) {
        return left.details() != null && left.details().equals(right.details());
    }
}
