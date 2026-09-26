package cn.dancingsnow.neoecoae.crafting.execution.worker;

import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingWork;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVirtualCraftingWork;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Immutable input to the thread work-installation state transition. */
record ECOCraftingThreadWork(
    List<ItemStack> itemOutputs,
    List<ItemStack> itemInputs,
    List<ItemStack> itemRemaining,
    List<GenericStack> genericOutputs,
    List<GenericStack> genericInputs,
    List<GenericStack> genericRemaining,
    @Nullable UUID craftingJobId,
    int finiteBatchCraftCount,
    long craftCount,
    boolean virtualBatch,
    boolean resetProgress,
    ItemStack craftingEventOutput
) {
    static ECOCraftingThreadWork items(
        List<ItemStack> outputs,
        List<ItemStack> inputs,
        List<ItemStack> remaining,
        @Nullable UUID craftingJobId,
        int finiteBatchCraftCount
    ) {
        ItemStack eventOutput = outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0).copy();
        return new ECOCraftingThreadWork(
            List.copyOf(outputs),
            List.copyOf(inputs),
            List.copyOf(remaining),
            List.of(),
            List.of(),
            List.of(),
            craftingJobId,
            finiteBatchCraftCount,
            finiteBatchCraftCount,
            false,
            false,
            eventOutput
        );
    }

    static ECOCraftingThreadWork batch(ECOBatchCraftingWork work) {
        return new ECOCraftingThreadWork(
            List.of(),
            List.of(),
            List.of(),
            List.copyOf(work.outputTotal()),
            List.copyOf(work.inputTotal()),
            List.copyOf(work.remainingTotal()),
            work.craftingJobId(),
            work.batchSize(),
            work.batchSize(),
            false,
            false,
            ItemStack.EMPTY
        );
    }

    static ECOCraftingThreadWork virtual(ECOVirtualCraftingWork work) {
        return new ECOCraftingThreadWork(
            List.of(),
            List.of(),
            List.of(),
            List.copyOf(work.outputTotal()),
            List.copyOf(work.inputTotal()),
            List.copyOf(work.remainingTotal()),
            work.craftingJobId(),
            1,
            work.craftCount(),
            true,
            true,
            ItemStack.EMPTY
        );
    }
}
