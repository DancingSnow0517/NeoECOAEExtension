package cn.dancingsnow.neoecoae.core.execution;

import java.math.BigInteger;

/** Pure limit reduction; inventory, provider ownership and energy transactions belong to adapters. */
public final class BatchLimitPlanner {
    private BatchLimitPlanner() {}

    public static BigInteger exact(BigInteger remaining, BigInteger inventory,
            BigInteger providerCapacity, BigInteger energyCapacity) {
        if (remaining.signum() < 0 || inventory.signum() < 0
                || providerCapacity.signum() < 0 || energyCapacity.signum() < 0) {
            throw new IllegalArgumentException("Negative exact batch limit");
        }
        return remaining.min(inventory).min(providerCapacity).min(energyCapacity);
    }

    public static long count(long observedMinimum, boolean single, boolean stateful, long statefulLimit) {
        long count = single ? Math.min(1L, observedMinimum) : observedMinimum;
        return stateful ? Math.min(count, statefulLimit) : count;
    }
}
