package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Verified recipe credential bound to one unlimited-lane, 64-bit virtual batch. */
public record ECOVerifiedVirtualExecution(
    ECOVerifiedFastPathRecipe recipe,
    long craftCount,
    @Nullable UUID craftingJobId
) {
    public ECOVerifiedVirtualExecution {
        if (craftCount <= 0L) {
            throw new IllegalArgumentException("virtual craftCount must be positive");
        }
    }

    public boolean isCurrent(long reloadGeneration) {
        return recipe.isCurrent(reloadGeneration);
    }
}
