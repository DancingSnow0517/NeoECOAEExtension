package cn.dancingsnow.neoecoae.mixins.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "appeng.crafting.execution.ExecutingCraftingJob$TaskProgress", remap = false)
public interface TaskProgressAccessor {
    @Accessor("value")
    long neoecoae$getValue();

    @Accessor("value")
    void neoecoae$setValue(long value);
}
