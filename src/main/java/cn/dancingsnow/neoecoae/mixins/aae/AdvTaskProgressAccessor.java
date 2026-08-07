package cn.dancingsnow.neoecoae.mixins.aae;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob$TaskProgress", remap = false)
public interface AdvTaskProgressAccessor {
    @Accessor("value")
    long neoecoae$getValue();

    @Accessor("value")
    void neoecoae$setValue(long value);
}
