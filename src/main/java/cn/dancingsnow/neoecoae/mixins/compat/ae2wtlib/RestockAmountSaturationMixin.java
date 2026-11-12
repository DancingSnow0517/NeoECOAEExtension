package cn.dancingsnow.neoecoae.mixins.compat.ae2wtlib;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AE2WTLib computes the restock overlay amount as {@code stack.getCount() + networkAmount} without
 * saturation. ECO infinite resource cells report {@link Long#MAX_VALUE} (the project-wide "unbounded"
 * sentinel), so the sum wraps negative and {@code ReadableNumberConverter.format} throws, crashing
 * any screen that renders inventory slot decorations. Saturate the result instead.
 */
@Pseudo
@Mixin(targets = "de.mari_023.ae2wtlib.wct.CraftingTerminalHandler", remap = false)
public class RestockAmountSaturationMixin {
    @Inject(method = "getAccessibleAmount", at = @At("RETURN"), cancellable = true, require = 0)
    private void neoecoae$saturateAccessibleAmount(CallbackInfoReturnable<Long> cir) {
        if (cir.getReturnValueJ() < 0L) cir.setReturnValue(Long.MAX_VALUE);
    }
}
