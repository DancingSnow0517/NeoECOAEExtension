package cn.dancingsnow.neoecoae.network;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerFallbackReason;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Synchronizes the fallback marker only to the player currently viewing the affected crafting menu. */
public record ECOPlannerNoticePayload(int containerId, String reasonId, long elapsedNanos) implements CustomPacketPayload {
    public static final Type<ECOPlannerNoticePayload> TYPE = new Type<>(NeoECOAE.id("planner_notice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ECOPlannerNoticePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        ECOPlannerNoticePayload::containerId,
        ByteBufCodecs.STRING_UTF8,
        ECOPlannerNoticePayload::reasonId,
        ByteBufCodecs.VAR_LONG,
        ECOPlannerNoticePayload::elapsedNanos,
        ECOPlannerNoticePayload::new
    );
    private static final Map<Integer, ClientNotice> CLIENT_NOTICES = new ConcurrentHashMap<>();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, ECOPlannerNoticePayload::handle);
    }

    public static Optional<ECOPlannerFallbackReason> getClientNotice(int containerId) {
        return Optional.ofNullable(CLIENT_NOTICES.get(containerId)).map(ClientNotice::reason);
    }

    public static Optional<ClientNotice> getClientNoticeData(int containerId) {
        return Optional.ofNullable(CLIENT_NOTICES.get(containerId));
    }

    public static void clearClientNotice(int containerId) {
        CLIENT_NOTICES.remove(containerId);
    }

    private static void handle(ECOPlannerNoticePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu.containerId != payload.containerId()) {
                return;
            }
            ECOPlannerFallbackReason reason = ECOPlannerFallbackReason.fromId(payload.reasonId());
            CLIENT_NOTICES.put(payload.containerId(), new ClientNotice(reason, payload.elapsedNanos()));
            if (reason == ECOPlannerFallbackReason.FAST_PATH) {
                return;
            }
            context.player().displayClientMessage(
                Component.translatable(
                    "chat.neoecoae.planning.ae2_fallback",
                    Component.translatable(reason.translationKey())
                ),
                false
            );
        });
    }

    public record ClientNotice(ECOPlannerFallbackReason reason, long elapsedNanos) {
    }
}
