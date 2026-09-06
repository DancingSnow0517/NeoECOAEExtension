package cn.dancingsnow.neoecoae.mixins.advanced_ae;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob$TaskProgress", remap = false)
public interface NeoECOAEAdvTaskProgressAccessor {
    @Accessor("value")
    long neoecoae$getValue();

    @Accessor("value")
    void neoecoae$setValue(long value);
}
