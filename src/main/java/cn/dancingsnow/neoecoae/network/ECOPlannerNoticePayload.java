package cn.dancingsnow.neoecoae.network;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerDiagnostic;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerFallbackReason;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

/** Synchronizes planner status only to the player currently viewing the affected crafting menu. */
public record ECOPlannerNoticePayload(
    int containerId,
    long requestId,
    String reasonId,
    long elapsedNanos,
    String formattedBytes,
    String diagnosticIds
) implements CustomPacketPayload {
    public ECOPlannerNoticePayload(
        int containerId,
        long requestId,
        String reasonId,
        long elapsedNanos,
        String formattedBytes
    ) {
        this(containerId, requestId, reasonId, elapsedNanos, formattedBytes, "");
    }
    public static final Type<ECOPlannerNoticePayload> TYPE = new Type<>(NeoECOAE.id("planner_notice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ECOPlannerNoticePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        ECOPlannerNoticePayload::containerId,
        ByteBufCodecs.VAR_LONG,
        ECOPlannerNoticePayload::requestId,
        ByteBufCodecs.STRING_UTF8,
        ECOPlannerNoticePayload::reasonId,
        ByteBufCodecs.VAR_LONG,
        ECOPlannerNoticePayload::elapsedNanos,
        ByteBufCodecs.STRING_UTF8,
        ECOPlannerNoticePayload::formattedBytes,
        ByteBufCodecs.STRING_UTF8,
        ECOPlannerNoticePayload::diagnosticIds,
        ECOPlannerNoticePayload::new
    );
    private static final Map<Integer, ClientNotice> CLIENT_NOTICES = new ConcurrentHashMap<>();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToClient(TYPE, STREAM_CODEC, ECOPlannerNoticePayload::handle);
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
            ClientNotice incoming = new ClientNotice(
                payload.requestId(),
                reason,
                payload.elapsedNanos(),
                payload.formattedBytes(),
                parseDiagnostics(payload.diagnosticIds())
            );
            ClientNotice previous = CLIENT_NOTICES.get(payload.containerId());
            if (previous != null && previous.requestId() > incoming.requestId()) {
                return;
            }
            CLIENT_NOTICES.put(payload.containerId(), incoming);
            if (reason == ECOPlannerFallbackReason.FAST_PATH || reason == ECOPlannerFallbackReason.OVERFLOW) {
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

    public record ClientNotice(
        long requestId,
        ECOPlannerFallbackReason reason,
        long elapsedNanos,
        String formattedBytes,
        List<ECOPlannerDiagnostic> diagnostics
    ) {
        public ClientNotice {
            diagnostics = List.copyOf(diagnostics);
        }

        public boolean overflow() {
            return reason == ECOPlannerFallbackReason.OVERFLOW && !formattedBytes.isEmpty();
        }
    }

    private static List<ECOPlannerDiagnostic> parseDiagnostics(String ids) {
        if (ids == null || ids.isBlank()) {
            return List.of();
        }
        List<ECOPlannerDiagnostic> result = new ArrayList<>();
        Arrays.stream(ids.split(","))
            .map(String::trim)
            .map(ECOPlannerDiagnostic::fromId)
            .filter(java.util.Objects::nonNull)
            .forEach(diagnostic -> {
                if (!result.contains(diagnostic)) {
                    result.add(diagnostic);
                }
            });
        return List.copyOf(result);
    }
}
