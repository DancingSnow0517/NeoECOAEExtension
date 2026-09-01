package cn.dancingsnow.neoecoae.mixins.useless;

import appeng.api.crafting.IPatternDetails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Optional typed unwrapping boundary for Useless Mod's smart-doubling pattern wrapper. */
@Pseudo
@Mixin(targets = "com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.ScaledProcessingPattern",
    remap = false)
public interface UselessScaledPatternAccessor {
    @Invoker("getOriginal")
    IPatternDetails neoecoae$getOriginal();
}
