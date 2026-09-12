package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.api.me.menu.ECOForceCraftStartSync;
import cn.dancingsnow.neoecoae.impl.crafting.ECOForcedCraftingPlan;
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

    @Inject(method = "startJob", at = @At("HEAD"))
    private void neoecoae$wrapSimulationPlan(CallbackInfo ci) {
        CraftConfirmMenu menu = (CraftConfirmMenu) (Object) this;
        if (menu.isClientSide() || !neoecoae$consumeForceCraftStart()
                || result == null || !result.simulation()) return;
        neoecoae$forceCraftStartActive = true;
        neoecoae$originalSimulationResult = result;
        result = new ECOForcedCraftingPlan(result);
    }

    @Inject(method = "startJob", at = @At("RETURN"))
    private void neoecoae$restorePlan(CallbackInfo ci) {
        if (neoecoae$originalSimulationResult != null) {
            result = neoecoae$originalSimulationResult;
            neoecoae$originalSimulationResult = null;
            neoecoae$forceCraftStartActive = false;
        }
    }
}
