package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Verified recipe credential bound to one unlimited-lane, 64-bit virtual batch. */
public record ECOVerifiedVirtualExecution(
    ECOVerifiedFastPathRecipe recipe,
    long craftCount,
    @Nullable UUID craftingJobId,
    List<GenericStack> inputTotal,
    List<GenericStack> outputTotal,
    List<GenericStack> remainingTotal
) {
    public ECOVerifiedVirtualExecution {
        if (craftCount <= 0L) {
            throw new IllegalArgumentException("virtual craftCount must be positive");
        }
        inputTotal = List.copyOf(inputTotal);
        outputTotal = List.copyOf(outputTotal);
        remainingTotal = List.copyOf(remainingTotal);
    }

    public boolean isCurrent(long reloadGeneration) {
        return recipe.isCurrent(reloadGeneration);
    }
}
