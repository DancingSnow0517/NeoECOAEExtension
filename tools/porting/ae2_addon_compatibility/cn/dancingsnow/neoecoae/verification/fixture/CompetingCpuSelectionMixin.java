package cn.dancingsnow.neoecoae.verification.fixture;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.service.CraftingService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Simulates an independent addon that claims submission after the native automatic scan. */
@Mixin(value = CraftingService.class, remap = false)
public abstract class CompetingCpuSelectionMixin {
    @Inject(method = "submitJob", at = @At(value = "INVOKE_ASSIGN", target =
        "Lappeng/me/service/CraftingService;findSuitableCraftingCPU(Lappeng/api/networking/crafting/ICraftingPlan;ZLappeng/api/networking/security/IActionSource;Lorg/apache/commons/lang3/mutable/MutableObject;)Lappeng/me/cluster/implementations/CraftingCPUCluster;"),
        cancellable = true)
    private void independentAddonSelectCpu(ICraftingPlan plan, ICraftingRequester requester, ICraftingCPU target,
            boolean prioritizePower, IActionSource source, CallbackInfoReturnable<ICraftingSubmitResult> callback) {
        callback.setReturnValue(CraftingSubmitResult.NO_CPU_FOUND);
    }
}
