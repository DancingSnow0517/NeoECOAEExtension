package cn.dancingsnow.neoecoae.crafting.execution;

import cn.dancingsnow.neoecoae.config.NEConfig;

/**
 * Per-tick ordinary crafting budget for an ECO CPU.
 *
 * <p>The budget is deliberately limited to ordinary one-copy provider pushes. ECO FastPath batches are
 * already bounded by the live provider/worker capacity and therefore do not consume this budget.</p>
 *
 * <p>Each tick receives at most 64 ordinary pushes, independently of co-processors and previous ticks.</p>
 */
public final class ECOCraftingDispatchStrategy {
    /**
     * Returns the ordinary one-copy operations available for the current tick.
     *
     * @param configuredLimit ECO's ordinary-path safety limit
     */
    public int beginTick(int configuredLimit) {
        long cpuLimit = 64L;
        long safeConfiguredLimit = Math.min(
            (long) NEConfig.MAX_ECO_CPU_PUSH_TICK_LIMIT,
            Math.max(0L, configuredLimit)
        );
        long available = Math.min(cpuLimit, safeConfiguredLimit);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, available));
    }

}
