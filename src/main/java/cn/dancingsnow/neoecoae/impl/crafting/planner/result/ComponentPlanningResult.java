package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveResult;
import java.util.Map;
import java.util.Set;
import appeng.api.crafting.IPatternDetails;
import org.jetbrains.annotations.Nullable;

public record ComponentPlanningResult(
    int componentId,
    Type type,
    Status status,
    Map<AEKey, Long> requiredOutputs,
    Set<IPatternDetails> patterns,
    @Nullable CyclePlanningStatus cycleStatus,
    @Nullable String diagnostic,
    @Nullable CycleSolveResult cycleResult
) {
    public enum Type { ACYCLIC, CYCLIC }

    public enum Status {
        PLANNED,
        NOT_REQUIRED,
        UNRESOLVED,
        UNSUPPORTED,
        /** The cycle solver produced a verified firing order that stage one deliberately does not emit yet. */
        SOLVED_NOT_EMITTED
    }

    public ComponentPlanningResult(int componentId, Type type, Status status, Map<AEKey, Long> requiredOutputs,
            @Nullable CyclePlanningStatus cycleStatus, @Nullable String diagnostic) {
        this(componentId, type, status, requiredOutputs, Set.of(), cycleStatus, diagnostic, null);
    }

    public ComponentPlanningResult {
        requiredOutputs = Map.copyOf(requiredOutputs);
        patterns = Set.copyOf(patterns);
    }
}
