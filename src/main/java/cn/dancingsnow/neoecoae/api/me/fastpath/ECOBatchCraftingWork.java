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
        int occupiedThreadSlots) {
    public ECOBatchCraftingWork {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (occupiedThreadSlots <= 0) {
            throw new IllegalArgumentException("occupiedThreadSlots must be positive");
        }
        inputTotal = List.copyOf(inputTotal);
        outputTotal = List.copyOf(outputTotal);
        remainingTotal = List.copyOf(remainingTotal);
    }
}
