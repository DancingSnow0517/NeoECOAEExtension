package cn.dancingsnow.neoecoae.mixins.useless;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.compat.useless.UselessScaledPatternView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Injects the runtime-safe view into Useless Mod's scaled pattern wrapper. */
@Pseudo
@Mixin(targets = "com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.ScaledProcessingPattern",
    remap = false)
public abstract class UselessScaledPatternMixin implements UselessScaledPatternView {
    @Override
    @Invoker("getOriginal")
    public abstract IPatternDetails neoecoae$getOriginal();
}
