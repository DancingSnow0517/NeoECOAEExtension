package cn.dancingsnow.neoecoae.core.network;

import cn.dancingsnow.neoecoae.core.amount.ExactAmount;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Exact quantities and deltas without a dependency on Minecraft buffers or AE keys. */
public final class ExactAmountPacketCodec {
    private static final int MAX_ENTRIES = 100_000;
    private static final int MAX_INTEGER_BYTES = 4096;
    private ExactAmountPacketCodec() {}

    public record Delta<K>(Map<K, ExactAmount> updates, Set<K> removed) {}

    public static <K> void write(PacketBuf<K> buf, Map<K, ExactAmount> updates, Set<K> removed) {
        if (updates.size() > MAX_ENTRIES || removed.size() > MAX_ENTRIES)
            throw new IllegalArgumentException("Too many exact amount entries");
        buf.writeVarInt(updates.size());
        updates.forEach((key, value) -> {
            buf.writeKey(key);
            buf.writeBoolean(value.infinite());
            if (!value.infinite()) writeInteger(buf, value.value());
        });
        buf.writeVarInt(removed.size());
        removed.forEach(buf::writeKey);
    }

    public static <K> Delta<K> read(PacketBuf<K> buf) {
        Map<K, ExactAmount> updates = new HashMap<>();
        int size = readCount(buf);
        for (int i = 0; i < size; i++) {
            K key = buf.readKey();
            updates.put(key, buf.readBoolean() ? ExactAmount.unbounded() : ExactAmount.finite(readInteger(buf)));
        }
        Set<K> removed = new HashSet<>();
        size = readCount(buf);
        for (int i = 0; i < size; i++) removed.add(buf.readKey());
        return new Delta<>(updates, removed);
    }

    public static void writeInteger(PacketBuf<?> buf, BigInteger value) {
        if (value.signum() < 0) throw new IllegalArgumentException("Negative amount");
        writeSignedInteger(buf, value);
    }

    public static BigInteger readInteger(PacketBuf<?> buf) {
        BigInteger value = readSignedInteger(buf);
        if (value.signum() < 0) throw new IllegalArgumentException("Negative amount");
        return value;
    }

    public static void writeSignedInteger(PacketBuf<?> buf, BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > MAX_INTEGER_BYTES) throw new IllegalArgumentException("Integer is too large");
        buf.writeBytes(bytes);
    }

    public static BigInteger readSignedInteger(PacketBuf<?> buf) {
        byte[] bytes = buf.readBytes(MAX_INTEGER_BYTES);
        if (bytes.length == 0) throw new IllegalArgumentException("Empty amount");
        return new BigInteger(bytes);
    }

    private static int readCount(PacketBuf<?> buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) throw new IllegalArgumentException("Invalid exact amount count");
        return size;
    }
}
