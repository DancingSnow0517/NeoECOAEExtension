package cn.dancingsnow.neoecoae.impl.crafting.planner.growth;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.PatternRun;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Complete answer of one single-pattern net growth evaluation.
 *
 * <ul>
 *   <li>{@code firings} — how many times the one pattern runs, computed, never searched.</li>
 *   <li>{@code requiredSeed} — the start-up stock of the feedback key, which is {@code consume(K)} and is
 *       independent of {@code firings}.</li>
 *   <li>{@code seedShortfall} — the part of {@code requiredSeed} the stock snapshot cannot cover.</li>
 *   <li>{@code externalDemand} — every other consumed key, linear in {@code firings}, net of the
 *       determinate static remainder. It is handed to the existing external demand planner; nothing is
 *       recursively planned here.</li>
 *   <li>{@code producedOutputs} — gross production over the whole run, byproducts included.</li>
 *   <li>{@code netDelta} — signed net change of every touched key over the whole run.</li>
 *   <li>{@code deliverableOutputs} — what is on hand for the required keys once the run is done.</li>
 *   <li>{@code executionPlan} — compact {@code pattern × count} metadata, never {@code N} witness steps.</li>
 * </ul>
 */
public record SinglePatternGrowthResult(
    SinglePatternGrowthStatus status,
    Reason reason,
    @Nullable ValidatedPatternProfile profile,
    @Nullable AEKey feedbackKey,
    long consumePerFiring,
    long producePerFiring,
    long growthPerFiring,
    long firings,
    Map<AEKey, Long> requiredSeed,
    Map<AEKey, Long> seedShortfall,
    Map<AEKey, Long> externalDemand,
    Map<AEKey, Long> producedOutputs,
    Map<AEKey, Long> netDelta,
    Map<AEKey, Long> deliverableOutputs,
    List<PatternRun> executionPlan,
    String diagnostic,
    PlannerAmount exactFirings,
    Map<AEKey, PlannerAmount> exactRequiredSeed,
    Map<AEKey, PlannerAmount> exactSeedShortfall,
    Map<AEKey, PlannerAmount> exactExternalDemand,
    Map<AEKey, PlannerAmount> exactProducedOutputs,
    Map<AEKey, PlannerAmount> exactNetDelta,
    Map<AEKey, PlannerAmount> exactDeliverableOutputs
) {
    /** Why the calculator produced the status it did. {@link #NONE} accompanies a computed answer. */
    public enum Reason {
        NONE,
        /** No profile was supplied at all. */
        MISSING_PROFILE,
        /** The pattern does not carry {@link PatternCapability#NET_GROWTH_SAFE}. */
        PATTERN_NOT_NET_GROWTH_SAFE,
        /** The cycle component holds no transition. */
        NO_TRANSITION,
        /** Two or more distinct patterns are needed to close the cycle: bounded solver territory. */
        MULTIPLE_PATTERNS,
        /** The strongly connected component holds more than one member key. */
        MULTIPLE_MEMBERS,
        /** An internal edge belongs to another pattern, so the loop is not this pattern's own. */
        FOREIGN_INTERNAL_EDGE,
        /** The component's internal edges are not a self-loop of the single member on the single pattern. */
        NOT_A_SELF_LOOP,
        /** The pattern consumes and returns no common key, so it closes no loop by itself. */
        NO_FEEDBACK_KEY,
        /** More than one key is consumed and returned; stage one deliberately refuses this. */
        MULTIPLE_FEEDBACK_KEYS,
        /** The discovered feedback key is not the component member. */
        FEEDBACK_KEY_MISMATCH,
        /** {@code produce(K) == consume(K)}: not a self-growing pattern in stage one. */
        ZERO_GROWTH,
        /** {@code produce(K) < consume(K)}: the loop shrinks. */
        NEGATIVE_GROWTH,
        /** The request names no required output. */
        NO_REQUIRED_OUTPUT,
        /** Stock already covers every required output, so there is nothing for this calculator to do. */
        NO_OUTSTANDING_DEMAND,
        /** A required output has no positive net production in this pattern. */
        REQUIRED_OUTPUT_NOT_PRODUCED,
        /** A computed execution quantity is exact but cannot be represented by AE2's long boundary. */
        AMOUNT_UNREPRESENTABLE,
        /** Legacy reason retained for source compatibility. */
        AMOUNT_OVERFLOW,
        /** An unexpected runtime failure was contained; the bounded solver answers instead. */
        INTERNAL_ERROR
    }

    public SinglePatternGrowthResult {
        requiredSeed = Map.copyOf(requiredSeed);
        seedShortfall = Map.copyOf(seedShortfall);
        externalDemand = Map.copyOf(externalDemand);
        producedOutputs = Map.copyOf(producedOutputs);
        netDelta = Map.copyOf(netDelta);
        deliverableOutputs = Map.copyOf(deliverableOutputs);
        executionPlan = List.copyOf(executionPlan);
        exactFirings = exactFirings == null ? PlannerAmount.ZERO : exactFirings;
        exactRequiredSeed = Map.copyOf(exactRequiredSeed);
        exactSeedShortfall = Map.copyOf(exactSeedShortfall);
        exactExternalDemand = Map.copyOf(exactExternalDemand);
        exactProducedOutputs = Map.copyOf(exactProducedOutputs);
        exactNetDelta = Map.copyOf(exactNetDelta);
        exactDeliverableOutputs = Map.copyOf(exactDeliverableOutputs);
        if (status == SinglePatternGrowthStatus.SUCCESS && !seedShortfall.isEmpty()) {
            throw new IllegalArgumentException("A successful growth result cannot report a seed shortfall");
        }
    }

    static SinglePatternGrowthResult declined(SinglePatternGrowthStatus status, Reason reason, String diagnostic) {
        return new SinglePatternGrowthResult(status, reason, null, null, 0, 0, 0, 0, Map.of(), Map.of(), Map.of(),
            Map.of(), Map.of(), Map.of(), List.of(), diagnostic, PlannerAmount.ZERO, Map.of(), Map.of(), Map.of(),
            Map.of(), Map.of(), Map.of());
    }

    @Nullable
    public CompiledPattern pattern() {
        return profile == null ? null : profile.pattern();
    }

    @Nullable
    public IPatternDetails details() {
        return profile == null ? null : profile.details();
    }

    public boolean solved() {
        return status.solved();
    }

    public long seedShortfallOf(AEKey key) {
        return seedShortfall.getOrDefault(key, 0L);
    }

    public String summary() {
        return status + (reason == Reason.NONE ? "" : " (" + reason + ")") + ": " + diagnostic;
    }
}
