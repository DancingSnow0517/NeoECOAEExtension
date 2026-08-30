package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/** Pure phase policy shared by runtime and focused scheduler tests. */
public final class ECOPhaseScheduler {
    private ECOPhaseScheduler() {}

    public static boolean metadataAvailable(boolean requiresOrderedCycleExecution,
            ECOExecutionSchedule schedule, boolean witnessMissing) {
        return !requiresOrderedCycleExecution || (schedule != null && !witnessMissing);
    }

    /** A compact single-transition cycle has no expanded witness but still needs component phase ordering. */
    public static boolean requiresComponentScheduling(ECOExecutionSchedule schedule) {
        return schedule != null && schedule.phases().stream().anyMatch(phase ->
            phase.type() == ECOExecutionSchedule.Type.CYCLE && !phase.patternSet().isEmpty());
    }

    public static boolean canDispatch(ECOExecutionSchedule.ComponentExecutionPhase phase, int witnessIndex,
            IPatternDetails pattern) {
        if (phase.patternSet().stream().noneMatch(member -> samePattern(member, pattern))) return false;
        if (phase.type() == ECOExecutionSchedule.Type.DAG) return true;
        // A cycle phase without a per-firing witness carries a compact single-transition plan: there is only
        // one pattern to fire, so no ordering is being suppressed and the pattern set is the whole gate.
        if (phase.cycleWitness().isEmpty()) return true;
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
            PlannerAmount consumed = PlannerAmount.ZERO;
            for (var input : pattern.getInputs()) {
                if (input == null || input.getMultiplier() <= 0L) return PlannerAmount.ZERO;
                var possible = input.getPossibleInputs();
                if (possible == null || possible.length != 1 || possible[0] == null
                        || possible[0].what() == null || possible[0].amount() <= 0L) return PlannerAmount.ZERO;
                if (key.equals(possible[0].what())) {
                    consumed = consumed.add(PlannerAmount.of(possible[0].amount()).multiply(input.getMultiplier()));
                }
            }
            if (consumed.signum() <= 0) return PlannerAmount.ZERO;
            PlannerAmount produced = PlannerAmount.ZERO;
            for (var output : pattern.getOutputs()) {
                if (output != null && key.equals(output.what()) && output.amount() > 0L) {
                    produced = produced.add(output.amount());
                }
            }
            for (var input : pattern.getInputs()) {
                var possible = input.getPossibleInputs();
                if (key.equals(input.getRemainingKey(possible[0].what()))) {
                    produced = produced.add(input.getMultiplier());
                }
            }
            if (produced.compareTo(consumed) <= 0) return PlannerAmount.ZERO;
            return consumed.multiply(remaining);
        } catch (RuntimeException rejected) {
            return PlannerAmount.ZERO;
        }
    }

    /** Pattern detail instances may be reconstructed by AE2; the encoded pattern item is the stable identity. */
    public static boolean samePattern(IPatternDetails left, IPatternDetails right) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        var leftDefinition = left.getDefinition();
        var rightDefinition = right.getDefinition();
        return leftDefinition != null && leftDefinition.equals(rightDefinition);
    }

    public static boolean isComplete(ECOExecutionSchedule.ComponentExecutionPhase phase, int witnessIndex,
            ToLongFunction<IPatternDetails> remainingTasks, Predicate<AEKey> hasInFlightOutput) {
        if (phase.type() == ECOExecutionSchedule.Type.CYCLE && witnessIndex < phase.cycleWitness().size()) return false;
        for (var pattern : phase.patternSet()) {
            if (remainingTasks.applyAsLong(pattern) > 0) return false;
            for (var output : pattern.getOutputs()) if (hasInFlightOutput.test(output.what())) return false;
        }
        return true;
    }

    public static int witnessAfterDispatch(int witnessIndex, boolean accepted) {
        return accepted ? Math.addExact(witnessIndex, 1) : witnessIndex;
    }
}
