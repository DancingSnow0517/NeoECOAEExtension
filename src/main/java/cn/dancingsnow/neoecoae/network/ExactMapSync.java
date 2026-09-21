package cn.dancingsnow.neoecoae.network;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Shared binary exact-amount delta codec. The enclosing stream provides atomic delivery. */
public final class ExactMapSync {
    private static final int MAX_ENTRIES = 100_000;
    private static final int MAX_INTEGER_BYTES = 4096;
    private ExactMapSync() {}

    public static void write(RegistryFriendlyByteBuf buf, MapDelta<AEKey, ExactAmount> delta) {
        if (delta.updates().size() > MAX_ENTRIES || delta.removed().size() > MAX_ENTRIES)
            throw new IllegalArgumentException("Too many exact amount entries");
        buf.writeVarInt(delta.updates().size());
        delta.updates().forEach((key, value) -> {
            AEKey.writeKey(buf, key);
            buf.writeBoolean(value.infinite());
            if (!value.infinite()) writeInteger(buf, value.value());
        });
        buf.writeVarInt(delta.removed().size());
        delta.removed().forEach(key -> AEKey.writeKey(buf, key));
    }

    public static MapDelta<AEKey, ExactAmount> read(RegistryFriendlyByteBuf buf) {
        Map<AEKey, ExactAmount> updates = new HashMap<>();
        int size = readCount(buf);
        for (int i = 0; i < size; i++) {
            AEKey key = AEKey.readKey(buf);
            updates.put(key, buf.readBoolean() ? ExactAmount.unbounded() : ExactAmount.finite(readInteger(buf)));
        }
        var removed = new HashSet<AEKey>();
        size = readCount(buf);
        for (int i = 0; i < size; i++) removed.add(AEKey.readKey(buf));
        return new MapDelta<>(updates, removed);
    }

    public static void writeInteger(net.minecraft.network.FriendlyByteBuf buf, BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (value.signum() < 0 || bytes.length > MAX_INTEGER_BYTES) throw new IllegalArgumentException("Invalid amount");
        buf.writeByteArray(bytes);
    }

    public static BigInteger readInteger(net.minecraft.network.FriendlyByteBuf buf) {
        byte[] bytes = buf.readByteArray(MAX_INTEGER_BYTES);
        if (bytes.length == 0) throw new IllegalArgumentException("Empty amount");
        BigInteger value = new BigInteger(bytes);
        if (value.signum() < 0) throw new IllegalArgumentException("Negative amount");
        return value;
    }

    public static Map<AEKey, ExactAmount> finite(Map<AEKey, BigInteger> amounts) {
        Map<AEKey, ExactAmount> result = new HashMap<>();
        amounts.forEach((key, value) -> result.put(key, ExactAmount.finite(value)));
        return Map.copyOf(result);
    }

    public static Map<AEKey, BigInteger> integers(Map<AEKey, ExactAmount> amounts) {
        Map<AEKey, BigInteger> result = new HashMap<>();
        amounts.forEach((key, value) -> {
            if (value.infinite()) throw new IllegalArgumentException("Infinite CPU quantity");
            result.put(key, value.value());
        });
        return Map.copyOf(result);
    }

    private static int readCount(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) throw new IllegalArgumentException("Invalid exact amount count");
        return size;
    }
}
