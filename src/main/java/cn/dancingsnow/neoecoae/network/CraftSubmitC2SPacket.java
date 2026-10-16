package cn.dancingsnow.neoecoae.network;

import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftConfirmMenuMode;
import cn.dancingsnow.neoecoae.api.me.menu.ECOForceCraftStartSync;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.LoggerFactory;

/** Only submission intent crosses the network; the open server menu owns the plan and CPU. */
public record CraftSubmitC2SPacket(int containerId, SubmitMode mode, boolean forced) implements ECOPayload {
    public enum SubmitMode { STANDARD, BIG_ORDER }

    public static final Type<CraftSubmitC2SPacket> TYPE = new Type<>(NeoECOAE.id("craft_submit"));
    public static final StreamCodec<FriendlyByteBuf, CraftSubmitC2SPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeVarInt(packet.containerId);
            buf.writeEnum(packet.mode);
            buf.writeBoolean(packet.forced);
        },
        buf -> new CraftSubmitC2SPacket(buf.readVarInt(), buf.readEnum(SubmitMode.class), buf.readBoolean()));

    /** Retains the client's existing routing policy, including the separate ordinary force-start path. */
    public static SubmitMode selectMode(boolean unrepresentable, boolean forced, PlanningStatus status,
            boolean hasCycles) {
        return unrepresentable || forced && status == PlanningStatus.MISSING_ITEMS && hasCycles
            ? SubmitMode.BIG_ORDER : SubmitMode.STANDARD;
    }

    public static void handle(CraftSubmitC2SPacket packet, IPayloadContext context) {
        ECOPayload.dispatch(packet, context);
    }

    @Override public void handleOnServer(IPayloadContext context) {
        CraftSubmitC2SPacket packet = this;
        if (!(context.player() instanceof ServerPlayer player)) return;
        var current = player.containerMenu;
        var logger = LoggerFactory.getLogger("neoecoae");
        String tag = packet.mode == SubmitMode.BIG_ORDER ? "[big-order-submit]" : "[craft-submit]";
        logger.info("{} Server received: container={}, currentContainer={}, forced={}",
            tag, packet.containerId, current == null ? null : current.containerId, packet.forced);
        if (!(current instanceof CraftConfirmMenu menu) || menu.containerId != packet.containerId) {
            logger.warn("{} Rejected packet: reason=MENU_MISMATCH, container={}", tag, packet.containerId);
            return;
        }
        if (!menu.stillValid(player)) {
            logger.warn("{} Rejected packet: reason=MENU_INVALID, container={}", tag, packet.containerId);
            player.closeContainer();
            return;
        }
        if (packet.mode == SubmitMode.BIG_ORDER) {
            if (!(menu instanceof ECOCraftConfirmMenuMode mode)) {
                rejectCapability(menu, tag, "ECO_MENU_UNAVAILABLE");
                return;
            }
            mode.neoecoae$startBigOrder(packet.forced);
        } else {
            if (!(menu instanceof ECOForceCraftStartSync sync)) {
                rejectCapability(menu, tag, "FORCE_START_SYNC_UNAVAILABLE");
                return;
            }
            // CraftConfirmMenuForceStartMixin consumes this flag at startJob HEAD.
            sync.neoecoae$setForceCraftStart(packet.forced);
            menu.startJob();
            var error = menu.submitError.result();
            if (error != null && !error.successful()) {
                logger.warn("{} Submission failed: container={}, error={}, detail={}",
                    tag, packet.containerId, error.errorCode(), error.errorDetail());
            }
        }
    }

    private static void rejectCapability(CraftConfirmMenu menu, String tag, String reason) {
        LoggerFactory.getLogger("neoecoae").warn("{} Rejected packet: reason={}, container={}",
            tag, reason, menu.containerId);
        menu.submitError = new CraftConfirmMenu.SyncableSubmitResult(
            appeng.crafting.execution.CraftingSubmitResult.INCOMPLETE_PLAN);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
