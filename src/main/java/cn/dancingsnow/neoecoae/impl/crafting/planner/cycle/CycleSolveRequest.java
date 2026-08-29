package cn.dancingsnow.neoecoae.impl.crafting.planner.cycle;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.component.ComponentDependency;
import cn.dancingsnow.neoecoae.impl.crafting.planner.component.CycleComponent;
import java.util.List;
import java.util.Map;

/** Minimal stable boundary for the cycle implementation. Amounts are request state, not graph state. */
public record CycleSolveRequest(
    CycleComponent component,
    Map<AEKey, Long> requiredOutputs,
    Map<AEKey, Long> availableRelevantStock,
    List<ComponentDependency> externalResourceBoundary,
    PlannerOptions options
) {
    public record PlannerOptions(boolean cyclePlanningEnabled, CycleSolveLimits limits) {
        public PlannerOptions(boolean cyclePlanningEnabled) {
            this(cyclePlanningEnabled, CycleSolveLimits.DEFAULT);
        }

        public PlannerOptions {
            if (limits == null) limits = CycleSolveLimits.DEFAULT;
        }
    }

    public CycleSolveRequest {
        requiredOutputs = Map.copyOf(requiredOutputs);
        availableRelevantStock = Map.copyOf(availableRelevantStock);
        externalResourceBoundary = List.copyOf(externalResourceBoundary);
    }

    public long requiredOutput(AEKey key) {
        return Math.max(0L, requiredOutputs.getOrDefault(key, 0L));
    }

    public long stockOf(AEKey key) {
        return Math.max(0L, availableRelevantStock.getOrDefault(key, 0L));
    }
}
