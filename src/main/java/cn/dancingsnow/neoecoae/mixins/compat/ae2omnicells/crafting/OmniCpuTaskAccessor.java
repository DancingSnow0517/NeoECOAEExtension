package cn.dancingsnow.neoecoae.mixins.compat.ae2omnicells.crafting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "appeng.crafting.execution.ExecutingCraftingJob$TaskProgress", remap = false)
public interface OmniCpuTaskAccessor {
    @Accessor("value") long neoecoae$value();
    @Accessor("value") void neoecoae$value(long value);
}
