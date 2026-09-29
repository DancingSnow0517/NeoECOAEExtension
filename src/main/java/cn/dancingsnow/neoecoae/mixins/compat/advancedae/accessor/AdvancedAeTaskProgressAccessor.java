package cn.dancingsnow.neoecoae.mixins.compat.advancedae.accessor;

import cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuJob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob$TaskProgress", remap = false)
public abstract class AdvancedAeTaskProgressAccessor implements ECOExternalCpuJob.Task {
    @Accessor("value")
    public abstract long neoecoae$value();

    @Accessor("value")
    public abstract void neoecoae$value(long value);
}
