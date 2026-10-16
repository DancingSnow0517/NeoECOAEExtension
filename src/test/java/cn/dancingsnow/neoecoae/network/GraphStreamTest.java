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
    @Test void abstractBufferPreservesLegacyUtfMaterialFieldsAndEnumOrdinals() throws Exception {
        var key = org.mockito.Mockito.mock(appeng.api.stacks.AEKey.class);
        var material = new CraftingGraphSnapshot.MaterialNode(7, key, 1, 2, 3, 4,
            CraftingGraphSnapshot.MaterialStatus.CYCLE, "9223372036854775808", "2", "3", "4", "5", "6");
        var graph = new CraftingGraphSnapshot(7, List.of(material), List.of(), List.of(), List.of(), List.of(),
            new CraftingGraphSnapshot.Summary("SUCCESS", 1, 0, 0, 0, 123));
        try (var keys = org.mockito.Mockito.mockStatic(appeng.api.stacks.AEKey.class)) {
            keys.when(() -> appeng.api.stacks.AEKey.writeKey(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(key))).thenAnswer(call -> {
                    ((RegistryFriendlyByteBuf) call.getArgument(0)).writeVarInt(42);
                    return null;
                });
            var encoded = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                graph.writeToPacket(encoded);
                int length = encoded.readVarInt();
                byte[] compressed = encoded.readByteArray();
                byte[] rawBytes;
                try (var inflater = new java.util.zip.InflaterInputStream(new java.io.ByteArrayInputStream(compressed))) {
                    rawBytes = inflater.readAllBytes();
                }
                assertEquals(length, rawBytes.length);
                var raw = new net.minecraft.network.FriendlyByteBuf(Unpooled.wrappedBuffer(rawBytes));
                try {
                    assertEquals(7, raw.readVarInt());
                    assertEquals(1, raw.readVarInt());
                    assertEquals(7, raw.readVarInt());
                    assertEquals(42, raw.readVarInt());
                    for (long value = 1; value <= 4; value++) assertEquals(value, raw.readVarLong());
                    assertEquals(CraftingGraphSnapshot.MaterialStatus.CYCLE.ordinal(), raw.readVarInt());
                    for (String text : List.of("9223372036854775808", "2", "3", "4", "5", "6"))
                        assertEquals(text, raw.readUtf());
                    for (int n = 0; n < 4; n++) assertEquals(0, raw.readVarInt());
                    assertEquals("SUCCESS", raw.readUtf());
                    assertEquals(1, raw.readVarInt());
                    for (int n = 0; n < 3; n++) assertEquals(0, raw.readVarInt());
                    assertEquals(123, raw.readVarLong());
                    assertFalse(raw.isReadable());
                } finally { raw.release(); }
            } finally { encoded.release(); }
        }
    }

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
