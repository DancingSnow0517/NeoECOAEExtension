package cn.dancingsnow.neoecoae.impl.storage.infinite;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Partitions inventory records without letting one invalid record hide unrelated resources. */
public final class InfiniteStorageEntries {
    public record Entry<K>(K key, CompoundTag encodedKey, HugeAmount amount) {}

    public record Result<K>(
            List<Entry<K>> available, List<CompoundTag> retained, List<String> failures, Set<K> blockedKeys) {}

    private InfiniteStorageEntries() {}

    static <K> Result<K> read(ListTag entries, Function<CompoundTag, K> decode) {
        return read(entries, decode, InfiniteStorageEntries::amount);
    }

    public static <K> Result<K> readOrdinary(ListTag entries, Function<CompoundTag, K> decode) {
        return read(entries, decode, entry -> {
            if (!entry.contains("amount", Tag.TAG_LONG) || entry.getLong("amount") <= 0L) {
                throw new IllegalArgumentException("Invalid ordinary cell amount");
            }
            return HugeAmount.of(entry.getLong("amount"));
        });
    }

    private static <K> Result<K> read(
            ListTag entries, Function<CompoundTag, K> decode, Function<CompoundTag, HugeAmount> readAmount) {
        Object2IntOpenHashMap<CompoundTag> encodedCounts = new Object2IntOpenHashMap<>();
        Object2IntOpenHashMap<K> keyCounts = new Object2IntOpenHashMap<>();
        encodedCounts.defaultReturnValue(0);
        keyCounts.defaultReturnValue(0);
        List<K> keys = new ArrayList<>();
        for (Tag raw : entries) {
            CompoundTag entry = (CompoundTag) raw;
            K key = null;
            if (entry.contains("key", Tag.TAG_COMPOUND)) {
                CompoundTag encoded = entry.getCompound("key");
                encodedCounts.merge(encoded, 1, Integer::sum);
                try {
                    key = decode.apply(encoded);
                } catch (RuntimeException ignored) {
                    // Unresolved keys are retained by the caller; the remaining records are independent.
                }
                if (key != null) keyCounts.merge(key, 1, Integer::sum);
            }
            keys.add(key);
        }
        List<Entry<K>> available = new ArrayList<>();
        List<CompoundTag> retained = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        Set<K> blocked = new ObjectOpenHashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            K key = keys.get(i);
            try {
                if (!entry.contains("key", Tag.TAG_COMPOUND)) {
                    throw new IllegalArgumentException(
                            entry.contains("isolated_reason", Tag.TAG_STRING)
                                    ? entry.getString("isolated_reason")
                                    : "Missing AEKey");
                }
                CompoundTag encoded = entry.getCompound("key");
                if (encodedCounts.get(encoded) != 1 || key != null && keyCounts.get(key) != 1) {
                    throw new IllegalArgumentException("Conflicting duplicate AEKey; all copies retained");
                }
                available.add(new Entry<>(key, encoded.copy(), readAmount.apply(entry)));
            } catch (RuntimeException e) {
                retained.add(entry.copy());
                failures.add("entry[" + i + "]: " + e.getMessage());
                if (key != null) blocked.add(key);
            }
        }
        return new Result<>(List.copyOf(available), List.copyOf(retained), List.copyOf(failures), Set.copyOf(blocked));
    }

    private static HugeAmount amount(CompoundTag entry) {
        boolean small = entry.contains("amount_long", Tag.TAG_LONG);
        boolean wide = entry.contains("amount_wide", Tag.TAG_BYTE_ARRAY);
        if (small == wide) throw new IllegalArgumentException("Expected exactly one amount encoding");
        if (small) {
            long value = entry.getLong("amount_long");
            if (value <= 0) throw new IllegalArgumentException("Non-positive amount");
            return HugeAmount.of(value);
        }
        byte[] bytes = entry.getByteArray("amount_wide");
        if (bytes.length == 0) throw new IllegalArgumentException("Empty wide amount");
        BigInteger value = new BigInteger(bytes);
        if (value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0 || !Arrays.equals(bytes, value.toByteArray())) {
            throw new IllegalArgumentException("Non-canonical wide amount");
        }
        return HugeAmount.of(value);
    }
}
