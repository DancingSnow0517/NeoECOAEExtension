package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.api.me.menu.ECOForceCraftStartSync;
import cn.dancingsnow.neoecoae.compat.extendedaeplus.EAEPForcedCrafting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CraftConfirmMenu.class, remap = false)
public abstract class CraftConfirmMenuForceStartMixin implements ECOForceCraftStartSync {
    @Shadow private ICraftingPlan result;
    @Unique private boolean neoecoae$pendingForceCraftStart;
    @Unique private boolean neoecoae$forceCraftStartActive;
    @Unique private ICraftingPlan neoecoae$originalSimulationResult;

    @Override public void neoecoae$setForceCraftStart(boolean forceStart) {
        neoecoae$pendingForceCraftStart = forceStart;
    }

    @Override public boolean neoecoae$consumeForceCraftStart() {
        boolean value = neoecoae$pendingForceCraftStart;
        neoecoae$pendingForceCraftStart = false;
        return value;
    }

    @Override public boolean neoecoae$isForceCraftStartActive() {
        return neoecoae$forceCraftStartActive;
    }

    @Inject(method = "startJob", at = @At("HEAD"), cancellable = true)
    private void neoecoae$wrapSimulationPlan(CallbackInfo ci) {
        CraftConfirmMenu menu = (CraftConfirmMenu) (Object) this;
        if (menu.isClientSide() || !neoecoae$consumeForceCraftStart()
                || result == null || !result.simulation()) return;
        if ((Object) menu instanceof cn.dancingsnow.neoecoae.api.me.menu.ECOCraftConfirmMenuMode mode
                && mode.neoecoae$shouldShowFastPlannerReport()
                && mode.neoecoae$getPlanningStatus()
                    != cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus.MISSING_ITEMS) {
            neoecoae$rejectForceStart(menu, ci);
            return;
        }
        try {
            var forced = EAEPForcedCrafting.force(result);
            neoecoae$forceCraftStartActive = true;
            neoecoae$originalSimulationResult = result;
            result = forced;
        } catch (RuntimeException | LinkageError failure) {
            org.slf4j.LoggerFactory.getLogger("neoecoae").warn("[craft-submit] Force-start preparation failed", failure);
            neoecoae$rejectForceStart(menu, ci);
        }
    }

    @Unique
    private void neoecoae$rejectForceStart(CraftConfirmMenu menu, CallbackInfo ci) {
        menu.submitError = new CraftConfirmMenu.SyncableSubmitResult(
            appeng.crafting.execution.CraftingSubmitResult.INCOMPLETE_PLAN);
        ci.cancel();
    }

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "startJob")
    private void neoecoae$restorePlan(com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        try {
            original.call();
        } finally {
            if (neoecoae$originalSimulationResult != null) {
                result = neoecoae$originalSimulationResult;
                neoecoae$originalSimulationResult = null;
            }
            neoecoae$forceCraftStartActive = false;
        }
    }
}
