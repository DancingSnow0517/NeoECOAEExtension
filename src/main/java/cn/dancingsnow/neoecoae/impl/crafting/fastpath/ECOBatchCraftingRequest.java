package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public record ECOBatchCraftingRequest(
        IPatternDetails details,
        ECOFastPathKey key,
        int batchSize,
        List<GenericStack> inputsPerCraft,
        List<GenericStack> outputsPerCraft,
        List<GenericStack> remainingPerCraft,
        @Nullable UUID craftingJobId) {
    public ECOBatchCraftingRequest {
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(key, "key");
        if (batchSize <= 0 || batchSize > ECOBatchCraftingHelper.MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("batchSize is outside the supported fast-path range");
        }
        inputsPerCraft = List.copyOf(inputsPerCraft);
        outputsPerCraft = List.copyOf(outputsPerCraft);
        remainingPerCraft = List.copyOf(remainingPerCraft);
        if (!ECOBatchCraftingHelper.areValidItemStacks(inputsPerCraft, Integer.MAX_VALUE, false)
                || !ECOBatchCraftingHelper.areValidItemStacks(outputsPerCraft, Integer.MAX_VALUE, true)
                || !ECOBatchCraftingHelper.areValidItemStacks(remainingPerCraft, Integer.MAX_VALUE, false)) {
            throw new IllegalArgumentException("Fast-path request contains invalid item stacks");
        }
    }
}
