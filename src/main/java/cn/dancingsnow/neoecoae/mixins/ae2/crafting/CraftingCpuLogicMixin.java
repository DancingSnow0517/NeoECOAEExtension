package cn.dancingsnow.neoecoae.mixins.ae2.crafting;

import appeng.core.network.clientbound.CraftingJobStatusPacket;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.network.ECOCraftingJobNetworkS2CPacket;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Tags AE2 CPU job-start notifications with their source network so pinned rows do not leak across networks. */
@Mixin(CraftingCpuLogic.class)
public abstract class CraftingCpuLogicMixin {
    @Shadow
    @Final
    CraftingCPUCluster cluster;

    @WrapOperation(
        method = "notifyJobOwner",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"
        )
    )
    private void neoecoae$attachNetworkId(
            ServerGamePacketListenerImpl connection,
            CustomPacketPayload payload,
            Operation<Void> original) {
        if (payload instanceof CraftingJobStatusPacket statusPacket
                && statusPacket.status() == CraftingJobStatusPacket.Status.STARTED) {
            long networkId = ECOCraftingNetworkSettings.networkId(cluster.getGrid());
            if (networkId > 0L) {
                PacketDistributor.sendToPlayer(connection.player,
                    new ECOCraftingJobNetworkS2CPacket(statusPacket.jobId(), networkId));
            }
        }
        original.call(connection, payload);
    }
}
