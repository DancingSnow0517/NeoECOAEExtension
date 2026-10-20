package cn.dancingsnow.neoecoae.mixins.compat.thunderbolt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.compat.thunderbolt.ECOThunderboltBatchBridge;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;
import org.spongepowered.asm.mixin.Mixin;

/** AE2LT (including its time-wheel CPU) retains allocation, energy and job accounting. */
@Mixin(value = ECOCraftingPatternBusBlockEntity.class, remap = false)
public abstract class ECOThunderboltProviderMixin implements IBatchCraftingProvider {
    @Override
    public long getBatchCapacity(IPatternDetails pattern) {
        return ECOThunderboltBatchBridge.capacity((ECOCraftingPatternBusBlockEntity) (Object) this, pattern);
    }

    /**
     * Compatibility layer: this method now uses Thunderbolt's stable three-argument batch contract instead of
     * {@code BatchDispatchContext}, which is not present in every supported Thunderbolt Core build.
     */
    @Override
    public long pushBatch(IPatternDetails pattern, KeyCounter[] oneCopy, long maxCraft) {
        var bus = (ECOCraftingPatternBusBlockEntity) (Object) this;
        return ECOThunderboltBatchBridge.push(bus, pattern, oneCopy, maxCraft, bus.getLevel(), null);
    }
    @Override
    public long pushBatch(IPatternDetails pattern, KeyCounter[] oneCopy, long maxCraft,
            com.moakiee.thunderbolt.api.crafting.batch.BatchJobView job) {
        return ECOThunderboltBatchBridge.push((ECOCraftingPatternBusBlockEntity) (Object) this,
            pattern, oneCopy, maxCraft, job.level(), job.craftingId());
    }
}
