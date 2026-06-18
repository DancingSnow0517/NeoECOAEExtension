package cn.dancingsnow.neoecoae.api.me.fastpath;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import java.util.List;
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
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        inputsPerCraft = List.copyOf(inputsPerCraft);
        outputsPerCraft = List.copyOf(outputsPerCraft);
        remainingPerCraft = List.copyOf(remainingPerCraft);
    }
}
