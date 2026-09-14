package cn.dancingsnow.neoecoae.mixins;

import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingServiceTicker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs custom CPU work before GTLCore can cancel a throttled CraftingService tick. */
@Mixin(value = CraftingService.class, priority = 1100, remap = false)
public abstract class CraftingServiceEarlyTickMixin {
    @Inject(method = "onServerEndTick", at = @At("HEAD"))
    private void neoecoae$tickBeforeCompatibilityThrottle(CallbackInfo ci) {
        ((ECOCraftingServiceTicker) this).neoecoae$tickComputationCpusNow();
    }
}
