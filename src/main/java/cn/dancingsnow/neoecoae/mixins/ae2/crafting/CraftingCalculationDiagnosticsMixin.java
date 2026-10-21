package cn.dancingsnow.neoecoae.mixins.ae2.crafting;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.platform.mixin.CraftingCalculationDiagnosticsBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mixin 0.8.5 has no per-injector order; lower priority puts diagnostics after normal plan transformers. */
@Mixin(value = CraftingCalculation.class, priority = 500, remap = false)
public abstract class CraftingCalculationDiagnosticsMixin {
    @Inject(method = "runCraftAttempt", at = @At("RETURN"))
    private void neoecoae$attachAttempt(boolean simulate, long amount, CallbackInfoReturnable<CraftingPlan> result) {
        ((CraftingCalculationDiagnosticsBridge) this).neoecoae$attachAttemptDiagnostics(simulate, amount, result);
    }

    @Inject(method = "run", at = @At("RETURN"))
    private void neoecoae$attachFinal(CallbackInfoReturnable<ICraftingPlan> result) {
        ((CraftingCalculationDiagnosticsBridge) this).neoecoae$attachFinalDiagnostics(result);
    }
}
