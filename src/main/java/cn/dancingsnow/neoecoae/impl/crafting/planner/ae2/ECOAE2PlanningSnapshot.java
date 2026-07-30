package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerNoticeDispatcher;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ECOAE2PlanningSnapshot(
    ECOPlanningProblem<AEKey, ECOAE2PatternVariant> problem,
    AEKey requestedKey,
    long requestedAmount,
    boolean multiplePaths,
    Map<ECOAE2PatternVariant, Integer> inputSlotCounts,
    Set<AEKey> emittableKeys,
    ECOPlannerNoticeDispatcher.Target noticeTarget
) {
    public ECOAE2PlanningSnapshot {
        Objects.requireNonNull(problem, "problem");
        Objects.requireNonNull(requestedKey, "requestedKey");
        if (requestedAmount <= 0) {
            throw new IllegalArgumentException("requestedAmount must be positive");
        }
        inputSlotCounts = Map.copyOf(Objects.requireNonNull(inputSlotCounts, "inputSlotCounts"));
        emittableKeys = Set.copyOf(Objects.requireNonNull(emittableKeys, "emittableKeys"));
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
            emittableKeys,
            noticeTarget
        );
    }
}
