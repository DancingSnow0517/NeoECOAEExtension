package cn.dancingsnow.neoecoae.crafting.execution.batch;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;

/** Pure batch planner: it only reduces observed limits and never touches inventory, energy, or task state. */
public final class ECOBatchPlanner {
    /** Exact orders are never narrowed to long; provider and material limits stay explicit. */
    public java.math.BigInteger planExact(java.math.BigInteger remaining, java.math.BigInteger inventory,
            java.math.BigInteger providerCapacity, java.math.BigInteger energyCapacity) {
        return planExact(PlannerAmount.of(remaining), PlannerAmount.of(inventory),
                PlannerAmount.of(providerCapacity), PlannerAmount.of(energyCapacity)).toBigInteger();
    }

    public PlannerAmount planExact(PlannerAmount remaining, PlannerAmount inventory,
            PlannerAmount providerCapacity, PlannerAmount energyCapacity) {
        for (var limit : new PlannerAmount[]{remaining, inventory, providerCapacity, energyCapacity}) {
            if (limit.signum() < 0) throw new IllegalArgumentException("Negative exact batch limit");
        }
        return remaining.min(inventory).min(providerCapacity).min(energyCapacity);
    }

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
