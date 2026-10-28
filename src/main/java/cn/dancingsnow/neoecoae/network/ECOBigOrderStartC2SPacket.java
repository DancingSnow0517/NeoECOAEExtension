package cn.dancingsnow.neoecoae.network;

import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftConfirmMenuMode;
import net.minecraft.network.FriendlyByteBuf;
import cn.dancingsnow.neoecoae.platform.network.NEStreamCodec;
import cn.dancingsnow.neoecoae.platform.network.NEPacketContext;

/** Contains intent only; the server resolves the result, target and CPU from the open menu. */
public record ECOBigOrderStartC2SPacket(int containerId, boolean forced) {

    public static final NEStreamCodec<FriendlyByteBuf, ECOBigOrderStartC2SPacket> STREAM_CODEC = NEStreamCodec.of(
        (buf, packet) -> { buf.writeVarInt(packet.containerId); buf.writeBoolean(packet.forced); },
        buf -> new ECOBigOrderStartC2SPacket(buf.readVarInt(), buf.readBoolean()));
    public static void handle(ECOBigOrderStartC2SPacket packet, NEPacketContext context) {
        context.enqueueWork(() -> {
            var current = context.player().containerMenu;
            var logger = org.slf4j.LoggerFactory.getLogger("neoecoae");
            logger.info("[big-order-submit] Server received: player={}, container={}, currentContainer={}, forced={}",
                context.player().getGameProfile().getName(), packet.containerId, current.containerId, packet.forced);
            if (!(current instanceof CraftConfirmMenu menu) || menu.containerId != packet.containerId) {
                logger.warn("[big-order-submit] Rejected packet: reason=MENU_MISMATCH, menuType={}",
                    current.getClass().getName());
                return;
            }
            if (!menu.stillValid(context.player())) {
                logger.warn("[big-order-submit] Rejected packet: reason=MENU_INVALID, container={}", packet.containerId);
                context.player().closeContainer();
                return;
            }
            if (!(menu instanceof ECOCraftConfirmMenuMode mode)) {
                logger.warn("[big-order-submit] Rejected packet: reason=ECO_MENU_UNAVAILABLE, container={}", packet.containerId);
                menu.submitError = new CraftConfirmMenu.SyncableSubmitResult(
                    appeng.crafting.execution.CraftingSubmitResult.INCOMPLETE_PLAN);
                return;
            }
            CraftSubmissionPacketFeedback.submit(menu, "big-order", packet.forced,
                () -> mode.neoecoae$startBigOrder(packet.forced));
        });
    }
}
