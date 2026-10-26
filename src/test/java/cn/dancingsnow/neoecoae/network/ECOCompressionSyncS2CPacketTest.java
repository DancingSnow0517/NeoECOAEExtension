package cn.dancingsnow.neoecoae.network;

import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.impl.storage.bulk.BulkUnits;
import cn.dancingsnow.neoecoae.impl.storage.bulk.ECOCompressionService;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import io.netty.buffer.Unpooled;
import java.math.BigInteger;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import net.neoforged.neoforge.registries.RegistryManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

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
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        try (var sync = mockStatic(RegistryManager.class)) {
            var packet = new ECOCompressionSyncS2CPacket(2, 1, List.of(iron()));
            ECOCompressionSyncS2CPacket.STREAM_CODEC.encode(buffer, packet);
            assertEquals(packet, ECOCompressionSyncS2CPacket.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
    @Test void partialSnapshotIsInvisibleUntilTheLastChunkArrives() {
        var iron = iron(); var gold = gold();
        ECOCompressionSyncS2CPacket.accept(new ECOCompressionSyncS2CPacket(2, 0, List.of(iron)));
        assertFalse(ECOCompressionService.units(iron.items().getFirst()).compressible());
        ECOCompressionSyncS2CPacket.accept(new ECOCompressionSyncS2CPacket(2, 1, List.of(gold)));
        assertEquals(iron, ECOCompressionService.units(iron.items().getFirst()));
        assertEquals(gold, ECOCompressionService.units(gold.items().getFirst()));
        ECOCompressionSyncS2CPacket.accept(new ECOCompressionSyncS2CPacket(0, 0, List.of()));
        assertFalse(ECOCompressionService.units(iron.items().getFirst()).compressible());
    }
    @Test void invalidOrOutOfOrderChunksDoNotReplaceTheActiveSnapshot() {
        var iron = iron(); ECOCompressionService.install(List.of(iron));
        ECOCompressionSyncS2CPacket.accept(new ECOCompressionSyncS2CPacket(2, 0, List.of(gold())));
        assertThrows(IllegalArgumentException.class, () -> ECOCompressionSyncS2CPacket.accept(new ECOCompressionSyncS2CPacket(3, 1, List.of(iron))));
        assertEquals(iron, ECOCompressionService.units(iron.items().getFirst()));
        assertThrows(IllegalArgumentException.class, () -> new ECOCompressionSyncS2CPacket(1, 1, List.of(iron)));
    }
}
