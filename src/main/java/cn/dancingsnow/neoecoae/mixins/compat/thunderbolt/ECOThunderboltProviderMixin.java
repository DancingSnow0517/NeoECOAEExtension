package cn.dancingsnow.neoecoae.mixins.compat.thunderbolt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import com.moakiee.thunderbolt.ae2.api.crafting.BatchDispatchContext;
import com.moakiee.thunderbolt.ae2.api.crafting.IBatchCraftingProvider;
import org.spongepowered.asm.mixin.Mixin;

/** AE2LT (including its time-wheel CPU) retains allocation, energy and job accounting. */
@Mixin(value = ECOCraftingPatternBusBlockEntity.class, remap = false)
public abstract class ECOThunderboltProviderMixin implements IBatchCraftingProvider {
    @Override
    public long getBatchCapacity(IPatternDetails pattern) {
        return !isBusy() && pattern instanceof appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern
            && cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECORecipeClassifier.classify(pattern).type()
                == cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECORecipeClassifier.Type.NORMAL
            ? Long.MAX_VALUE : 0;
    }

    @Override
    public long pushBatch(IPatternDetails pattern, KeyCounter[] oneCopy, long maxCraft) {
        var bus = (ECOCraftingPatternBusBlockEntity) (Object) this;
        return pushBatch(new BatchDispatchContext(pattern, oneCopy, maxCraft, bus.getLevel(), null));
    }

    @Override
    public long pushBatch(BatchDispatchContext context) {
        var bus = (ECOCraftingPatternBusBlockEntity) (Object) this;
        var batch = ECOFastPathFacade.prepareAllocated(bus, context.details(), context.oneCopyTemplate(),
            context.maxCraft(), context.level(), context.craftingJobId());
        if (batch == null) return context.maxCraft();
        // Thunderbolt settles energy and reinjects exactly the returned number of unaccepted copies.
        boolean accepted = batch.submit(amount -> new ECOFastPathFacade.Reservation() {
            public void commit() {}
            public void refund() {}
        });
        return accepted ? context.maxCraft() - batch.craftCount() : context.maxCraft();
    }
}
