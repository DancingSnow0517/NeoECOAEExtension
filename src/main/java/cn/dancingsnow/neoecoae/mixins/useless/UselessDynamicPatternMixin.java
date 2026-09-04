package cn.dancingsnow.neoecoae.mixins.useless;

import cn.dancingsnow.neoecoae.compat.useless.UselessDynamicPatternView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Injects the runtime-safe view into Useless Mod's dynamic pattern details. */
@Pseudo
@Mixin(targets =
    "com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicComponentPatternDetails",
    remap = false)
public abstract class UselessDynamicPatternMixin implements UselessDynamicPatternView {
    @Override
    @Invoker("isItemIdInput")
    public abstract boolean neoecoae$isItemIdInput(int slot);

    @Override
    @Invoker("isTagInput")
    public abstract boolean neoecoae$isTagInput(int slot);

    @Override
    @Invoker("isFluidTagInput")
    public abstract boolean neoecoae$isFluidTagInput(int slot);

    @Override
    @Invoker("usesDynamicOutputs")
    public abstract boolean neoecoae$usesDynamicOutputs();
}
