package cn.dancingsnow.neoecoae.crafting.execution.fastpath;

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

        // Match the verified mutation model: a batch remainder can legitimately carry non-zero damage.
        if (!CraftWorkValidation.valid(inputTotal, outputTotal, remainingTotal, ECOBatchCraftingHelper.MAX_BATCH_STACK_AMOUNT)) {
            throw new IllegalArgumentException("Fast-path batch work contains invalid item stacks");
        }
    }
}
