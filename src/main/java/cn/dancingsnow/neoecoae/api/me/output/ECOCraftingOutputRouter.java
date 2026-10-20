package cn.dancingsnow.neoecoae.api.me.output;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import java.util.UUID;

/** Routes outputs from an ECO worker to the CPU that owns the corresponding crafting job. */
public interface ECOCraftingOutputRouter {
    long neoecoae$insertIntoCpuForJob(UUID craftingJobId, AEKey what, long amount, Actionable type);

    /** Older CPU implementations retain their bounded delivery window. */
    default java.math.BigInteger neoecoae$insertExactIntoCpuForJob(UUID jobId, AEKey key,
            java.math.BigInteger amount, Actionable mode) {
        if (amount.signum() <= 0) return java.math.BigInteger.ZERO;
        long offered = amount.min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
        return java.math.BigInteger.valueOf(neoecoae$insertIntoCpuForJob(jobId, key, offered, mode));
    }
}
