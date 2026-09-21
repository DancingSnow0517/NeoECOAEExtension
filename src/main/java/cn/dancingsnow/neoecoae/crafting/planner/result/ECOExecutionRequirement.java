package cn.dancingsnow.neoecoae.crafting.planner.result;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure, shared classification of whether a submitted task vector needs solved cycle ordering. */
public enum ECOExecutionRequirement {
    NONE,
    ORDERED,
    DYNAMIC,
    BLOCKED;

    public static ECOExecutionRequirement classify(List<ComponentPlanningResult> components,
            Map<IPatternDetails, Long> plannedTasks) {
        Map<PlanIdentity.PatternIdentity, Long> positivePlannedCounts = new HashMap<>();
        Set<PlanIdentity.PatternIdentity> overflowingIdentities = new HashSet<>();
        Map<IPatternDetails, Long> fallbackCounts = new IdentityHashMap<>();
        Set<IPatternDetails> overflowingFallbacks = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (var entry : plannedTasks.entrySet()) {
            Long count = entry.getValue();
            if (count == null || count <= 0L) continue;
            PlanIdentity.PatternIdentity identity = safePatternIdentity(entry.getKey());
            if (identity == null) {
                if (overflowingFallbacks.contains(entry.getKey())) continue;
                Long existing = fallbackCounts.get(entry.getKey());
                if (existing == null) {
                    fallbackCounts.put(entry.getKey(), count);
                } else {
                    try {
                        fallbackCounts.put(entry.getKey(), Math.addExact(existing, count));
                    } catch (ArithmeticException overflow) {
                        overflowingFallbacks.add(entry.getKey());
                    }
                }
            } else if (overflowingIdentities.contains(identity)) {
                continue;
            } else {
                Long existing = positivePlannedCounts.get(identity);
                if (existing == null) {
                    positivePlannedCounts.put(identity, count);
                } else {
                    try {
                        positivePlannedCounts.put(identity, Math.addExact(existing, count));
                    } catch (ArithmeticException overflow) {
                        overflowingIdentities.add(identity);
                    }
                }
            }
        }
        boolean ordered = false;
        boolean dynamic = false;
        for (ComponentPlanningResult component : components) {
            if (component.type() != ComponentPlanningResult.Type.CYCLIC) continue;
            boolean plannedMember = component.executionPatterns().stream().anyMatch(pattern ->
                plannedCount(pattern, positivePlannedCounts, overflowingIdentities,
                    fallbackCounts, overflowingFallbacks) > 0);
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

    private static long plannedCount(IPatternDetails pattern,
            Map<PlanIdentity.PatternIdentity, Long> positivePlannedCounts,
            Set<PlanIdentity.PatternIdentity> overflowingIdentities,
            Map<IPatternDetails, Long> fallbackCounts,
            Set<IPatternDetails> overflowingFallbacks) {
        PlanIdentity.PatternIdentity identity = safePatternIdentity(pattern);
        if (identity != null) {
            if (overflowingIdentities.contains(identity)) {
                throw new ArithmeticException("long overflow");
            }
            return positivePlannedCounts.getOrDefault(identity, 0L);
        }
        if (overflowingFallbacks.contains(pattern)) throw new ArithmeticException("long overflow");
        return fallbackCounts.getOrDefault(pattern, 0L);
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
