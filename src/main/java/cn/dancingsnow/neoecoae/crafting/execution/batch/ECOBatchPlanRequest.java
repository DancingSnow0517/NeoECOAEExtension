package cn.dancingsnow.neoecoae.crafting.execution.batch;

import java.util.Objects;

/** Read-only inputs supplied to {@link ECOBatchPlanner}. */
public record ECOBatchPlanRequest(
        ECOPatternIdentity identity,
        long remainingTasks,
        long inventoryCapacity,
        long providerCapacity,
        long energyCapacity,
        long waitingOutputCapacity,
        long priorityQuota,
        long itemFluidLimit,
        long dynamicOutputLimit,
        long statefulRecipeLimit,
        ECOBatchMode requestedMode) {
    public ECOBatchPlanRequest {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(requestedMode, "requestedMode");
    }

    ECOBatchLimits limits() {
        return new ECOBatchLimits(remainingTasks, inventoryCapacity, providerCapacity, energyCapacity,
                waitingOutputCapacity, priorityQuota, itemFluidLimit, dynamicOutputLimit, statefulRecipeLimit);
    }
}
