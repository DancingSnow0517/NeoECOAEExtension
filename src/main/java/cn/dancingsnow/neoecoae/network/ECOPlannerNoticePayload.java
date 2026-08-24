package cn.dancingsnow.neoecoae.network;

import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerFallbackReason;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Client-visible result of the most recent ECO planning request for one menu. */
public record ECOPlannerNoticePayload(int containerId, String reasonId, long elapsedNanos, String formattedBytes) {
    private static final Map<Integer, ClientNotice> CLIENT_NOTICES = new ConcurrentHashMap<>();

    public ECOPlannerNoticePayload {
        reasonId = reasonId == null ? ECOPlannerFallbackReason.PLANNING_FAILURE.id() : reasonId;
        formattedBytes = formattedBytes == null ? "" : formattedBytes;
    }

    public static void encode(ECOPlannerNoticePayload payload, FriendlyByteBuf buffer) {
        buffer.writeVarInt(payload.containerId);
        buffer.writeUtf(payload.reasonId);
        buffer.writeVarLong(payload.elapsedNanos);
        buffer.writeUtf(payload.formattedBytes);
    }

    public static ECOPlannerNoticePayload decode(FriendlyByteBuf buffer) {
        return new ECOPlannerNoticePayload(
                buffer.readVarInt(), buffer.readUtf(), buffer.readVarLong(), buffer.readUtf());
    }

    public static void handle(ECOPlannerNoticePayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> CLIENT_NOTICES.put(
                payload.containerId,
                new ClientNotice(
                        ECOPlannerFallbackReason.fromId(payload.reasonId),
                        Math.max(0L, payload.elapsedNanos),
                        payload.formattedBytes)));
        context.setPacketHandled(true);
    }

    public static Optional<ClientNotice> getClientNoticeData(int containerId) {
        return Optional.ofNullable(CLIENT_NOTICES.get(containerId));
    }

    public static void clearClientNotice(int containerId) {
        CLIENT_NOTICES.remove(containerId);
    }

    /** Drops all cached notices; called on login because container ids restart at 1. */
    public static void clearAllClientNotices() {
        CLIENT_NOTICES.clear();
    }

    public record ClientNotice(ECOPlannerFallbackReason reason, long elapsedNanos, String formattedBytes) {
        public boolean overflow() {
            return reason == ECOPlannerFallbackReason.OVERFLOW && !formattedBytes.isEmpty();
        }
    }
}
