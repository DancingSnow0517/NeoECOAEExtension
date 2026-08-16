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

/** Synchronizes a live, post-submission input-deficit audit to the crafting confirmation screen. */
public record ECOSubmissionMissingPayload(int containerId, Map<AEKey, Long> missing) implements CustomPacketPayload {
    public static final Type<ECOSubmissionMissingPayload> TYPE =
        new Type<>(NeoECOAE.id("submission_missing"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ECOSubmissionMissingPayload> STREAM_CODEC =
        StreamCodec.ofMember(ECOSubmissionMissingPayload::write, ECOSubmissionMissingPayload::read);
    private static final Map<Integer, Map<AEKey, Long>> CLIENT_MISSING = new ConcurrentHashMap<>();

    public ECOSubmissionMissingPayload {
        missing = Map.copyOf(missing);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, ECOSubmissionMissingPayload::handle);
    }

    public static Optional<Map<AEKey, Long>> getClientMissing(int containerId) {
        return Optional.ofNullable(CLIENT_MISSING.get(containerId));
    }

    public static void clearClientMissing(int containerId) {
        CLIENT_MISSING.remove(containerId);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(missing.size());
        missing.forEach((key, amount) -> {
            AEKey.writeKey(buffer, key);
            buffer.writeVarLong(amount);
        });
    }

    private static ECOSubmissionMissingPayload read(RegistryFriendlyByteBuf buffer) {
        int containerId = buffer.readVarInt();
        int count = buffer.readVarInt();
        Map<AEKey, Long> missing = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            AEKey key = AEKey.readKey(buffer);
            long amount = buffer.readVarLong();
            if (amount > 0L) {
                missing.put(key, amount);
            }
        }
        return new ECOSubmissionMissingPayload(containerId, missing);
    }

    private static void handle(ECOSubmissionMissingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu.containerId != payload.containerId()) {
                return;
            }
            if (payload.missing().isEmpty()) {
                clearClientMissing(payload.containerId());
            } else {
                CLIENT_MISSING.put(payload.containerId(), payload.missing());
            }
        });
    }
}
