package cn.dancingsnow.neoecoae.api.me;

import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Explicit opt-in capability for a provider that accepts one atomic dispatch containing multiple crafts, but
 * cannot cheaply expose its current batch capacity.
 *
 * <p>This is deliberately independent from {@link ECOParallelCraftingProvider}: parallel providers still receive
 * one ordinary {@code pushPattern} call per craft, while one successful commit here transfers ownership of all
 * inputs for {@code craftCount} crafts.</p>
 *
 * <p>{@link #eco$simulateBatch} is a strict, side-effect-free query. It must not transfer inputs, mutate queues or
 * inventories, charge energy, produce output, or alter crafting-job accounting. Implementations should be monotone
 * for one provider state snapshot; adapters with discrete supported sizes must perform their own alignment.</p>
 *
 * <p>Immediately before {@link #eco$commitBatch} is called, the CPU has atomically extracted the complete input
 * total represented by {@code execution.inputItems() * craftCount}. Returning {@code true} transfers ownership of
 * those inputs to the provider. Returning {@code false}, or throwing before returning {@code true}, leaves ownership
 * with the CPU and causes a complete rollback.</p>
 */
public interface ECOBatchProbeCraftingProvider {
    boolean eco$simulateBatch(ECOExtractedPatternExecution execution, long craftCount);

    boolean eco$commitBatch(
        ECOExtractedPatternExecution execution,
        long craftCount,
        @Nullable UUID craftingJobId
    );

    /** Stable identity shared by adapters that dispatch into the same underlying machine or queue. */
    default Object eco$getBatchProbeScope() {
        return this;
    }
}
