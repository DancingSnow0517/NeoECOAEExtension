package cn.dancingsnow.neoecoae.mixins.compat.useless;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import java.util.function.Function;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps Useless Mod's smart-doubling optimizer from rewriting a plan whose ECO execution schedule was confirmed.
 * The target is optional. The plugin skips this hook when Useless (2.4.5.11+) already calls the API.
 */
@Pseudo
@Mixin(targets = "com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPlans",
    remap = false)
public abstract class UselessSmartDoublingPlansMixin {
    @Group(name = "neoecoae$preserveSubmissionPlan", min = 1)
    @Inject(method = "rewriteForSubmission(Lappeng/api/networking/crafting/ICraftingPlan;Ljava/util/function/Function;)Lappeng/api/networking/crafting/ICraftingPlan;",
        at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void neoecoae$preserveBoundPlan(ICraftingPlan plan,
            Function<IPatternDetails, Iterable<ICraftingProvider>> providerLookup,
            CallbackInfoReturnable<ICraftingPlan> cir) {
        neoecoae$preservePlan(plan, cir);
    }

    // Useless 2.4.5.4 calls this overload directly, bypassing the two-argument entry point.
    @Group(name = "neoecoae$preserveSubmissionPlan", min = 1)
    @Inject(method = "rewriteForSubmission(Lappeng/api/networking/crafting/ICraftingPlan;Ljava/util/function/Function;Lnet/minecraft/world/level/Level;)Lappeng/api/networking/crafting/ICraftingPlan;",
        at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void neoecoae$preserveBoundPlanWithLevel(ICraftingPlan plan,
            Function<IPatternDetails, Iterable<ICraftingProvider>> providerLookup, Level level,
            CallbackInfoReturnable<ICraftingPlan> cir) {
        neoecoae$preservePlan(plan, cir);
    }

    @Unique
    private static void neoecoae$preservePlan(ICraftingPlan plan, CallbackInfoReturnable<ICraftingPlan> cir) {
        if (ECOPlanningResultRegistry.shouldPreserveSubmissionPlan(plan)) {
            cir.setReturnValue(plan);
        }
    }
}
