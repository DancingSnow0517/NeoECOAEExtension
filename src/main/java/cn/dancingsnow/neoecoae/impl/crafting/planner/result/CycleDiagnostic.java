package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CycleDiagnostic(
    List<AEKey> keys,
    List<IPatternDetails> patterns,
    Map<AEKey, Long> netOutputs,
    Map<AEKey, Long> totalNetOutputs,
    Map<AEKey, Long> availableAmounts,
    Map<AEKey, ExactCycleAmount> exactNetOutputs,
    Map<AEKey, ExactCycleAmount> exactTotalNetOutputs,
    ExecutionCountKnowledge executionCountKnowledge,
    CycleSolveStatus solveStatus
) {
    /** Legacy shape: without execution counts the total output is unknown, rather than equal to one cycle. */
    public CycleDiagnostic(List<AEKey> keys, List<IPatternDetails> patterns,
            Map<AEKey, Long> netOutputs, Map<AEKey, Long> availableAmounts) {
        this(keys, patterns, netOutputs, Map.of(), availableAmounts, exact(netOutputs), Map.of(),
            ExecutionCountKnowledge.UNKNOWN, CycleSolveStatus.NOT_IMPLEMENTED);
    }

    public CycleDiagnostic(List<AEKey> keys, List<IPatternDetails> patterns,
            Map<AEKey, PlannerAmount> exactNetOutputs, Map<AEKey, PlannerAmount> exactTotalNetOutputs,
            Map<AEKey, Long> availableAmounts, ExecutionCountKnowledge knowledge, CycleSolveStatus solveStatus) {
        this(keys, patterns, representable(exactNetOutputs), representable(exactTotalNetOutputs), availableAmounts,
            exactAmounts(exactNetOutputs), exactAmounts(exactTotalNetOutputs), knowledge, solveStatus);
    }

    public CycleDiagnostic {
        keys = List.copyOf(keys);
        patterns = List.copyOf(patterns);
        netOutputs = Map.copyOf(netOutputs);
        totalNetOutputs = Map.copyOf(totalNetOutputs);
        availableAmounts = Map.copyOf(availableAmounts);
        exactNetOutputs = Map.copyOf(exactNetOutputs);
        exactTotalNetOutputs = Map.copyOf(exactTotalNetOutputs);
        executionCountKnowledge = executionCountKnowledge == null ? ExecutionCountKnowledge.UNKNOWN
            : executionCountKnowledge;
        solveStatus = solveStatus == null ? CycleSolveStatus.NOT_IMPLEMENTED : solveStatus;
    }

    public CycleDiagnostic withAvailableAmounts(KeyCounter inventory) {
        Map<AEKey, Long> available = new LinkedHashMap<>();
        for (AEKey key : keys) {
            available.putIfAbsent(key, Math.max(0L, inventory.get(key)));
        }
        return new CycleDiagnostic(keys, patterns, netOutputs, totalNetOutputs, available, exactNetOutputs,
            exactTotalNetOutputs, executionCountKnowledge, solveStatus);
    }

    private static Map<AEKey, ExactCycleAmount> exact(Map<AEKey, Long> values) {
        Map<AEKey, ExactCycleAmount> result = new LinkedHashMap<>();
        values.forEach((key, value) -> result.put(key, ExactCycleAmount.of(value)));
        return Map.copyOf(result);
    }

    private static Map<AEKey, ExactCycleAmount> exactAmounts(Map<AEKey, PlannerAmount> values) {
        Map<AEKey, ExactCycleAmount> result = new LinkedHashMap<>();
        values.forEach((key, value) -> result.put(key, ExactCycleAmount.of(value)));
        return Map.copyOf(result);
    }

    private static Map<AEKey, Long> representable(Map<AEKey, PlannerAmount> values) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (value.fitsLong()) result.put(key, value.longValueExact());
        });
        return Map.copyOf(result);
    }
}
