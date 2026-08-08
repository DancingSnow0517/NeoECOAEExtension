package cn.dancingsnow.neoecoae.network;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOCyclePlanningDiagnostics;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Client-visible statistics for the selected cyclic planning path. */
public record ECOCycleDiagnosticsPayload(int containerId, ECOCyclePlanningDiagnostics diagnostics) {
    private static final Map<Integer, ECOCyclePlanningDiagnostics> CLIENT_DIAGNOSTICS = new ConcurrentHashMap<>();

    public ECOCycleDiagnosticsPayload {
        diagnostics = diagnostics == null ? ECOCyclePlanningDiagnostics.EMPTY : diagnostics;
    }

    public static void encode(ECOCycleDiagnosticsPayload payload, FriendlyByteBuf buffer) {
        buffer.writeVarInt(payload.containerId);
        buffer.writeVarInt(payload.diagnostics.materials().size());
        payload.diagnostics.materials().forEach((key, stats) -> {
            AEKey.writeKey(buffer, key);
            buffer.writeVarLong(stats.initial());
            buffer.writeVarLong(stats.consumed());
            buffer.writeVarLong(stats.produced());
            buffer.writeVarLong(stats.remaining());
        });
        buffer.writeVarInt(payload.diagnostics.missingSeeds().size());
        payload.diagnostics.missingSeeds().forEach((key, amount) -> {
            AEKey.writeKey(buffer, key);
            buffer.writeVarLong(amount);
        });
    }

    public static ECOCycleDiagnosticsPayload decode(FriendlyByteBuf buffer) {
        int containerId = buffer.readVarInt();
        Map<AEKey, ECOCyclePlanningDiagnostics.MaterialStats> materials = new LinkedHashMap<>();
        int materialCount = buffer.readVarInt();
        for (int i = 0; i < materialCount; i++) {
            materials.put(
                    AEKey.readKey(buffer),
                    new ECOCyclePlanningDiagnostics.MaterialStats(
                            buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong()));
        }
        Map<AEKey, Long> missingSeeds = new LinkedHashMap<>();
        int missingCount = buffer.readVarInt();
        for (int i = 0; i < missingCount; i++) {
            missingSeeds.put(AEKey.readKey(buffer), buffer.readVarLong());
        }
        return new ECOCycleDiagnosticsPayload(containerId, new ECOCyclePlanningDiagnostics(materials, missingSeeds));
    }

    public static void handle(ECOCycleDiagnosticsPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (payload.diagnostics.isEmpty()) {
                CLIENT_DIAGNOSTICS.remove(payload.containerId);
            } else {
                CLIENT_DIAGNOSTICS.put(payload.containerId, payload.diagnostics);
            }
        });
        context.setPacketHandled(true);
    }

    public static Optional<ECOCyclePlanningDiagnostics> getClientDiagnostics(int containerId) {
        return Optional.ofNullable(CLIENT_DIAGNOSTICS.get(containerId));
    }

    public static void clearClientDiagnostics(int containerId) {
        CLIENT_DIAGNOSTICS.remove(containerId);
    }
}
