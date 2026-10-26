package cn.dancingsnow.neoecoae.network;

import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.impl.storage.bulk.BulkUnits;
import cn.dancingsnow.neoecoae.impl.storage.bulk.ECOCompressionService;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ECOCompressionSyncS2CPacket(int total, int offset, List<BulkUnits> chains) implements CustomPacketPayload {
    public static final int CHAINS_PER_PACKET = 32;
    private static List<BulkUnits> receiving = new ArrayList<>();
    private static int expectedTotal;
    public ECOCompressionSyncS2CPacket {
        chains = List.copyOf(chains);
        if (total < 0 || total > 65536 || offset < 0 || offset > total || chains.size() > CHAINS_PER_PACKET
                || chains.size() > total - offset || chains.isEmpty() && total != 0)
            throw new IllegalArgumentException("Invalid compression sync chunk");
    }
    public static final Type<ECOCompressionSyncS2CPacket> TYPE = new Type<>(
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(NeoECOAE.MOD_ID, "compression_chains"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ECOCompressionSyncS2CPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeVarInt(packet.total); buf.writeVarInt(packet.offset);
            buf.writeVarInt(packet.chains.size());
            for (BulkUnits chain : packet.chains) {
                buf.writeVarInt(chain.items().size());
                for (int i = 0; i < chain.items().size(); i++) {
                    ItemStack.STREAM_CODEC.encode(buf, chain.items().get(i).toStack());
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
                    keys.add(AEItemKey.of(ItemStack.STREAM_CODEC.decode(buf))); factors.add(new BigInteger(buf.readUtf()));
                }
                chains.add(new BulkUnits(keys, factors));
            }
            return new ECOCompressionSyncS2CPacket(total, offset, chains);
        });
    private static int size(int value) {
        if (value < 0 || value > 65536) throw new IllegalArgumentException("Invalid compression sync size");
        return value;
    }
    public static void handle(ECOCompressionSyncS2CPacket packet, IPayloadContext context) {
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
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
