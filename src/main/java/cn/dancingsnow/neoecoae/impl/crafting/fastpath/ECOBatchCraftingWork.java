package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public record ECOBatchCraftingWork(
    long batchSize,
    List<GenericStack> inputTotal,
    List<GenericStack> outputTotal,
    List<GenericStack> remainingTotal,
    @Nullable UUID craftingJobId,
    int occupiedThreadSlots
) {
    /**
     * Source-compatible constructor for callers that still pass the removed progress value.
     */
    public ECOBatchCraftingWork(
        long batchSize,
        List<GenericStack> inputTotal,
        List<GenericStack> outputTotal,
        List<GenericStack> remainingTotal,
        @Nullable UUID craftingJobId,
        int progress,
        int occupiedThreadSlots
    ) {
        this(batchSize, inputTotal, outputTotal, remainingTotal, craftingJobId, occupiedThreadSlots);
    }

    public ECOBatchCraftingWork {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize is outside the supported fast-path range");
        }
        if (occupiedThreadSlots <= 0 || occupiedThreadSlots > batchSize) {
            throw new IllegalArgumentException("occupiedThreadSlots must be positive and not exceed batchSize");
        }
        inputTotal = List.copyOf(inputTotal);
        outputTotal = List.copyOf(outputTotal);
        remainingTotal = List.copyOf(remainingTotal);
        if (!ECOBatchCraftingHelper.areValidPersistedItemStacks(
                inputTotal, ECOBatchCraftingHelper.MAX_BATCH_STACK_AMOUNT, false)
            || !ECOBatchCraftingHelper.areValidPersistedItemStacks(
                outputTotal, ECOBatchCraftingHelper.MAX_BATCH_STACK_AMOUNT, true)
            || !ECOBatchCraftingHelper.areValidPersistedItemStacks(
                remainingTotal, ECOBatchCraftingHelper.MAX_BATCH_STACK_AMOUNT, false)) {
            throw new IllegalArgumentException("Fast-path batch work contains invalid item stacks");
        }
    }
}
