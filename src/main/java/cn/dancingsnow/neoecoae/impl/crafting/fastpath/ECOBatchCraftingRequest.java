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
        long batchSize,
        List<GenericStack> inputsPerCraft,
        List<GenericStack> outputsPerCraft,
        List<GenericStack> remainingPerCraft,
        @Nullable UUID craftingJobId) {
    /** Compatibility constructor used by Thunderbolt Core's optional NeoECO bridge. */
    public ECOBatchCraftingRequest(
            IPatternDetails details,
            ECOFastPathKey key,
            int batchSize,
            List<GenericStack> inputsPerCraft,
            List<GenericStack> outputsPerCraft,
            List<GenericStack> remainingPerCraft,
            @Nullable UUID craftingJobId) {
        this(details, key, (long) batchSize, inputsPerCraft, outputsPerCraft, remainingPerCraft, craftingJobId);
    }

    public ECOBatchCraftingRequest {
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(key, "key");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize is outside the supported fast-path range");
        }
        inputsPerCraft = List.copyOf(inputsPerCraft);
        outputsPerCraft = List.copyOf(outputsPerCraft);
        remainingPerCraft = List.copyOf(remainingPerCraft);
        if (!ECOBatchCraftingHelper.areValidPersistedItemStacks(inputsPerCraft, Integer.MAX_VALUE, false)
                || !ECOBatchCraftingHelper.areValidPersistedItemStacks(outputsPerCraft, Integer.MAX_VALUE, true)
                || !ECOBatchCraftingHelper.areValidPersistedItemStacks(remainingPerCraft, Integer.MAX_VALUE, false)
                || !ECOFastPathStacks.isSafeForFastPath(outputsPerCraft, remainingPerCraft, inputsPerCraft)) {
            throw new IllegalArgumentException("Fast-path request contains invalid item stacks");
        }
    }
}
