package cn.dancingsnow.neoecoae.network;

import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ECOMenuChunkS2CPacket(int containerId, MenuDataTransport.Channel channel,
        long transfer, int total, int offset, byte[] bytes) implements CustomPacketPayload {
    public static final int CHUNK_BYTES = 16 * 1024;
    public static final Type<ECOMenuChunkS2CPacket> TYPE = new Type<>(
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(NeoECOAE.MOD_ID, "menu_chunk"));
    public static final StreamCodec<FriendlyByteBuf, ECOMenuChunkS2CPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeVarInt(packet.containerId);
            buf.writeEnum(packet.channel);
            buf.writeVarLong(packet.transfer);
            buf.writeVarInt(packet.total);
            buf.writeVarInt(packet.offset);
            buf.writeByteArray(packet.bytes);
        }, buf -> new ECOMenuChunkS2CPacket(buf.readVarInt(), buf.readEnum(MenuDataTransport.Channel.class),
            buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(CHUNK_BYTES)));

    public static void handle(ECOMenuChunkS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> MenuDataTransport.receive(context.player(), packet));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
