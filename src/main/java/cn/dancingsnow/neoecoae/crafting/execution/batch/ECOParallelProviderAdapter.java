package cn.dancingsnow.neoecoae.crafting.execution.batch;

import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
import appeng.api.stacks.KeyCounter;
import org.jetbrains.annotations.Nullable;

/** Uses the shared material transaction while the ordinary provider validates its recipe. */
public record ECOParallelProviderAdapter(ECOParallelCraftingProvider provider) implements ECOFastPathDispatchProvider {
    @Override
    public @Nullable Preparation eco$prepareFastPath(ECOBatchDispatchContext context) {
        int capacity = provider.eco$getAvailableParallelSlots();
        if (capacity <= 0) return null;
        return new Preparation(capacity, null, false, batch -> {
            KeyCounter[] totals = context.inputCounters();
            for (var counter : totals) {
                for (var entry : counter) counter.set(entry.getKey(),
                    Math.multiplyExact(entry.getLongValue(), batch.craftCount()));
            }
            return provider.eco$pushPatternBatch(context.pattern(), totals,
                batch.craftCount(), context.craftingJobId());
        });
    }
}
