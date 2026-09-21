package cn.dancingsnow.neoecoae.crafting.execution.batch;

/** Pure batch planner: it only reduces observed limits and never touches inventory, energy, or task state. */
public final class ECOBatchPlanner {
    public ECOBatchPlan plan(ECOBatchPlanRequest request) {
        ECOBatchLimits limits = request.limits();
        long count = limits.minimum();
        if (request.requestedMode() == ECOBatchMode.SINGLE) count = Math.min(1L, count);
        if (request.requestedMode() == ECOBatchMode.STATEFUL_FAST_PATH) {
            count = Math.min(count, limits.statefulRecipeLimit());
        }
        if (count <= 0L) return null;
        return new ECOBatchPlan(request.identity(), count, request.requestedMode(), limits);
    }
}
