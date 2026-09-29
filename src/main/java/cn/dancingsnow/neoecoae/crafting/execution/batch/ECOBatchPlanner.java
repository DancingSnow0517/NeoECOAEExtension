package cn.dancingsnow.neoecoae.crafting.execution.batch;

import java.math.BigInteger;

/** Pure quantity planning. Resource acquisition always happens after these observed limits. */
public final class ECOBatchPlanner {
    private ECOBatchPlanner() {}

    public static long plan(long remaining, long inventory, long provider, long energy, long material) {
        return Math.max(0L, Math.min(remaining, Math.min(inventory, Math.min(provider, Math.min(energy, material)))));
    }

    public static BigInteger planExact(
            BigInteger remaining, BigInteger inventory, BigInteger provider, BigInteger energy) {
        for (BigInteger limit : new BigInteger[] {remaining, inventory, provider, energy}) {
            if (limit.signum() < 0) throw new IllegalArgumentException("Negative exact batch limit");
        }
        return remaining.min(inventory).min(provider).min(energy);
    }
}
