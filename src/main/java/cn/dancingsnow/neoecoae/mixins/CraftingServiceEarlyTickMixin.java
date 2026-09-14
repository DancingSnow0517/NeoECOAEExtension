package cn.dancingsnow.neoecoae.mixins;

import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingServiceTicker;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

/** Runs custom CPU work before GTLCore can cancel a throttled CraftingService tick. */
@Mixin(value = CraftingService.class, priority = 1100, remap = false)
public abstract class CraftingServiceEarlyTickMixin {
    @WrapMethod(method = "onServerEndTick")
    private void neoecoae$tickBeforeCompatibilityThrottle(Operation<Void> original) {
        ((ECOCraftingServiceTicker) this).neoecoae$tickComputationCpusNow();
        original.call();
    }
}
