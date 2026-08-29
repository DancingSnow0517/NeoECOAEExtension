package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/** Pure phase policy shared by runtime and focused scheduler tests. */
public final class ECOPhaseScheduler {
    private ECOPhaseScheduler() {}

    public static boolean metadataAvailable(boolean requiresOrderedCycleExecution,
            ECOExecutionSchedule schedule, boolean witnessMissing) {
        return !requiresOrderedCycleExecution || (schedule != null && !witnessMissing);
    }

    public static boolean canDispatch(ECOExecutionSchedule.ComponentExecutionPhase phase, int witnessIndex,
            IPatternDetails pattern) {
        if (!phase.patternSet().contains(pattern)) return false;
        if (phase.type() == ECOExecutionSchedule.Type.DAG) return true;
        return witnessIndex >= 0 && witnessIndex < phase.cycleWitness().size()
            && phase.cycleWitness().get(witnessIndex) == pattern;
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
