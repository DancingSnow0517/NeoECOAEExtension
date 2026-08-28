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
    @Nullable UUID craftingJobId
) {
    public ECOBatchCraftingRequest {
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(key, "key");
        ECOBatchCraftingHelper.validateBatchSize(batchSize);
        inputsPerCraft = List.copyOf(inputsPerCraft);
        outputsPerCraft = List.copyOf(outputsPerCraft);
        remainingPerCraft = List.copyOf(remainingPerCraft);
        if (!ECOFastPathStacks.areValidItemStacks(
                inputsPerCraft, Integer.MAX_VALUE, false, ECOFastPathStacks.ItemStackValidation.FAST_PATH)
            || !ECOFastPathStacks.areValidItemStacks(
                outputsPerCraft, Integer.MAX_VALUE, true, ECOFastPathStacks.ItemStackValidation.FAST_PATH)
            || !ECOFastPathStacks.areValidItemStacks(
                remainingPerCraft, Integer.MAX_VALUE, false, ECOFastPathStacks.ItemStackValidation.FAST_PATH)) {
            throw new IllegalArgumentException("Fast-path request contains invalid item stacks");
        }
    }
}
