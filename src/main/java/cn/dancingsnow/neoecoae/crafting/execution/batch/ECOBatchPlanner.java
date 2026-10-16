package cn.dancingsnow.neoecoae.crafting.execution.batch;

/** Pure batch planner: it only reduces observed limits and never touches inventory, energy, or task state. */
public final class ECOBatchPlanner {
    /** Exact orders are never narrowed to long; provider and material limits stay explicit. */
    public java.math.BigInteger planExact(java.math.BigInteger remaining, java.math.BigInteger inventory,
            java.math.BigInteger providerCapacity, java.math.BigInteger energyCapacity) {
        return cn.dancingsnow.neoecoae.core.execution.BatchLimitPlanner.exact(
            remaining, inventory, providerCapacity, energyCapacity);
    }

    public ECOBatchPlan plan(ECOBatchPlanRequest request) {
        ECOBatchLimits limits = request.limits();
        long count = cn.dancingsnow.neoecoae.core.execution.BatchLimitPlanner.count(limits.minimum(),
            request.requestedMode() == ECOBatchMode.SINGLE,
            request.requestedMode() == ECOBatchMode.STATEFUL_FAST_PATH, limits.statefulRecipeLimit());
        if (count <= 0L) return null;
        return new ECOBatchPlan(request.identity(), count, request.requestedMode(), limits);
    }
}
