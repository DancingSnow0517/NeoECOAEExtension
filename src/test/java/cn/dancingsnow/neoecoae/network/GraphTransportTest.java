package cn.dancingsnow.neoecoae.network;

import static org.junit.jupiter.api.Assertions.*;

import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class GraphTransportTest {
    @Test
    void graphLargerThanAe2PacketLimitRoundTripsThroughBoundedParts() {
        var random = new Random(19);
        var ids = new ArrayList<ResourceLocation>();
        char[] chars = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
        for (int i = 0; i < 40_000; i++) {
            StringBuilder path = new StringBuilder();
            for (int j = 0; j < 128; j++) path.append(chars[random.nextInt(chars.length)]);
            ids.add(ResourceLocation.fromNamespaceAndPath("test", path.toString()));
        }
        var graph = new CraftingGraphSnapshot(
                -1, List.of(), List.of(), List.of(), List.of(), ids, CraftingGraphSnapshot.EMPTY.summary());
        byte[] encoded = BoundedData.encode(BoundedData.MAX_BYTES, graph::writeToPacket);
        assertTrue(encoded.length > 2 * 1024 * 1024, "Fixture must exceed the old AE2 limit");
        var receiver = new BoundedData.Receiver();
        byte[] received = null;
        for (int offset = 0; offset < encoded.length; offset += BoundedData.PART_BYTES) {
            int end = Math.min(encoded.length, offset + BoundedData.PART_BYTES);
            received = receiver.accept(encoded.length, offset, java.util.Arrays.copyOfRange(encoded, offset, end));
        }
        var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(received));
        try {
            assertEquals(graph, new CraftingGraphSnapshot(buffer));
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
        System.out.println("Compressed graph: " + encoded.length + " B, reconstructed without a >2 MiB packet");
    }
}
