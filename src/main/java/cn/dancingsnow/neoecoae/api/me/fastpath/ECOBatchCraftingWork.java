package cn.dancingsnow.neoecoae.api.me.fastpath;

import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public record ECOBatchCraftingWork(
    int batchSize,
    List<GenericStack> inputTotal,
    List<GenericStack> outputTotal,
    List<GenericStack> remainingTotal,
    @Nullable UUID craftingJobId,
    int progress,
    int occupiedThreadSlots
) {
    public ECOBatchCraftingWork {
        if (batchSize <= 0 || batchSize > ECOBatchCraftingHelper.MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("batchSize is outside the supported fast-path range");
        }
        if (occupiedThreadSlots != batchSize) {
            throw new IllegalArgumentException("Batch work must occupy one thread slot per craft");
        }
        if (progress < 0) {
            throw new IllegalArgumentException("progress must not be negative");
        }
        inputTotal = List.copyOf(inputTotal);
        outputTotal = List.copyOf(outputTotal);
        remainingTotal = List.copyOf(remainingTotal);
        if (!ECOBatchCraftingHelper.areValidItemStacks(
                inputTotal, ECOBatchCraftingHelper.MAX_BATCH_STACK_AMOUNT, false)
            || !ECOBatchCraftingHelper.areValidItemStacks(
                outputTotal, ECOBatchCraftingHelper.MAX_BATCH_STACK_AMOUNT, true)
            || !ECOBatchCraftingHelper.areValidItemStacks(
                remainingTotal, ECOBatchCraftingHelper.MAX_BATCH_STACK_AMOUNT, false)) {
            throw new IllegalArgumentException("Fast-path batch work contains invalid item stacks");
        }
    }
}
