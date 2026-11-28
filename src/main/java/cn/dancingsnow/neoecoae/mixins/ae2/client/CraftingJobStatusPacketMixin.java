package cn.dancingsnow.neoecoae.mixins.ae2.client;

import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.PinnedKeys;
import appeng.core.network.clientbound.CraftingJobStatusPacket;
import cn.dancingsnow.neoecoae.client.gui.crafting.ECONetworkPinnedJobs;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Routes network-tagged crafting status notifications into per-network pin storage instead of global PinnedKeys. */
@Mixin(CraftingJobStatusPacket.class)
public abstract class CraftingJobStatusPacketMixin {
    @Shadow
    @Final
    private UUID jobId;

    @Shadow
    @Final
    private AEKey what;

    @Shadow
    @Final
    private CraftingJobStatusPacket.Status status;

    @Inject(method = "handleOnClient", at = @At("HEAD"))
    private void neoecoae$trackNetworkPin(Player player, CallbackInfo ci) {
        ECONetworkPinnedJobs.handleJobStatus(jobId, what, status);
    }

    @WrapOperation(
        method = "handleOnClient",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/client/gui/me/common/PinnedKeys;pinKey(Lappeng/api/stacks/AEKey;Lappeng/client/gui/me/common/PinnedKeys$PinReason;)V"
        )
    )
    private void neoecoae$skipGlobalPinForNetworkJob(
            AEKey key, PinnedKeys.PinReason reason, Operation<Void> original) {
        if (!ECONetworkPinnedJobs.hasBoundNetwork(jobId)) {
            original.call(key, reason);
        }
    }
}
