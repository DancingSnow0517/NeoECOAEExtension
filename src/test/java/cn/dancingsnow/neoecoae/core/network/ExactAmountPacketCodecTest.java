package cn.dancingsnow.neoecoae.core.network;

import cn.dancingsnow.neoecoae.core.amount.ExactAmount;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExactAmountPacketCodecTest {
    @Test void finiteInfiniteAndRemovedKeysRoundTripOnAPlatformFreeBuffer() {
        var buf = new MemoryBuf();
        var updates = Map.of("finite", ExactAmount.finite(BigInteger.TEN.pow(1023)),
            "infinite", ExactAmount.unbounded(), "zero", ExactAmount.finite(BigInteger.ZERO));
        ExactAmountPacketCodec.write(buf, updates, Set.of("deleted"));
        var decoded = ExactAmountPacketCodec.read(buf);
        assertEquals(updates, decoded.updates());
        assertEquals(Set.of("deleted"), decoded.removed());
        assertTrue(buf.values.isEmpty());
    }

    @Test void invalidCountsAndIntegerPayloadsAreRejected() {
        var negativeCount = new MemoryBuf();
        negativeCount.writeVarInt(-1);
        assertThrows(IllegalArgumentException.class, () -> ExactAmountPacketCodec.read(negativeCount));
        var largeCount = new MemoryBuf();
        largeCount.writeVarInt(100_001);
        assertThrows(IllegalArgumentException.class, () -> ExactAmountPacketCodec.read(largeCount));
        var empty = new MemoryBuf();
        empty.writeBytes(new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> ExactAmountPacketCodec.readInteger(empty));
        var negative = new MemoryBuf();
        ExactAmountPacketCodec.writeSignedInteger(negative, BigInteger.valueOf(-7));
        assertThrows(IllegalArgumentException.class, () -> ExactAmountPacketCodec.readInteger(negative));
    }

    private static final class MemoryBuf implements PacketBuf<String> {
        private final Queue<Object> values = new ArrayDeque<>();
        @Override public void writeVarInt(int value) { values.add(value); }
        @Override public int readVarInt() { return (Integer) values.remove(); }
        @Override public void writeVarLong(long value) { values.add(value); }
        @Override public long readVarLong() { return (Long) values.remove(); }
        @Override public void writeUtf(String value) { values.add(value); }
        @Override public String readUtf() { return (String) values.remove(); }
        @Override public void writeBoolean(boolean value) { values.add(value); }
        @Override public boolean readBoolean() { return (Boolean) values.remove(); }
        @Override public void writeBytes(byte[] value) { values.add(value); }
        @Override public byte[] readBytes(int maximumLength) {
            var bytes = (byte[]) values.remove();
            if (bytes.length > maximumLength) throw new IllegalArgumentException("Too many bytes");
            return bytes;
        }
        @Override public void writeKey(String key) { values.add(key); }
        @Override public String readKey() { return (String) values.remove(); }
    }
}
