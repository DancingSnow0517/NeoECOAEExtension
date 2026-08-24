package cn.dancingsnow.neoecoae.network;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Serial-to-overlay-texture mapping for the AE2 crafting CPU selection list.
 *
 * <p>This travels through NeoECOAE's own channel instead of being appended to AE2's GuiSync
 * frame for {@code CraftingStatusMenu.cpuList}. AE2's GUI sync stream has no per-field length
 * prefix, so any foreign byte appended there desynchronizes the whole packet as soon as reader
 * and writer disagree — observed as "Server sent update for GUI field N" log spam whose numbers
 * decode (hex + UTF-8) to the texture path itself, with the CPU list failing to render. Keeping
 * this data out-of-band removes that failure mode entirely.
 */
public record ECOCpuOverlayPayload(int containerId, Map<Integer, String> overlays) {
    /** Client-side cache keyed by containerId, mirroring {@link ECOPlannerNoticePayload}. */
    private static final Map<Integer, Map<Integer, String>> CLIENT_OVERLAYS = new ConcurrentHashMap<>();

    public ECOCpuOverlayPayload {
        overlays = overlays == null ? Map.of() : Map.copyOf(overlays);
    }

    public static void encode(ECOCpuOverlayPayload payload, FriendlyByteBuf buffer) {
        buffer.writeVarInt(payload.containerId);
        buffer.writeVarInt(payload.overlays.size());
        for (var entry : payload.overlays.entrySet()) {
            buffer.writeVarInt(entry.getKey());
            buffer.writeUtf(entry.getValue());
        }
    }

    public static ECOCpuOverlayPayload decode(FriendlyByteBuf buffer) {
        int containerId = buffer.readVarInt();
        int count = buffer.readVarInt();
        var overlays = new HashMap<Integer, String>(count);
        for (int i = 0; i < count; i++) {
            int serial = buffer.readVarInt();
            String texture = buffer.readUtf();
            if (!texture.isEmpty()) {
                overlays.put(serial, texture);
            }
        }
        return new ECOCpuOverlayPayload(containerId, overlays);
    }

    public static void handle(ECOCpuOverlayPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> CLIENT_OVERLAYS.put(payload.containerId, payload.overlays()));
        context.setPacketHandled(true);
    }

    /** Returns the overlay texture registered for the given CPU serial on this menu, or null. */
    public static String getClientOverlay(int containerId, int serial) {
        var overlays = CLIENT_OVERLAYS.get(containerId);
        return overlays != null ? overlays.get(serial) : null;
    }

    public static void clearClientOverlays(int containerId) {
        CLIENT_OVERLAYS.remove(containerId);
    }

    /** Drops all cached overlays; called on login because container ids restart at 1. */
    public static void clearClientOverlays() {
        CLIENT_OVERLAYS.clear();
    }
}
