package cn.dancingsnow.neoecoae.network;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.NeoECOAE;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Synchronizes source deficits found specifically by the ECO planner. */
public record ECOPlanningMissingPayload(
    int containerId,
    long requestId,
    Map<AEKey, Long> missing
) implements CustomPacketPayload {
    public static final Type<ECOPlanningMissingPayload> TYPE =
        new Type<>(NeoECOAE.id("planning_missing"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ECOPlanningMissingPayload> STREAM_CODEC =
        StreamCodec.ofMember(ECOPlanningMissingPayload::write, ECOPlanningMissingPayload::read);
    private static final Map<Integer, ClientMissing> CLIENT_MISSING = new ConcurrentHashMap<>();

    public ECOPlanningMissingPayload {
        missing = Map.copyOf(missing);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, ECOPlanningMissingPayload::handle);
    }

    public static Optional<Map<AEKey, Long>> getClientMissing(int containerId) {
        return Optional.ofNullable(CLIENT_MISSING.get(containerId)).map(ClientMissing::missing);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarLong(requestId);
        buffer.writeVarInt(missing.size());
        missing.forEach((key, amount) -> {
            AEKey.writeKey(buffer, key);
            buffer.writeVarLong(amount);
        });
    }

    private static ECOPlanningMissingPayload read(RegistryFriendlyByteBuf buffer) {
        int containerId = buffer.readVarInt();
        long requestId = buffer.readVarLong();
        int count = buffer.readVarInt();
        Map<AEKey, Long> missing = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            AEKey key = AEKey.readKey(buffer);
            long amount = buffer.readVarLong();
            if (amount > 0L) {
                missing.put(key, amount);
            }
        }
        return new ECOPlanningMissingPayload(containerId, requestId, missing);
    }

    private static void handle(ECOPlanningMissingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu.containerId != payload.containerId()) {
                return;
            }
            ClientMissing previous = CLIENT_MISSING.get(payload.containerId());
            if (previous != null && previous.requestId() > payload.requestId()) {
                return;
            }
            CLIENT_MISSING.put(
                payload.containerId(),
                new ClientMissing(payload.requestId(), payload.missing())
            );
        });
    }

    private record ClientMissing(long requestId, Map<AEKey, Long> missing) {
    }
}
