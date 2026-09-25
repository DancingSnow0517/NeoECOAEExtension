package cn.dancingsnow.neoecoae.mixins.ae2.crafting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "appeng.crafting.execution.ExecutingCraftingJob$TaskProgress", remap = false)
public interface Ae2CpuTaskAccessor extends cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuJob.Task {
    @Accessor("value") long neoecoae$value();
    @Accessor("value") void neoecoae$value(long value);
}
