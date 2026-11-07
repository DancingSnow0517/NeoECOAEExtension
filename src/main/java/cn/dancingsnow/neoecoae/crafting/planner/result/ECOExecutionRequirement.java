package cn.dancingsnow.neoecoae.crafting.planner.result;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Pure, shared classification of whether a submitted task vector needs solved cycle ordering. */
public enum ECOExecutionRequirement {
    NONE,
    ORDERED,
    DYNAMIC,
    BLOCKED;

    public static ECOExecutionRequirement classify(List<ComponentPlanningResult> components,
            Map<IPatternDetails, Long> plannedTasks) {
        Map<PlanIdentity.PatternIdentity, PlannerAmount> positivePlannedCounts = new HashMap<>();
        Map<IPatternDetails, PlannerAmount> fallbackCounts = new IdentityHashMap<>();
        for (var entry : plannedTasks.entrySet()) {
            Long count = entry.getValue();
            if (count == null || count <= 0L) continue;
            PlanIdentity.PatternIdentity identity = safePatternIdentity(entry.getKey());
            if (identity == null) {
                fallbackCounts.merge(entry.getKey(), PlannerAmount.of(count), PlannerAmount::add);
            } else {
                positivePlannedCounts.merge(identity, PlannerAmount.of(count), PlannerAmount::add);
            }
        }
        boolean ordered = false;
        boolean dynamic = false;
        for (ComponentPlanningResult component : components) {
            if (component.type() != ComponentPlanningResult.Type.CYCLIC) continue;
            boolean plannedMember = component.executionPatterns().stream().anyMatch(pattern ->
                plannedCount(pattern, positivePlannedCounts, fallbackCounts).signum() > 0);
            switch (component.cycleDisposition()) {
                case BLOCKED -> { return BLOCKED; }
                case NOT_REQUIRED, STOCK_SATISFIED -> {
                    if (plannedMember) return BLOCKED;
                }
                case ORDERED_EXECUTION -> {
                    if (!plannedMember || component.cycleStatus() != CyclePlanningStatus.SOLVED
                            || component.cycleResult() == null || !component.cycleResult().status().solved()) {
                        return BLOCKED;
                    }
                    ordered = true;
                }
                case DYNAMIC_EXECUTION -> {
                    if (!plannedMember || component.cycleStatus() != CyclePlanningStatus.SOLVED
                            || component.cycleResult() == null || !component.cycleResult().status().solved()
                            || !component.cycleResult().hasExactExecutionCounts()) {
                        return BLOCKED;
                    }
                    dynamic = true;
                }
            }
        }
        return dynamic ? DYNAMIC : ordered ? ORDERED : NONE;
    }

    public static boolean componentIsOrdered(ComponentPlanningResult component) {
        return component != null && component.type() == ComponentPlanningResult.Type.CYCLIC
            && component.cycleDisposition() == CycleExecutionDisposition.ORDERED_EXECUTION;
    }

    public static boolean componentIsDynamic(ComponentPlanningResult component) {
        return component != null && component.type() == ComponentPlanningResult.Type.CYCLIC
            && component.cycleDisposition() == CycleExecutionDisposition.DYNAMIC_EXECUTION;
    }

    public static boolean componentIsExecutableCycle(ComponentPlanningResult component) {
        return componentIsOrdered(component) || componentIsDynamic(component);
    }

    private static PlannerAmount plannedCount(IPatternDetails pattern,
            Map<PlanIdentity.PatternIdentity, PlannerAmount> positivePlannedCounts,
            Map<IPatternDetails, PlannerAmount> fallbackCounts) {
        PlanIdentity.PatternIdentity identity = safePatternIdentity(pattern);
        if (identity != null) {
            return positivePlannedCounts.getOrDefault(identity, PlannerAmount.ZERO);
        }
        return fallbackCounts.getOrDefault(pattern, PlannerAmount.ZERO);
    }

    private static PlanIdentity.PatternIdentity safePatternIdentity(IPatternDetails pattern) {
        if (pattern == null) return null;
        try {
            PlanIdentity.PatternIdentity identity = PlanIdentity.patternIdentityFor(pattern);
            return identity != null && identity.kind() != null && identity.value() != null ? identity : null;
        } catch (RuntimeException rejected) {
            return null;
        }
    }
}
