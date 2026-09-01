package cn.dancingsnow.neoecoae.mixins.useless;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Optional typed view of Useless Mod's dynamic-component pattern policy. */
@Pseudo
@Mixin(targets =
    "com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicComponentPatternDetails",
    remap = false)
public interface UselessDynamicPatternAccessor {
    @Invoker("isItemIdInput")
    boolean neoecoae$isItemIdInput(int slot);

    @Invoker("isTagInput")
    boolean neoecoae$isTagInput(int slot);

    @Invoker("isFluidTagInput")
    boolean neoecoae$isFluidTagInput(int slot);

    @Invoker("usesDynamicOutputs")
    boolean neoecoae$usesDynamicOutputs();
}
