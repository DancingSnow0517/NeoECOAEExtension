package cn.dancingsnow.neoecoae.network;

import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.me.menu.ECOForceCraftStartSync;
import net.minecraft.network.FriendlyByteBuf;
import cn.dancingsnow.neoecoae.platform.network.NEStreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import cn.dancingsnow.neoecoae.platform.network.NEPacketContext;

public record ECOForceCraftStartFlagC2SPacket(int containerId, boolean forceStart) {

    public static final NEStreamCodec<FriendlyByteBuf, ECOForceCraftStartFlagC2SPacket> STREAM_CODEC =
        NEStreamCodec.of((buf, packet) -> { buf.writeVarInt(packet.containerId); buf.writeBoolean(packet.forceStart); },
            buf -> new ECOForceCraftStartFlagC2SPacket(buf.readVarInt(), buf.readBoolean()));

    public static void handle(ECOForceCraftStartFlagC2SPacket packet, NEPacketContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            var current = player.containerMenu;
            var logger = org.slf4j.LoggerFactory.getLogger("neoecoae");
            logger.info("[craft-submit] Server received: container={}, currentContainer={}, forced={}",
                packet.containerId, current == null ? null : current.containerId, packet.forceStart);
            if (!(current instanceof CraftConfirmMenu menu) || menu.containerId != packet.containerId) {
                logger.warn("[craft-submit] Rejected packet: reason=MENU_MISMATCH, container={}", packet.containerId);
                return;
            }
            if (!menu.stillValid(player)) {
                logger.warn("[craft-submit] Rejected packet: reason=MENU_INVALID, container={}", packet.containerId);
                player.closeContainer();
                return;
            }
            if (!(menu instanceof ECOForceCraftStartSync sync)) {
                logger.warn("[craft-submit] Rejected packet: reason=FORCE_START_SYNC_UNAVAILABLE, container={}",
                    packet.containerId);
                menu.submitError = new CraftConfirmMenu.SyncableSubmitResult(
                    appeng.crafting.execution.CraftingSubmitResult.INCOMPLETE_PLAN);
                return;
            }
            CraftSubmissionPacketFeedback.submit(menu, "ordinary", packet.forceStart, () -> {
                sync.neoecoae$setForceCraftStart(packet.forceStart);
                menu.startJob();
                var error = menu.submitError.result();
                if (error != null && !error.successful()) {
                    logger.warn("[craft-submit] Submission failed: container={}, error={}, detail={}",
                        packet.containerId, error.errorCode(), error.errorDetail());
                }
            });
        });
    }

}
