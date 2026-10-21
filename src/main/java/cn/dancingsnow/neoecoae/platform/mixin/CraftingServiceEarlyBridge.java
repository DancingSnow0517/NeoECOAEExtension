package cn.dancingsnow.neoecoae.platform.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.sugar.ref.LocalLongRef;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shared state stays in the ordinary service mixin; only order-sensitive hooks run early. */
public interface CraftingServiceEarlyBridge {
    void neoecoae$earlyInsertIntoCpus(AEKey key, long amount, Actionable mode,
            CallbackInfoReturnable<Long> result, LocalLongRef inserted);
    void neoecoae$earlySubmitJob(ICraftingPlan job, ICraftingRequester requester, ICraftingCPU target,
            boolean prioritizePower, IActionSource source, CallbackInfoReturnable<ICraftingSubmitResult> result,
            MutableObject<UnsuitableCpus> unsuitableCpus);
    void neoecoae$earlyGetCpus(CallbackInfoReturnable<ImmutableSet<ICraftingCPU>> result,
            ImmutableSet.Builder<ICraftingCPU> cpus);
    void neoecoae$earlyGetRequestedAmount(AEKey key, CallbackInfoReturnable<Long> result);
}
