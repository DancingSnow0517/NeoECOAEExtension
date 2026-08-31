package cn.dancingsnow.neoecoae.mixins.useless;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.api.me.ECOPlanningResultRegistry;
import java.util.function.Function;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps Useless Mod's smart-doubling optimizer from rewriting a plan whose ECO execution schedule was confirmed.
 * The target is optional; the class is ignored when Useless Mod is not installed.
 */
@Pseudo
@Mixin(targets = "com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPlans",
    remap = false)
public abstract class CraftingServiceSmartDoublingMixin {
    @Inject(method = "rewriteForSubmission", at = @At("HEAD"), cancellable = true, remap = false)
    private static void neoecoae$preserveBoundPlan(ICraftingPlan plan,
            Function<IPatternDetails, Iterable<ICraftingProvider>> providerLookup,
            CallbackInfoReturnable<ICraftingPlan> cir) {
        if (ECOPlanningResultRegistry.shouldPreserveSubmissionPlan(plan)) {
            cir.setReturnValue(plan);
        }
    }
}
