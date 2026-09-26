package cn.dancingsnow.neoecoae.mixins.compat.thunderbolt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationPatternProvider;
import cn.dancingsnow.neoecoae.compat.thunderbolt.ECOThunderboltWorkstationBridge;
import com.moakiee.thunderbolt.api.crafting.batch.BatchJobView;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Makes only the workstation queue available to AE2LT's independent batch CPU. */
@Pseudo
@Mixin(value = LargeWorkstationPatternProvider.class, remap = false)
public abstract class ECOThunderboltWorkstationProviderMixin implements IBatchCraftingProvider {
    @Override
    public long getBatchCapacity(IPatternDetails pattern) {
        return ECOThunderboltWorkstationBridge.capacity((LargeWorkstationPatternProvider) (Object) this);
    }

    @Override
    public long pushBatch(IPatternDetails pattern, KeyCounter[] oneCopy, long requested) {
        return ECOThunderboltWorkstationBridge.push((LargeWorkstationPatternProvider) (Object) this,
            pattern, oneCopy, requested, null);
    }

    @Override
    public long pushBatch(IPatternDetails pattern, KeyCounter[] oneCopy, long requested, BatchJobView job) {
        return ECOThunderboltWorkstationBridge.push((LargeWorkstationPatternProvider) (Object) this,
            pattern, oneCopy, requested, job.craftingId());
    }
}
