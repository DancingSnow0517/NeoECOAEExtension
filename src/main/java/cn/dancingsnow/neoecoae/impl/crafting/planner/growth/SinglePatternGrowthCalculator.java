package cn.dancingsnow.neoecoae.impl.crafting.planner.growth;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.impl.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveRequest;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.PatternRun;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphEdge;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
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
 * {@link SinglePatternGrowthStatus#UNREPRESENTABLE}. In that case the caller reports the exact boundary value;
 * other declined cases are handed to the bounded cycle solver.
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
     * back as {@link SinglePatternGrowthStatus#UNREPRESENTABLE} when its exact execution result cannot fit.
     *
     * @param requiredOutputs absolute on-hand targets per key, exactly as a cycle solve request states them
     * @param availableStock  the relevant stock snapshot the targets are measured against
     */
    public SinglePatternGrowthResult evaluate(ValidatedPatternProfile profile, Map<AEKey, Long> requiredOutputs,
            Map<AEKey, Long> availableStock) {
        try {
            return compute(profile, requiredOutputs, availableStock);
        } catch (ArithmeticException overflow) {
            return SinglePatternGrowthResult.declined(SinglePatternGrowthStatus.NOT_APPLICABLE,
                SinglePatternGrowthResult.Reason.INTERNAL_ERROR,
                "Exact growth calculation could not be completed: " + overflow.getMessage());
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
        PlannerAmount consume = PlannerAmount.of(profile.consumptionOf(feedback));
        PlannerAmount produce = PlannerAmount.of(profile.grossProductionOf(feedback));
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
        for (Map.Entry<AEKey, Long> entry : required.entrySet()) {
            long want = entry.getValue() == null ? 0L : entry.getValue();
            if (want <= 0) continue;
            PlannerAmount have = PlannerAmount.of(Math.max(0L, stock.getOrDefault(entry.getKey(), 0L)));
            PlannerAmount outstanding = PlannerAmount.of(want).subtract(have);
            if (outstanding.signum() <= 0) continue;
            PlannerAmount perFiring = PlannerAmount.of(profile.netDeltaPerFiring(entry.getKey()));
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
        PlannerAmount available = PlannerAmount.of(Math.max(0L, stock.getOrDefault(feedback, 0L)));
        PlannerAmount shortfall = consume.subtract(available).max(PlannerAmount.ZERO);

        Map<AEKey, PlannerAmount> exactExternalDemand = new LinkedHashMap<>();
        for (AEKey key : profile.consumption().keySet()) {
            if (feedback.equals(key)) continue;
            PlannerAmount netPerFiring = PlannerAmount.of(profile.consumptionOf(key))
                .subtract(profile.remainderOf(key));
            if (netPerFiring.signum() <= 0) continue;
            exactExternalDemand.put(key, netPerFiring.multiply(firings));
        }

        Map<AEKey, PlannerAmount> exactProducedOutputs = new LinkedHashMap<>();
        Map<AEKey, PlannerAmount> exactNetDelta = new LinkedHashMap<>();
        for (AEKey key : profile.touchedKeys()) {
            PlannerAmount gross = PlannerAmount.of(profile.grossProductionOf(key));
            if (gross.signum() > 0) exactProducedOutputs.put(key, gross.multiply(firings));
            PlannerAmount delta = PlannerAmount.of(profile.netDeltaPerFiring(key));
            if (!delta.isZero()) exactNetDelta.put(key, delta.multiply(firings));
        }

        Map<AEKey, PlannerAmount> exactDeliverable = new LinkedHashMap<>();
        for (AEKey key : required.keySet()) {
            PlannerAmount have = PlannerAmount.of(Math.max(0L, stock.getOrDefault(key, 0L)));
            exactDeliverable.put(key, have.add(
                PlannerAmount.of(profile.netDeltaPerFiring(key)).multiply(firings)));
        }

        if (!firings.fitsLong() || !shortfall.fitsLong() || !allFit(exactExternalDemand)
                || !allFit(exactProducedOutputs) || !allFit(exactNetDelta) || !allFit(exactDeliverable)) {
            return SinglePatternGrowthResult.declined(SinglePatternGrowthStatus.UNREPRESENTABLE,
                SinglePatternGrowthResult.Reason.AMOUNT_UNREPRESENTABLE,
                "Execution amount exceeds AE2 long range: firings=" + firings
                    + ", seedShortfall=" + shortfall + ", externalDemand=" + exactExternalDemand
                    + ", producedOutputs=" + exactProducedOutputs + ", deliverable=" + exactDeliverable
                    + ", max=" + Long.MAX_VALUE);
        }

        long firingsLong = firings.longValueExact();
        SinglePatternGrowthStatus status = shortfall.signum() > 0
            ? SinglePatternGrowthStatus.INSUFFICIENT_SEED
            : SinglePatternGrowthStatus.SUCCESS;
        String diagnostic = consume + " " + feedback + " -> " + produce + " " + feedback + " (delta +" + growth
            + "), " + firingsLong + " firing(s), seed " + consume
            + (shortfall.signum() > 0 ? ", seed shortfall " + shortfall : " covered by stock");
        return new SinglePatternGrowthResult(status, SinglePatternGrowthResult.Reason.NONE, profile, feedback,
            consume.longValueExact(), produce.longValueExact(), growth.longValueExact(), firingsLong,
            Map.of(feedback, consume.longValueExact()),
            shortfall.signum() > 0 ? Map.of(feedback, shortfall.longValueExact()) : Map.of(),
            toLongMap(exactExternalDemand), toLongMap(exactProducedOutputs), toLongMap(exactNetDelta),
            toLongMap(exactDeliverable), List.of(new PatternRun(profile.pattern(), firingsLong)), diagnostic);
    }

    private static boolean allFit(Map<AEKey, PlannerAmount> amounts) {
        return amounts.values().stream().allMatch(PlannerAmount::fitsLong);
    }

    private static Map<AEKey, Long> toLongMap(Map<AEKey, PlannerAmount> amounts) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        amounts.forEach((key, amount) -> result.put(key, amount.longValueExact()));
        return Map.copyOf(result);
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
