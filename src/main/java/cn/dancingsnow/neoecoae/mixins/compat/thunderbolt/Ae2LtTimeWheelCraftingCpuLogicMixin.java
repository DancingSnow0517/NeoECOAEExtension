package cn.dancingsnow.neoecoae.mixins.compat.thunderbolt;

import cn.dancingsnow.neoecoae.compat.ae2lt.ECOAe2LtCancellationBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Notifies ECO workers when AE2LT's independent time-wheel CPU is cancelled. */
@Pseudo
@Mixin(targets = "com.moakiee.ae2lt.crafting.timewheel.Ae2LtTimeWheelCraftingCpuLogic", remap = false)
public abstract class Ae2LtTimeWheelCraftingCpuLogicMixin {
    @Inject(method = "finishJob", at = @At("HEAD"), require = 0)
    private void neoecoae$recoverCancelledWorkers(boolean success, CallbackInfo ci) {
        if (!success) {
            ECOAe2LtCancellationBridge.cancel(this);
        }
    }
}
