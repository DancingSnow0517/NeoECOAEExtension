package cn.dancingsnow.neoecoae.crafting.planner.result;

import appeng.api.crafting.IPatternDetails;
import java.util.List;
import java.util.Map;

/** @deprecated ExecutionMode is the plan-level policy; retained for binary and source compatibility. */
@Deprecated
public enum ECOExecutionRequirement {
    NONE, ORDERED, DYNAMIC, BLOCKED;

    public static ECOExecutionRequirement classify(List<ComponentPlanningResult> components,
            Map<IPatternDetails, Long> plannedTasks) {
        return from(ECOPlanningResult.classifyExecutionMode(components, plannedTasks));
    }

    static ECOExecutionRequirement from(ExecutionMode mode) {
        return switch (mode) {
            case NATIVE, PHASED_DAG -> NONE;
            case ORDERED_CYCLE -> ORDERED;
            case DYNAMIC_CYCLE -> DYNAMIC;
            case BLOCKED -> BLOCKED;
        };
    }

    public static boolean componentIsOrdered(ComponentPlanningResult component) {
        return component != null && component.type() == ComponentPlanningResult.Type.CYCLIC
            && component.cycleDisposition() == CycleExecutionDisposition.ORDERED_EXECUTION;
    }

    public static boolean componentIsDynamic(ComponentPlanningResult component) {
        return component != null && component.type() == ComponentPlanningResult.Type.CYCLIC
            && component.cycleDisposition().dynamic();
    }

    public static boolean componentIsExecutableCycle(ComponentPlanningResult component) {
        return component != null && component.type() == ComponentPlanningResult.Type.CYCLIC
            && component.cycleDisposition().executable();
    }
}
