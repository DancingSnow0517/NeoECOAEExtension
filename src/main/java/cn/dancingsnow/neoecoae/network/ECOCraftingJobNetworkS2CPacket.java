package cn.dancingsnow.neoecoae.network;

import cn.dancingsnow.neoecoae.NeoECOAE;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Associates a crafting job status notification with the runtime ME network that started it. */
public record ECOCraftingJobNetworkS2CPacket(UUID jobId, long networkId) implements CustomPacketPayload {
    public static final Type<ECOCraftingJobNetworkS2CPacket> TYPE =
        new Type<>(NeoECOAE.id("crafting_job_network"));

    public static final StreamCodec<FriendlyByteBuf, ECOCraftingJobNetworkS2CPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeUUID(packet.jobId);
            buf.writeVarLong(packet.networkId);
        },
        buf -> new ECOCraftingJobNetworkS2CPacket(buf.readUUID(), buf.readVarLong())
    );

    private static volatile BiConsumer<UUID, Long> clientHandler = (jobId, networkId) -> {};

    public static void setClientHandler(BiConsumer<UUID, Long> handler) {
        clientHandler = handler;
    }

    public static void handle(ECOCraftingJobNetworkS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> clientHandler.accept(packet.jobId(), packet.networkId()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
