package cn.dancingsnow.neoecoae.crafting.planner.result;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveResult;
import java.util.Map;
import java.util.Set;
import appeng.api.crafting.IPatternDetails;
import org.jetbrains.annotations.Nullable;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;

public record ComponentPlanningResult(
    Map<AEKey, PlannerAmount> plannerRequiredOutputs,
    Map<AEKey, PlannerAmount> plannerStockReservations,
    int componentId,
    Type type,
    Status status,
    /** Structural candidates retained for diagnostics and graph presentation. */
    Set<IPatternDetails> patterns,
    /** Physical patterns selected into the final executable plan for this component. */
    Set<IPatternDetails> executionPatterns,
    @Nullable CyclePlanningStatus cycleStatus,
    @Nullable CycleExternalDemandStatus externalDemandStatus,
    Map<AEKey, Long> externalMissingItems,
    @Nullable String diagnostic,
    @Nullable CycleSolveResult cycleResult,
    CycleExecutionDisposition cycleDisposition
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

    /** Compatibility constructor for ordinary AE2-sized component metadata. */
    public ComponentPlanningResult(int componentId, Type type, Status status, Map<AEKey, Long> requiredOutputs,
            Set<IPatternDetails> patterns, Set<IPatternDetails> executionPatterns,
            @Nullable CyclePlanningStatus cycleStatus, @Nullable CycleExternalDemandStatus externalDemandStatus,
            Map<AEKey, Long> externalMissingItems, @Nullable String diagnostic, @Nullable CycleSolveResult cycleResult,
            CycleExecutionDisposition cycleDisposition, Map<AEKey, Long> stockReservations) {
        this(exact(requiredOutputs), exact(stockReservations), componentId, type, status, patterns, executionPatterns,
            cycleStatus, externalDemandStatus, externalMissingItems, diagnostic, cycleResult, cycleDisposition);
    }

    public Map<AEKey, Long> requiredOutputs() { return bounded(plannerRequiredOutputs); }
    public Map<AEKey, Long> stockReservations() { return bounded(plannerStockReservations); }

    private static Map<AEKey, PlannerAmount> exact(Map<AEKey, Long> values) {
        var result = new java.util.LinkedHashMap<AEKey, PlannerAmount>();
        values.forEach((key, amount) -> result.put(key, PlannerAmount.of(amount)));
        return result;
    }

    private static Map<AEKey, Long> bounded(Map<AEKey, PlannerAmount> values) {
        var result = new java.util.LinkedHashMap<AEKey, Long>();
        values.forEach((key, amount) -> result.put(key, amount.longValueSaturated()));
        return result;
    }

    public ComponentPlanningResult(int componentId, Type type, Status status, Map<AEKey, Long> requiredOutputs,
            @Nullable CyclePlanningStatus cycleStatus, @Nullable String diagnostic) {
        this(componentId, type, status, requiredOutputs, Set.of(), Set.of(), cycleStatus, null, Map.of(),
            diagnostic, null, type == Type.CYCLIC ? CycleExecutionDisposition.BLOCKED
                : CycleExecutionDisposition.NOT_REQUIRED, Map.of());
    }

    /** Legacy/test shape where every reported pattern is also considered selected for execution. */
    public ComponentPlanningResult(int componentId, Type type, Status status, Map<AEKey, Long> requiredOutputs,
            Set<IPatternDetails> patterns, @Nullable CyclePlanningStatus cycleStatus,
            @Nullable CycleExternalDemandStatus externalDemandStatus, Map<AEKey, Long> externalMissingItems,
            @Nullable String diagnostic, @Nullable CycleSolveResult cycleResult) {
        this(componentId, type, status, requiredOutputs, patterns, patterns, cycleStatus, externalDemandStatus,
            externalMissingItems, diagnostic, cycleResult, type == Type.CYCLIC
                ? CycleExecutionDisposition.BLOCKED : CycleExecutionDisposition.NOT_REQUIRED, Map.of());
    }

    public ComponentPlanningResult {




        java.util.Objects.requireNonNull(cycleDisposition, "cycleDisposition");

    }
}
