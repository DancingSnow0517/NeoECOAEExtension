package cn.dancingsnow.neoecoae.platform.mixin;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.crafting.CraftingPlan;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets the late injection pass use the calculation's original attempt-local state. */
public interface CraftingCalculationDiagnosticsBridge {
    void neoecoae$attachAttemptDiagnostics(boolean simulate, long amount, CallbackInfoReturnable<CraftingPlan> result);
    void neoecoae$attachFinalDiagnostics(CallbackInfoReturnable<ICraftingPlan> result);
}
