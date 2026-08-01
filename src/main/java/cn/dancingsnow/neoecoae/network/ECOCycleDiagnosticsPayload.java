package cn.dancingsnow.neoecoae.network;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOCyclePlanningDiagnostics;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Synchronizes cycle-only planning details to the active AE2 crafting confirmation screen. */
public record ECOCycleDiagnosticsPayload(
    int containerId,
    ECOCyclePlanningDiagnostics diagnostics
) implements CustomPacketPayload {
    public static final Type<ECOCycleDiagnosticsPayload> TYPE = new Type<>(NeoECOAE.id("cycle_diagnostics"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ECOCycleDiagnosticsPayload> STREAM_CODEC =
        StreamCodec.ofMember(ECOCycleDiagnosticsPayload::write, ECOCycleDiagnosticsPayload::read);
    private static final Map<Integer, ECOCyclePlanningDiagnostics> CLIENT_DIAGNOSTICS = new ConcurrentHashMap<>();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, ECOCycleDiagnosticsPayload::handle);
    }

    public static Optional<ECOCyclePlanningDiagnostics> getClientDiagnostics(int containerId) {
        return Optional.ofNullable(CLIENT_DIAGNOSTICS.get(containerId));
    }

    public static void clearClientDiagnostics(int containerId) {
        CLIENT_DIAGNOSTICS.remove(containerId);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(diagnostics.materials().size());
        diagnostics.materials().forEach((key, stats) -> {
            AEKey.writeKey(buffer, key);
            buffer.writeVarLong(stats.initial());
            buffer.writeVarLong(stats.consumed());
            buffer.writeVarLong(stats.produced());
            buffer.writeVarLong(stats.remaining());
        });
        buffer.writeVarInt(diagnostics.missingSeeds().size());
        diagnostics.missingSeeds().forEach((key, amount) -> {
            AEKey.writeKey(buffer, key);
            buffer.writeVarLong(amount);
        });
    }

    private static ECOCycleDiagnosticsPayload read(RegistryFriendlyByteBuf buffer) {
        int containerId = buffer.readVarInt();
        Map<AEKey, ECOCyclePlanningDiagnostics.MaterialStats> materials = new LinkedHashMap<>();
        int materialCount = buffer.readVarInt();
        for (int i = 0; i < materialCount; i++) {
            materials.put(AEKey.readKey(buffer), new ECOCyclePlanningDiagnostics.MaterialStats(
                buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong()));
        }
        Map<AEKey, Long> missingSeeds = new LinkedHashMap<>();
        int missingCount = buffer.readVarInt();
        for (int i = 0; i < missingCount; i++) {
            missingSeeds.put(AEKey.readKey(buffer), buffer.readVarLong());
        }
        return new ECOCycleDiagnosticsPayload(
            containerId, new ECOCyclePlanningDiagnostics(materials, missingSeeds));
    }

    private static void handle(ECOCycleDiagnosticsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu.containerId != payload.containerId()) {
                return;
            }
            if (payload.diagnostics().isEmpty()) {
                clearClientDiagnostics(payload.containerId());
            } else {
                CLIENT_DIAGNOSTICS.put(payload.containerId(), payload.diagnostics());
            }
        });
    }
}
