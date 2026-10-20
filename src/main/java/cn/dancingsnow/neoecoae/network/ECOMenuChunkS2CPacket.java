package cn.dancingsnow.neoecoae.network;

import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.network.FriendlyByteBuf;
import cn.dancingsnow.neoecoae.platform.network.NEStreamCodec;
import cn.dancingsnow.neoecoae.platform.network.NEPacketContext;

public record ECOMenuChunkS2CPacket(int containerId, MenuDataTransport.Channel channel,
        long transfer, int total, int offset, byte[] bytes) {
    public static final int CHUNK_BYTES = 16 * 1024;

    public static final NEStreamCodec<FriendlyByteBuf, ECOMenuChunkS2CPacket> STREAM_CODEC = NEStreamCodec.of(
        (buf, packet) -> {
            buf.writeVarInt(packet.containerId);
            buf.writeEnum(packet.channel);
            buf.writeVarLong(packet.transfer);
            buf.writeVarInt(packet.total);
            buf.writeVarInt(packet.offset);
            buf.writeByteArray(packet.bytes);
        }, buf -> new ECOMenuChunkS2CPacket(buf.readVarInt(), buf.readEnum(MenuDataTransport.Channel.class),
            buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(CHUNK_BYTES)));

    public static void handle(ECOMenuChunkS2CPacket packet, NEPacketContext context) {
        context.enqueueWork(() -> MenuDataTransport.receive(context.player(), packet));
    }

}
