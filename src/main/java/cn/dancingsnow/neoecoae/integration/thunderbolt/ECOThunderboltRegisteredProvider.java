package cn.dancingsnow.neoecoae.integration.thunderbolt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProviders;
import com.moakiee.thunderbolt.api.crafting.batch.BatchJobView;
import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderAdapters;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;
import java.util.List;

public record ECOThunderboltRegisteredProvider(ICraftingProvider provider) implements IBatchCraftingProvider {
    public static void register() {
        BatchProviderAdapters.register(NeoECOAE.id("registered_fast_path"), -100, (provider, pattern, job) ->
            !(provider instanceof ECOFastPathDispatchProvider) && ECOParallelCraftingProviders.findFastPath(provider) != null
                ? new ECOThunderboltRegisteredProvider(provider) : null);
    }

    @Override public List<IPatternDetails> getAvailablePatterns() { return provider.getAvailablePatterns(); }
    @Override public boolean isBusy() { return provider.isBusy(); }

    @Override public long pushBatch(IPatternDetails pattern, KeyCounter[] oneCopy, long maxCraft) {
        return maxCraft;
    }

    @Override public long pushBatch(IPatternDetails pattern, KeyCounter[] oneCopy, long maxCraft, BatchJobView job) {
        if (maxCraft <= 0) return 0;
        var batch = ECOFastPathFacade.prepareAllocated(provider, pattern, oneCopy, maxCraft, job.level(), job.craftingId());
        if (batch != null && batch.submit(amount -> new ECOFastPathFacade.Reservation() {
            @Override public void commit() {}
            @Override public void refund() {}
        })) return maxCraft - batch.craftCount();

        var inputs = new KeyCounter[oneCopy.length];
        for (int slot = 0; slot < inputs.length; slot++) {
            inputs[slot] = new KeyCounter();
            inputs[slot].addAll(oneCopy[slot]);
        }
        return ECOFastPathFacade.pushPatternForJob(provider, pattern, inputs, job.craftingId()) ? maxCraft - 1 : maxCraft;
    }
}
