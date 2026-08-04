package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import java.util.Map;
import java.util.Objects;

public record ECOAE2PlanningSnapshot(
    ECOPlanningProblem<AEKey, ECOAE2PatternVariant> problem,
    AEKey requestedKey,
    long requestedAmount,
    boolean multiplePaths,
    Map<ECOAE2PatternVariant, Integer> inputSlotCounts,
    boolean truncatedStateExpansion,
    boolean excludedDynamicPaths
) {
    public ECOAE2PlanningSnapshot {
        Objects.requireNonNull(problem, "problem");
        Objects.requireNonNull(requestedKey, "requestedKey");
        if (requestedAmount <= 0) {
            throw new IllegalArgumentException("requestedAmount must be positive");
        }
        inputSlotCounts = Map.copyOf(Objects.requireNonNull(inputSlotCounts, "inputSlotCounts"));
    }

    public ECOAE2PlanningSnapshot forAmount(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        return new ECOAE2PlanningSnapshot(
            new ECOPlanningProblem<>(
                problem.operations(),
                problem.inventory(),
                Map.of(requestedKey, amount)
            ),
            requestedKey,
            amount,
            multiplePaths,
            inputSlotCounts,
            truncatedStateExpansion,
            excludedDynamicPaths
        );
    }
}
