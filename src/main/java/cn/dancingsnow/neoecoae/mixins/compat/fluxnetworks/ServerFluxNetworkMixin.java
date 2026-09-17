package cn.dancingsnow.neoecoae.mixins.compat.fluxnetworks;

import cn.dancingsnow.neoecoae.util.NEMath;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

/** Prevents unlimited storage demand from wrapping the network's input limit below zero. */
@Pseudo
@Mixin(targets = "sonar.fluxnetworks.common.connection.ServerFluxNetwork", remap = false)
public abstract class ServerFluxNetworkMixin {
    @ModifyExpressionValue(
        method = "onEndServerTick()V",
        at = @At(value = "INVOKE",
            target = "Lsonar/fluxnetworks/common/connection/TransferHandler;getRequest()J"),
        slice = @Slice(from = @At(value = "INVOKE",
            target = "Lsonar/fluxnetworks/common/connection/TransferHandler;onCycleEnd()V")),
        require = 1, allow = 1, remap = false)
    private static long neoecoae$limitDemandContribution(long request, @Local(name = "limiter") long total) {
        // The original LADD still runs. Bound its operand BEFORE addition, on every iteration.
        // Negative requests contribute zero; the accumulator starts at zero and stays non-negative.
        return NEMath.saturatingAdd(total, request) - total;
    }
}
