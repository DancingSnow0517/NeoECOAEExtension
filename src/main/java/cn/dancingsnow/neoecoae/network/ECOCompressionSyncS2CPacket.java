package cn.dancingsnow.neoecoae.network;

import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.storage.bulk.BulkUnits;
import cn.dancingsnow.neoecoae.storage.bulk.ECOCompressionService;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import cn.dancingsnow.neoecoae.platform.network.NEPacketContext;
import cn.dancingsnow.neoecoae.platform.network.NEStreamCodec;
import net.minecraft.network.FriendlyByteBuf;

public record ECOCompressionSyncS2CPacket(int total, int offset, List<BulkUnits> chains) {
    public static final int CHAINS_PER_PACKET = 32;
    private static List<BulkUnits> receiving = new ArrayList<>();
    private static int expectedTotal;
    public ECOCompressionSyncS2CPacket {
        chains = List.copyOf(chains);
        if (total < 0 || total > 65536 || offset < 0 || offset > total || chains.size() > CHAINS_PER_PACKET
                || chains.size() > total - offset || chains.isEmpty() && total != 0)
            throw new IllegalArgumentException("Invalid compression sync chunk");
    }
    public static final NEStreamCodec<FriendlyByteBuf, ECOCompressionSyncS2CPacket> STREAM_CODEC = NEStreamCodec.of(
        (buf, packet) -> {
            buf.writeVarInt(packet.total); buf.writeVarInt(packet.offset);
            buf.writeVarInt(packet.chains.size());
            for (BulkUnits chain : packet.chains) {
                buf.writeVarInt(chain.items().size());
                for (int i = 0; i < chain.items().size(); i++) {
                    buf.writeItem(chain.items().get(i).toStack());
                    buf.writeUtf(chain.factors().get(i).toString());
                }
            }
        }, buf -> {
            int total = size(buf.readVarInt()), offset = size(buf.readVarInt());
            int count = size(buf.readVarInt());
            if (count > CHAINS_PER_PACKET) throw new IllegalArgumentException("Oversized compression chunk");
            List<BulkUnits> chains = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int variants = size(buf.readVarInt());
                List<AEItemKey> keys = new ArrayList<>(); List<BigInteger> factors = new ArrayList<>();
                for (int j = 0; j < variants; j++) {
                    keys.add(AEItemKey.of(buf.readItem())); factors.add(new BigInteger(buf.readUtf()));
                }
                chains.add(new BulkUnits(keys, factors));
            }
            return new ECOCompressionSyncS2CPacket(total, offset, chains);
        });
    private static int size(int value) {
        if (value < 0 || value > 65536) throw new IllegalArgumentException("Invalid compression sync size");
        return value;
    }
    public static void handle(ECOCompressionSyncS2CPacket packet, NEPacketContext context) {
        context.enqueueWork(() -> accept(packet));
    }
    // Play packets are ordered. Publish only a complete snapshot, never an intermediate chain list.
    static void accept(ECOCompressionSyncS2CPacket packet) {
        if (packet.offset == 0) { receiving = new ArrayList<>(); expectedTotal = packet.total; }
        if (packet.total != expectedTotal || packet.offset != receiving.size())
            throw new IllegalArgumentException("Out-of-order compression sync chunk");
        receiving.addAll(packet.chains);
        if (receiving.size() == expectedTotal) {
            ECOCompressionService.install(receiving);
            receiving = new ArrayList<>();
        }
    }
}
