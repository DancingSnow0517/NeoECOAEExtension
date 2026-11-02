package cn.dancingsnow.neoecoae.mixins.ae2.crafting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "appeng.crafting.execution.ExecutingCraftingJob$TaskProgress", remap = false)
public abstract class Ae2CpuTaskAccessor
        implements cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuJob.Task {
    @Accessor("value")
    public abstract long neoecoae$value();

    @Accessor("value")
    public abstract void neoecoae$value(long value);
}
