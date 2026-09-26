package cn.dancingsnow.neoecoae.mixins.compat.omnisequence;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.compat.omnisequence.ECOOmniSequenceBatchAdapter;
import com.atir.molecularmanipulator.api.crafting.OmniBatchAdmission;
import com.atir.molecularmanipulator.api.crafting.OmniBatchCraftingProvider;
import com.atir.molecularmanipulator.api.crafting.OmniBatchProbe;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ECOCraftingPatternBusBlockEntity.class, remap = false)
public abstract class ECOOmniSequenceProviderMixin implements OmniBatchCraftingProvider {
    @Override
    public OmniBatchAdmission prepareOmniBatch(OmniBatchProbe probe) {
        var bus = (ECOCraftingPatternBusBlockEntity) (Object) this;
        return ECOOmniSequenceBatchAdapter.prepare(bus, bus.getLevel(), probe);
    }
}
