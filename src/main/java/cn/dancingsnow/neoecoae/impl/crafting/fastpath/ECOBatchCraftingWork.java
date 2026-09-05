package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public record ECOBatchCraftingWork(
    int batchSize,
    List<GenericStack> inputTotal,
    List<GenericStack> outputTotal,
    List<GenericStack> remainingTotal,
    @Nullable UUID craftingJobId
) {
    public ECOBatchCraftingWork {
        ECOBatchCraftingHelper.validateBatchSize(batchSize);
        inputTotal = List.copyOf(inputTotal);
        outputTotal = List.copyOf(outputTotal);
        remainingTotal = List.copyOf(remainingTotal);
        // Match the verified mutation model: a batch remainder can legitimately carry non-zero damage.
        if (!ECOFastPathStacks.areValidItemStacks(
                inputTotal,
                ECOBatchCraftingHelper.MAX_BATCH_STACK_AMOUNT,
                false,
                ECOFastPathStacks.ItemStackValidation.FAST_PATH_INPUT)
            || !ECOFastPathStacks.areValidItemStacks(
                outputTotal,
                ECOBatchCraftingHelper.MAX_BATCH_STACK_AMOUNT,
                true,
                ECOFastPathStacks.ItemStackValidation.FAST_PATH)
            || !ECOFastPathStacks.areValidItemStacks(
                remainingTotal,
                ECOBatchCraftingHelper.MAX_BATCH_STACK_AMOUNT,
                false,
                ECOFastPathStacks.ItemStackValidation.FAST_PATH_MUTATION)) {
            throw new IllegalArgumentException("Fast-path batch work contains invalid item stacks");
        }
    }
}
