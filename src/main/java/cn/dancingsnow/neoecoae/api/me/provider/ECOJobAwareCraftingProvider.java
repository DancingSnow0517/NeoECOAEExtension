package cn.dancingsnow.neoecoae.api.me.provider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Ordinary one-copy ownership transfer that retains the calling CPU's task identity. */
public interface ECOJobAwareCraftingProvider extends ICraftingProvider {
    boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs, @Nullable UUID craftingJobId);
}
