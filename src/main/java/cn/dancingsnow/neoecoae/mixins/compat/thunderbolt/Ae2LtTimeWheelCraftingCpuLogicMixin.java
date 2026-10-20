package cn.dancingsnow.neoecoae.mixins.compat.thunderbolt;

import cn.dancingsnow.neoecoae.compat.ae2lt.ECOAe2LtCancellationBridge;
import cn.dancingsnow.neoecoae.compat.ae2lt.ECOAe2LtJobDispatch;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Notifies ECO workers when AE2LT's independent time-wheel CPU is cancelled. */
@Pseudo
@Mixin(targets = "com.moakiee.ae2lt.crafting.timewheel.Ae2LtTimeWheelCraftingCpuLogic", remap = false)
public abstract class Ae2LtTimeWheelCraftingCpuLogicMixin {
    @WrapOperation(method = "tryPushPattern", at = @At(value = "INVOKE",
        target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"), require = 0)
    private boolean neoecoae$preserveJobOnReleasedEngine(ICraftingProvider provider, IPatternDetails pattern,
            KeyCounter[] inputs, Operation<Boolean> ordinary) {
        return ECOAe2LtJobDispatch.push(this, provider, pattern, inputs, () -> ordinary.call(provider, pattern, inputs));
    }

    @Inject(method = "finishJob", at = @At("HEAD"), require = 0)
    private void neoecoae$recoverCancelledWorkers(boolean success, CallbackInfo ci) {
        if (!success) {
            ECOAe2LtCancellationBridge.cancel(this);
        }
    }
}
