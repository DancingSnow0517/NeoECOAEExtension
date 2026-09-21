package cn.dancingsnow.neoecoae.network;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import io.netty.buffer.Unpooled;
import java.math.BigInteger;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExactMapSyncTest {
    @Test void incrementalChangesAndRemovalsMatchCompleteSnapshots() {
        var first = Map.of("a", 10, "b", 20);
        var second = Map.of("a", 11, "c", 30);
        var delta = MapDelta.between(first, second);
        assertEquals(Map.of("a", 11, "c", 30), delta.updates());
        assertEquals(java.util.Set.of("b"), delta.removed());
        assertEquals(second, delta.apply(first));
        assertTrue(MapDelta.between(second, second).isEmpty());
        assertTrue(MapDelta.between(second, Map.<String, Integer>of()).apply(second).isEmpty());
    }

    @Test void binaryDeltaPreservesLargeInfiniteZeroAndDeletedQuantities() {
        AEKey a = mock(AEKey.class);
        AEKey b = mock(AEKey.class);
        AEKey c = mock(AEKey.class);
        var before = Map.of(a, ExactAmount.finite(BigInteger.ONE), b, ExactAmount.unbounded());
        var after = Map.of(a, ExactAmount.finite(BigInteger.TEN.pow(1023)), c, ExactAmount.unbounded());
        var keys = java.util.List.of(a, b, c);
        try (var statics = mockStatic(AEKey.class)) {
            statics.when(() -> AEKey.writeKey(any(), any())).thenAnswer(call -> {
                ((RegistryFriendlyByteBuf) call.getArgument(0)).writeVarInt(keys.indexOf(call.getArgument(1)));
                return null;
            });
            statics.when(() -> AEKey.readKey(any())).thenAnswer(call -> keys.get(
                ((RegistryFriendlyByteBuf) call.getArgument(0)).readVarInt()));
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                ExactMapSync.write(buf, MapDelta.between(before, after));
                assertTrue(buf.readableBytes() < 500, "1024 decimal digits should occupy roughly 426 binary bytes");
                assertEquals(after, ExactMapSync.read(buf).apply(before));
                assertFalse(buf.isReadable());
            } finally { buf.release(); }
        }
    }

    @Test void integerCodecRejectsNegativeEmptyAndOverLimitValues() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            assertThrows(IllegalArgumentException.class, () -> ExactMapSync.writeInteger(buf, BigInteger.valueOf(-1)));
            assertThrows(IllegalArgumentException.class, () -> ExactMapSync.writeInteger(buf, BigInteger.ONE.shiftLeft(32768)));
            ExactMapSync.writeInteger(buf, BigInteger.ZERO);
            assertEquals(BigInteger.ZERO, ExactMapSync.readInteger(buf));
            buf.writeByteArray(new byte[0]);
            assertThrows(IllegalArgumentException.class, () -> ExactMapSync.readInteger(buf));
        } finally { buf.release(); }
    }
}
