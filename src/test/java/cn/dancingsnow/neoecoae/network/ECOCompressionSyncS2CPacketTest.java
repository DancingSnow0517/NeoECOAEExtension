package cn.dancingsnow.neoecoae.network;

import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.storage.bulk.BulkUnits;
import cn.dancingsnow.neoecoae.storage.bulk.ECOCompressionService;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import io.netty.buffer.Unpooled;
import java.math.BigInteger;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ECOCompressionSyncS2CPacketTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }
    @BeforeEach void reset() { ECOCompressionService.install(List.of()); }
    static BulkUnits iron() {
        return new BulkUnits(List.of(AEItemKey.of(Items.IRON_NUGGET), AEItemKey.of(Items.IRON_INGOT)),
            List.of(BigInteger.ONE, BigInteger.TEN.pow(40)));
    }
    static BulkUnits gold() {
        return new BulkUnits(List.of(AEItemKey.of(Items.GOLD_NUGGET), AEItemKey.of(Items.GOLD_INGOT)),
            List.of(BigInteger.ONE, BigInteger.valueOf(9)));
    }
    @Test void codecPreservesHugeFactorsAndChunkCoordinates() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var packet = new ECOCompressionSyncS2CPacket(2, 1, List.of(iron()));
            ECOCompressionSyncS2CPacket.STREAM_CODEC.encode(buffer, packet);
            assertEquals(packet, ECOCompressionSyncS2CPacket.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
    @Test void partialSnapshotIsInvisibleUntilTheLastChunkArrives() {
        var iron = iron(); var gold = gold();
        ECOCompressionSyncS2CPacket.accept(new ECOCompressionSyncS2CPacket(2, 0, List.of(iron)));
        assertFalse(ECOCompressionService.units(iron.items().get(0)).compressible());
        ECOCompressionSyncS2CPacket.accept(new ECOCompressionSyncS2CPacket(2, 1, List.of(gold)));
        assertEquals(iron, ECOCompressionService.units(iron.items().get(0)));
        assertEquals(gold, ECOCompressionService.units(gold.items().get(0)));
        ECOCompressionSyncS2CPacket.accept(new ECOCompressionSyncS2CPacket(0, 0, List.of()));
        assertFalse(ECOCompressionService.units(iron.items().get(0)).compressible());
    }
    @Test void invalidOrOutOfOrderChunksDoNotReplaceTheActiveSnapshot() {
        var iron = iron(); ECOCompressionService.install(List.of(iron));
        ECOCompressionSyncS2CPacket.accept(new ECOCompressionSyncS2CPacket(2, 0, List.of(gold())));
        assertThrows(IllegalArgumentException.class, () -> ECOCompressionSyncS2CPacket.accept(new ECOCompressionSyncS2CPacket(3, 1, List.of(iron))));
        assertEquals(iron, ECOCompressionService.units(iron.items().get(0)));
        assertThrows(IllegalArgumentException.class, () -> new ECOCompressionSyncS2CPacket(1, 1, List.of(iron)));
    }
}
