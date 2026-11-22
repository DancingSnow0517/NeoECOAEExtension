package cn.dancingsnow.neoecoae.mixins.ae2.crafting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "appeng.me.service.helpers.NetworkCraftingProviders$PatternsForKey", remap = false)
public abstract class PatternsForKeyMixin {
    @Shadow private boolean needsSorting;

    @Inject(method = "sortPatterns", at = @At("RETURN"))
    private void neoecoae$markPatternsSorted(CallbackInfo ci) {
        needsSorting = false;
    }
}
