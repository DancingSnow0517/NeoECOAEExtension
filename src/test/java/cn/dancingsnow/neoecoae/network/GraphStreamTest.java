package cn.dancingsnow.neoecoae.network;

import cn.dancingsnow.neoecoae.api.me.menu.ECOCycleItemList;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphStreamTest {
    @Test void compressedGraphAndCycleRowsRoundTripAcrossManyChunks() {
        List<CraftingGraphSnapshot.PatternNode> patterns = new ArrayList<>();
        Random random = new Random(43);
        for (int i = 0; i < 4000; i++) patterns.add(new CraftingGraphSnapshot.PatternNode(i,
            Long.toHexString(random.nextLong()) + Long.toHexString(random.nextLong()), List.of(), List.of(),
            5L, CraftingGraphSnapshot.CandidateStatus.SELECTED, null, -1));
        var graph = new CraftingGraphSnapshot(-1, List.of(), patterns, List.of(), List.of(), List.of(),
            new CraftingGraphSnapshot.Summary("SUCCESS", 0, patterns.size(), 0, 0, 123));
        var encoded = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            graph.writeToPacket(encoded);
            ECOCycleItemList.EMPTY.writeToPacket(encoded);
            byte[] bytes = new byte[encoded.readableBytes()];
            encoded.readBytes(bytes);
            assertTrue(bytes.length > 16_384);
            var assembler = new MenuStreamAssembler();
            byte[] completed = null;
            for (int offset = 0; offset < bytes.length; offset += 16_384)
                completed = assembler.accept(1, bytes.length, offset,
                    Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + 16_384)));
            assertNotNull(completed);
            var received = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(completed), RegistryAccess.EMPTY);
            try {
                assertEquals(graph, new CraftingGraphSnapshot(received));
                assertEquals(ECOCycleItemList.EMPTY, new ECOCycleItemList(received));
                assertFalse(received.isReadable());
            } finally { received.release(); }
        } finally { encoded.release(); }
    }
}
