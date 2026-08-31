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
    /** Structural candidates retained for diagnostics and graph presentation. */
    Set<IPatternDetails> patterns,
    /** Physical patterns selected into the final executable plan for this component. */
    Set<IPatternDetails> executionPatterns,
    @Nullable CyclePlanningStatus cycleStatus,
    @Nullable CycleExternalDemandStatus externalDemandStatus,
    Map<AEKey, Long> externalMissingItems,
    @Nullable String diagnostic,
    @Nullable CycleSolveResult cycleResult
) {
    public enum Type { ACYCLIC, CYCLIC }

    public enum Status {
        PLANNED,
        NOT_REQUIRED,
        UNRESOLVED,
        UNSUPPORTED,
        /** The component's exact demand exists but cannot be carried by an AE2 long-valued field. */
        UNREPRESENTABLE,
        /** The cycle solver produced a verified firing order that stage one deliberately does not emit yet. */
        SOLVED_NOT_EMITTED
    }

    public ComponentPlanningResult(int componentId, Type type, Status status, Map<AEKey, Long> requiredOutputs,
            @Nullable CyclePlanningStatus cycleStatus, @Nullable String diagnostic) {
        this(componentId, type, status, requiredOutputs, Set.of(), Set.of(), cycleStatus, null, Map.of(),
            diagnostic, null);
    }

    /** Legacy/test shape where every reported pattern is also considered selected for execution. */
    public ComponentPlanningResult(int componentId, Type type, Status status, Map<AEKey, Long> requiredOutputs,
            Set<IPatternDetails> patterns, @Nullable CyclePlanningStatus cycleStatus,
            @Nullable CycleExternalDemandStatus externalDemandStatus, Map<AEKey, Long> externalMissingItems,
            @Nullable String diagnostic, @Nullable CycleSolveResult cycleResult) {
        this(componentId, type, status, requiredOutputs, patterns, patterns, cycleStatus, externalDemandStatus,
            externalMissingItems, diagnostic, cycleResult);
    }

    public ComponentPlanningResult {
        requiredOutputs = Map.copyOf(requiredOutputs);
        patterns = Set.copyOf(patterns);
        executionPatterns = Set.copyOf(executionPatterns);
        externalMissingItems = Map.copyOf(externalMissingItems);
    }
}
