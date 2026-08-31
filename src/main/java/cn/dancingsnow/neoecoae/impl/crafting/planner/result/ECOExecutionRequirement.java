package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity;
import java.util.List;
import java.util.Map;

/** Pure, shared classification of whether a submitted task vector needs solved cycle ordering. */
public enum ECOExecutionRequirement {
    NONE,
    ORDERED,
    BLOCKED;

    public static ECOExecutionRequirement classify(List<ComponentPlanningResult> components,
            Map<IPatternDetails, Long> plannedTasks) {
        boolean ordered = false;
        for (ComponentPlanningResult component : components) {
            if (component.type() != ComponentPlanningResult.Type.CYCLIC) continue;
            boolean plannedMember = component.executionPatterns().stream().anyMatch(pattern ->
                plannedCount(pattern, plannedTasks) > 0);
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
            }
        }
        return ordered ? ORDERED : NONE;
    }

    public static boolean componentIsOrdered(ComponentPlanningResult component) {
        return component != null && component.type() == ComponentPlanningResult.Type.CYCLIC
            && component.cycleDisposition() == CycleExecutionDisposition.ORDERED_EXECUTION;
    }

    private static long plannedCount(IPatternDetails pattern, Map<IPatternDetails, Long> tasks) {
        long count = 0;
        for (var entry : tasks.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0 && PlanIdentity.samePattern(pattern, entry.getKey())) {
                count = Math.addExact(count, entry.getValue());
            }
        }
        return count;
    }
}
