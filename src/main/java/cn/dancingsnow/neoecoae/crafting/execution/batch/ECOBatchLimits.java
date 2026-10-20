package cn.dancingsnow.neoecoae.crafting.execution.batch;

/**
 * The independent limits observed while planning one batch.
 * {@link Long#MAX_VALUE} means that a particular limit is currently unbounded.
 */
public record ECOBatchLimits(
        long remainingTasks,
        long inventory,
        long providerCapacity,
        long energyCapacity,
        long waitingOutputCapacity,
        long priorityQuota,
        long itemFluidLimit,
        long dynamicOutputLimit,
        long statefulRecipeLimit) {
    public ECOBatchLimits {
        validate("remainingTasks", remainingTasks);
        validate("inventory", inventory);
        validate("providerCapacity", providerCapacity);
        validate("energyCapacity", energyCapacity);
        validate("waitingOutputCapacity", waitingOutputCapacity);
        validate("priorityQuota", priorityQuota);
        validate("itemFluidLimit", itemFluidLimit);
        validate("dynamicOutputLimit", dynamicOutputLimit);
        validate("statefulRecipeLimit", statefulRecipeLimit);
    }

    public long minimum() {
        return Math.min(remainingTasks, Math.min(inventory,
                Math.min(providerCapacity, Math.min(energyCapacity,
                        Math.min(waitingOutputCapacity, Math.min(priorityQuota,
                                Math.min(itemFluidLimit, Math.min(dynamicOutputLimit, statefulRecipeLimit))))))));
    }

    private static void validate(String name, long value) {
        if (value < 0L) throw new IllegalArgumentException(name + " must not be negative");
    }
}
