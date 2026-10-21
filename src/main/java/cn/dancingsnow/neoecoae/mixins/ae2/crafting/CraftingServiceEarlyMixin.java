package cn.dancingsnow.neoecoae.mixins.ae2.crafting;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.platform.mixin.CraftingServiceEarlyBridge;
import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalLongRef;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The four source order=500 callbacks run before normal-priority providers on Mixin 0.8.5. */
@Mixin(value = CraftingService.class, priority = 1500, remap = false)
public abstract class CraftingServiceEarlyMixin {
    @Inject(method = "insertIntoCpus", at = @At(value = "RETURN", shift = At.Shift.BY, by = -1))
    private void neoecoae$insert(AEKey key, long amount, Actionable mode, CallbackInfoReturnable<Long> result,
            @Local(name = "inserted") LocalLongRef inserted) {
        ((CraftingServiceEarlyBridge) this).neoecoae$earlyInsertIntoCpus(key, amount, mode, result, inserted);
    }

    @Inject(method = "submitJob", at = @At(value = "INVOKE_ASSIGN", target = "Lappeng/me/service/CraftingService;findSuitableCraftingCPU(Lappeng/api/networking/crafting/ICraftingPlan;ZLappeng/api/networking/security/IActionSource;Lorg/apache/commons/lang3/mutable/MutableObject;)Lappeng/me/cluster/implementations/CraftingCPUCluster;"), cancellable = true)
    private void neoecoae$submit(ICraftingPlan job, ICraftingRequester requester, ICraftingCPU target,
            boolean prioritizePower, IActionSource source, CallbackInfoReturnable<ICraftingSubmitResult> result,
            @Local(name = "unsuitableCpusResult") MutableObject<UnsuitableCpus> unsuitableCpus) {
        ((CraftingServiceEarlyBridge) this).neoecoae$earlySubmitJob(job, requester, target,
                prioritizePower, source, result, unsuitableCpus);
    }

    @Inject(method = "getCpus", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableSet$Builder;build()Lcom/google/common/collect/ImmutableSet;"))
    private void neoecoae$cpus(CallbackInfoReturnable<ImmutableSet<ICraftingCPU>> result,
            @Local(name = "cpus") ImmutableSet.Builder<ICraftingCPU> cpus) {
        ((CraftingServiceEarlyBridge) this).neoecoae$earlyGetCpus(result, cpus);
    }

    @Inject(method = "getRequestedAmount", at = @At("RETURN"), cancellable = true)
    private void neoecoae$requested(AEKey key, CallbackInfoReturnable<Long> result) {
        ((CraftingServiceEarlyBridge) this).neoecoae$earlyGetRequestedAmount(key, result);
    }
}
