package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** A single physical FX lane carrying an entire 64-bit remaining task in virtual mode. */
public record ECOVirtualCraftingWork(
    long craftCount,
    List<GenericStack> inputTotal,
    List<GenericStack> outputTotal,
    List<GenericStack> remainingTotal,
    @Nullable UUID craftingJobId
) {
    public ECOVirtualCraftingWork {
        if (craftCount <= 0L) {
            throw new IllegalArgumentException("virtual craftCount must be positive");
        }
        inputTotal = List.copyOf(inputTotal);
        outputTotal = List.copyOf(outputTotal);
        remainingTotal = List.copyOf(remainingTotal);
        if (!ECOFastPathStacks.areValidItemStacks(inputTotal, Long.MAX_VALUE, false,
                ECOFastPathStacks.ItemStackValidation.FAST_PATH)
            || !ECOFastPathStacks.areValidItemStacks(outputTotal, Long.MAX_VALUE, true,
                ECOFastPathStacks.ItemStackValidation.FAST_PATH)
            || !ECOFastPathStacks.areValidItemStacks(remainingTotal, Long.MAX_VALUE, false,
                ECOFastPathStacks.ItemStackValidation.FAST_PATH)) {
            throw new IllegalArgumentException("Virtual batch contains invalid item totals");
        }
    }
}
