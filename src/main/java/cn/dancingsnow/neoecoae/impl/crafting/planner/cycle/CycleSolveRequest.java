package cn.dancingsnow.neoecoae.impl.crafting.planner.cycle;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.component.ComponentDependency;
import cn.dancingsnow.neoecoae.impl.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal stable boundary for the cycle implementation. Amounts are request state, not graph state. */
public record CycleSolveRequest(
    CycleComponent component,
    Map<AEKey, Long> requiredOutputs,
    Map<AEKey, PlannerAmount> plannerRequiredOutputs,
    Map<AEKey, Long> availableRelevantStock,
    List<ComponentDependency> externalResourceBoundary,
    PlannerOptions options
) {
    /** Source-compatible constructor for callers that only have AE2-sized request amounts. */
    public CycleSolveRequest(CycleComponent component, Map<AEKey, Long> requiredOutputs,
            Map<AEKey, Long> availableRelevantStock, List<ComponentDependency> externalResourceBoundary,
            PlannerOptions options) {
        this(component, requiredOutputs, exact(requiredOutputs), availableRelevantStock, externalResourceBoundary,
            options);
    }

    /** Exact planner request constructor; the legacy map is only a representable compatibility projection. */
    public CycleSolveRequest(CycleComponent component, Map<AEKey, Long> requiredOutputs,
            Map<AEKey, PlannerAmount> plannerRequiredOutputs, Map<AEKey, Long> availableRelevantStock,
            List<ComponentDependency> externalResourceBoundary, PlannerOptions options) {
        this.component = component;
        this.requiredOutputs = Map.copyOf(requiredOutputs);
        this.plannerRequiredOutputs = Map.copyOf(plannerRequiredOutputs);
        this.availableRelevantStock = Map.copyOf(availableRelevantStock);
        this.externalResourceBoundary = List.copyOf(externalResourceBoundary);
        this.options = options == null ? new PlannerOptions() : options;
    }

    public record PlannerOptions(CycleSolveLimits limits) {
        public PlannerOptions() {
            this(CycleSolveLimits.DEFAULT);
        }

        /** Source-compatible bridge for callers compiled against the former enable flag. */
        public PlannerOptions(boolean ignoredCyclePlanningEnabled, CycleSolveLimits limits) {
            this(limits);
        }

        public PlannerOptions {
            if (limits == null) limits = CycleSolveLimits.DEFAULT;
        }
    }

    public long requiredOutput(AEKey key) {
        return Math.max(0L, requiredOutputs.getOrDefault(key, 0L));
    }

    public PlannerAmount requiredOutputAmount(AEKey key) {
        return plannerRequiredOutputs.getOrDefault(key, PlannerAmount.ZERO).max(PlannerAmount.ZERO);
    }

    public long stockOf(AEKey key) {
        return Math.max(0L, availableRelevantStock.getOrDefault(key, 0L));
    }

    private static Map<AEKey, PlannerAmount> exact(Map<AEKey, Long> amounts) {
        Map<AEKey, PlannerAmount> result = new LinkedHashMap<>();
        amounts.forEach((key, amount) -> result.put(key, PlannerAmount.of(amount == null ? 0L : amount)));
        return result;
    }
}
