package cn.dancingsnow.neoecoae.crafting.execution.batch;

import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
import org.jetbrains.annotations.Nullable;

/** Uses the shared material transaction while the ordinary provider validates its recipe. */
@Deprecated
public record ECOParallelProviderAdapter(ECOParallelCraftingProvider provider) implements ECOFastPathDispatchProvider {
    @Override
    public @Nullable Preparation eco$prepareFastPath(ECOBatchDispatchContext context) {
        return cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade.adaptParallel(provider)
            .eco$prepareFastPath(context);
    }
}
