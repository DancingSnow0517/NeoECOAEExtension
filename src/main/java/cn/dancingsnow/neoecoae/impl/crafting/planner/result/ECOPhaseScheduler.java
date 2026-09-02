package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemanticAdapters;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import java.util.function.ToLongFunction;
import java.util.function.Predicate;
import java.util.concurrent.ConcurrentHashMap;

/** Pure phase policy shared by runtime and focused scheduler tests. */
public final class ECOPhaseScheduler {
    private ECOPhaseScheduler() {}
    private static final ConcurrentHashMap<PlanIdentity.PatternIdentity, PatternSemantics> SEMANTIC_CACHE =
        new ConcurrentHashMap<>();

    public static boolean metadataAvailable(boolean requiresOrderedCycleExecution,
            ECOExecutionSchedule schedule, boolean witnessMissing) {
        return !requiresOrderedCycleExecution || (schedule != null && !witnessMissing);
    }

    /** Any ECO execution plan, including a pure DAG, carries supplier-to-consumer phase order. */
    public static boolean hasExecutionPhases(ECOExecutionSchedule schedule) {
        return schedule != null && !schedule.phases().isEmpty();
    }

    /** A compact single-transition cycle has no expanded witness but still needs component phase ordering. */
    public static boolean requiresComponentScheduling(ECOExecutionSchedule schedule) {
        return schedule != null && schedule.phases().stream().anyMatch(phase ->
            phase.type() != ECOExecutionSchedule.Type.DAG && !phase.patternSet().isEmpty());
    }

    public static boolean canDispatch(ECOExecutionSchedule.ComponentExecutionPhase phase, int witnessIndex,
            IPatternDetails pattern) {
        if (phase.patternSet().stream().noneMatch(member -> samePattern(member, pattern))) return false;
        if (phase.type() == ECOExecutionSchedule.Type.DAG) return true;
        // A cycle phase without a per-firing witness carries a compact single-transition plan: there is only
        // one pattern to fire, so no ordering is being suppressed and the pattern set is the whole gate.
        if (phase.cycleWitness().isEmpty()) return true;
        // A physical pattern can also satisfy an acyclic multi-output demand. AE2 aggregates both uses into
        // one task counter, while the witness contains only the firings needed to prove the cycle. Once that
        // proof has been replayed, keeping the witness gate closed would leave the aggregate remainder unable
        // to run and the phase unable to complete.
        if (witnessIndex >= phase.cycleWitness().size()) return true;
        return witnessIndex >= 0 && witnessIndex < phase.cycleWitness().size()
            && samePattern(phase.cycleWitness().get(witnessIndex), pattern);
    }

    /**
     * Returns the target amount of {@code key} that a compact self-growing cycle should retain. Keeping
     * enough feedback for every remaining firing lets the verified batch path consume all feedback currently
     * on hand, producing natural growth waves such as 1 -> 2 -> 4 -> 8. The caller still caps retention to
     * physical output that actually arrived.
     */
    public static long compactCycleFeedbackReserve(ECOExecutionSchedule.ComponentExecutionPhase phase,
            ToLongFunction<IPatternDetails> remainingTasks, AEKey key) {
        PlannerAmount exact = compactCycleFeedbackReserveExact(phase, remainingTasks, key);
        // This method feeds a runtime long-valued reservation API. Keep the exact result above, and only
        // project at this final execution boundary where there is no BigInteger slot to pass through.
        return exact.fitsLong() ? exact.longValueExact() : Long.MAX_VALUE;
    }

    /** Exact planner-side form of {@link #compactCycleFeedbackReserve}; no quantity is saturated here. */
    public static PlannerAmount compactCycleFeedbackReserveExact(
            ECOExecutionSchedule.ComponentExecutionPhase phase,
            ToLongFunction<IPatternDetails> remainingTasks, AEKey key) {
        if (phase == null || key == null || phase.type() != ECOExecutionSchedule.Type.CYCLE
                || !phase.cycleWitness().isEmpty() || phase.patternSet().size() != 1) return PlannerAmount.ZERO;
        IPatternDetails pattern = phase.patternSet().iterator().next();
        long remaining = remainingTasks.applyAsLong(pattern);
        if (remaining <= 0L) return PlannerAmount.ZERO;
        try {
            PatternSemantics semantics = semantic(pattern);
            if (!semantics.supported()) return PlannerAmount.ZERO;
            PlannerAmount consumed = PlannerAmount.ZERO;
            for (var input : semantics.consumedInputs()) {
                if (input.amountPerPattern().signum() <= 0L) return PlannerAmount.ZERO;
                if (key.equals(input.key())) {
                    consumed = consumed.add(input.amountPerPattern());
                }
            }
            if (consumed.signum() <= 0) return PlannerAmount.ZERO;
            PlannerAmount produced = PlannerAmount.ZERO;
            for (var output : semantics.producedOutputs()) {
                if (output != null && key.equals(output.what()) && output.amount() > 0L) {
                    produced = produced.add(output.amount());
                }
            }
            for (var output : semantics.returnedOutputs()) {
                if (output != null && key.equals(output.what()) && output.amount() > 0L) {
                    produced = produced.add(output.amount());
                }
            }
            if (produced.compareTo(consumed) <= 0) return PlannerAmount.ZERO;
            return consumed.multiply(remaining);
        } catch (RuntimeException rejected) {
            return PlannerAmount.ZERO;
        }
    }

    static PatternSemantics semantic(IPatternDetails pattern) {
        var identity = PlanIdentity.patternIdentityFor(pattern);
        if (identity != null) {
            var cached = SEMANTIC_CACHE.get(identity);
            if (cached != null) return cached;
        }
        PatternSemanticAdapter adapter = PatternSemanticAdapters.find(PatternSemanticAdapters.defaults(), pattern);
        PatternSemantics result;
        if (adapter == null) result = new cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.AE2PatternSemanticAdapter()
            .analyze(pattern);
        else {
        try {
            result = adapter.analyze(pattern);
        } catch (RuntimeException rejected) {
            result = PatternSemantics.unsupported(pattern, null,
                "SEMANTIC_ANALYSIS_FAILED:" + rejected.getClass().getSimpleName());
        }
        }
        if (identity != null) SEMANTIC_CACHE.putIfAbsent(identity, result);
        return result;
    }

    /** Pattern detail instances may be reconstructed by AE2; use the shared strict structural identity. */
    public static boolean samePattern(IPatternDetails left, IPatternDetails right) {
        return PlanIdentity.samePattern(left, right);
    }

    public static boolean isComplete(ECOExecutionSchedule.ComponentExecutionPhase phase, int witnessIndex,
            ToLongFunction<IPatternDetails> remainingTasks) {
        if (phase.type() == ECOExecutionSchedule.Type.CYCLE && witnessIndex < phase.cycleWitness().size()) return false;
        for (var pattern : phase.patternSet()) {
            if (remainingTasks.applyAsLong(pattern) > 0) return false;
        }
        return true;
    }

    /**
     * Compatibility overload for callers compiled against the pre-deadlock API.
     * The in-flight predicate is intentionally ignored: transport state is not a
     * phase-completion condition.
     */
    @Deprecated
    public static boolean isComplete(ECOExecutionSchedule.ComponentExecutionPhase phase, int witnessIndex,
            ToLongFunction<IPatternDetails> remainingTasks, Predicate<AEKey> ignoredInFlightPredicate) {
        return isComplete(phase, witnessIndex, remainingTasks);
    }

    public static int witnessAfterDispatch(int witnessIndex, boolean accepted) {
        return accepted ? Math.addExact(witnessIndex, 1) : witnessIndex;
    }

}
