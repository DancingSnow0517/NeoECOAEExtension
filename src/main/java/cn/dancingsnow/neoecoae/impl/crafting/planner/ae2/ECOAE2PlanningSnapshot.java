package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerDiagnostic;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public record ECOAE2PlanningSnapshot(
    ECOPlanningProblem<AEKey, ECOAE2PatternVariant> problem,
    AEKey requestedKey,
    long requestedAmount,
    boolean multiplePaths,
    Map<ECOAE2PatternVariant, Integer> inputSlotCounts,
    List<ECOAE2StateCapacityTemplate> stateCapacityTemplates,
    boolean truncatedStateExpansion,
    boolean excludedDynamicPaths,
    boolean dynamicSmithing,
    List<ECOPlannerDiagnostic> diagnostics,
    Set<ResourceLocation> fuzzyItemIds,
    String diagnosticRequestId
) {
    public ECOAE2PlanningSnapshot {
        Objects.requireNonNull(problem, "problem");
        Objects.requireNonNull(requestedKey, "requestedKey");
        if (requestedAmount <= 0) {
            throw new IllegalArgumentException("requestedAmount must be positive");
        }
        inputSlotCounts = Map.copyOf(Objects.requireNonNull(inputSlotCounts, "inputSlotCounts"));
        stateCapacityTemplates = List.copyOf(Objects.requireNonNull(
            stateCapacityTemplates, "stateCapacityTemplates"
        ));
        fuzzyItemIds = Set.copyOf(Objects.requireNonNull(fuzzyItemIds, "fuzzyItemIds"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        diagnosticRequestId = Objects.requireNonNullElse(diagnosticRequestId, "unscoped");
    }

    public ECOAE2PlanningSnapshot(
        ECOPlanningProblem<AEKey, ECOAE2PatternVariant> problem,
        AEKey requestedKey,
        long requestedAmount,
        boolean multiplePaths,
        Map<ECOAE2PatternVariant, Integer> inputSlotCounts,
        boolean truncatedStateExpansion,
        boolean excludedDynamicPaths
    ) {
        this(
            problem, requestedKey, requestedAmount, multiplePaths, inputSlotCounts, List.of(),
            truncatedStateExpansion, excludedDynamicPaths, false, List.of(), Set.of(), "unscoped"
        );
    }

    public ECOAE2PlanningSnapshot forAmount(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        return new ECOAE2PlanningSnapshot(
            new ECOPlanningProblem<>(
                problem.operations(),
                problem.inventory(),
                Map.of(requestedKey, amount),
                problem.unlimitedInventory()
            ),
            requestedKey,
            amount,
            multiplePaths,
            inputSlotCounts,
            stateCapacityTemplates,
            truncatedStateExpansion,
            excludedDynamicPaths,
            dynamicSmithing,
            diagnostics,
            fuzzyItemIds,
            diagnosticRequestId
        );
    }
}
