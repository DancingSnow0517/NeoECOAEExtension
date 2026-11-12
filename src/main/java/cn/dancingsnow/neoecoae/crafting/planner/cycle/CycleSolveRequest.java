package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.planner.component.ComponentDependency;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal stable boundary for the cycle implementation. Amounts are request state, not graph state. */
public record CycleSolveRequest(
    CycleComponent component,
    Map<AEKey, PlannerAmount> plannerRequiredOutputs,
    Map<AEKey, PlannerAmount> plannerRelevantStock,
    java.util.Set<AEKey> unboundedStock,
    List<ComponentDependency> externalResourceBoundary,
    PlannerOptions options,
    Map<AEKey, PlannerAmount> boundaryImportCaps
) {
    /** Source-compatible constructor for callers that only have AE2-sized request amounts. */
    public CycleSolveRequest(CycleComponent component, Map<AEKey, Long> requiredOutputs,
            Map<AEKey, Long> availableRelevantStock, List<ComponentDependency> externalResourceBoundary,
            PlannerOptions options) {
        this(component, exact(requiredOutputs), exact(availableRelevantStock), java.util.Set.of(),
            externalResourceBoundary, options, Map.of());
    }

    /** Exact planner request constructor; the legacy map is only a representable compatibility projection. */
    public CycleSolveRequest(CycleComponent component, Map<AEKey, Long> requiredOutputs,
            Map<AEKey, PlannerAmount> plannerRequiredOutputs, Map<AEKey, Long> availableRelevantStock,
            List<ComponentDependency> externalResourceBoundary, PlannerOptions options) {
        this(component, plannerRequiredOutputs, exact(availableRelevantStock), java.util.Set.of(),
            externalResourceBoundary, options, Map.of());
    }

    public CycleSolveRequest(CycleComponent component, Map<AEKey, Long> requiredOutputs,
            Map<AEKey, PlannerAmount> plannerRequiredOutputs, Map<AEKey, Long> availableRelevantStock,
            List<ComponentDependency> externalResourceBoundary, PlannerOptions options,
            Map<AEKey, PlannerAmount> boundaryImportCaps) {
        this(component, plannerRequiredOutputs, exact(availableRelevantStock), java.util.Set.of(),
            externalResourceBoundary, options, boundaryImportCaps);
    }

    public CycleSolveRequest {
        if (options == null) options = new PlannerOptions();
        if (boundaryImportCaps == null) boundaryImportCaps = Map.of();
    }

    /** Caps bound the total amount imported over the whole witness; uncapped boundary keys stay unlimited. */
    public CycleSolveRequest withBoundaryImportCaps(Map<AEKey, PlannerAmount> caps) {
        for (PlannerAmount cap : caps.values()) {
            if (cap.signum() < 0) throw new IllegalArgumentException("Negative boundary import cap");
        }
        return new CycleSolveRequest(component, plannerRequiredOutputs, plannerRelevantStock, unboundedStock,
            externalResourceBoundary, options, Map.copyOf(caps));
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
        return requiredOutputAmount(key).longValueSaturated();
    }

    public PlannerAmount requiredOutputAmount(AEKey key) {
        return plannerRequiredOutputs.getOrDefault(key, PlannerAmount.ZERO).max(PlannerAmount.ZERO);
    }

    public long stockOf(AEKey key) {
        return stockAmountOf(key).longValueSaturated();
    }

    public PlannerAmount stockAmountOf(AEKey key) {
        return plannerRelevantStock.getOrDefault(key, PlannerAmount.ZERO).max(PlannerAmount.ZERO);
    }

    public Map<AEKey, Long> requiredOutputs() { return bounded(plannerRequiredOutputs); }
    public Map<AEKey, Long> availableRelevantStock() { return bounded(plannerRelevantStock); }

    private static Map<AEKey, Long> bounded(Map<AEKey, PlannerAmount> amounts) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        amounts.forEach((key, amount) -> result.put(key, amount.longValueSaturated()));
        return result;
    }

    private static Map<AEKey, PlannerAmount> exact(Map<AEKey, Long> amounts) {
        Map<AEKey, PlannerAmount> result = new LinkedHashMap<>();
        amounts.forEach((key, amount) -> result.put(key, PlannerAmount.of(amount == null ? 0L : amount)));
        return result;
    }
}
