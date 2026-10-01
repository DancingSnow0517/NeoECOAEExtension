package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.math.BigInteger;
import java.util.Map;
import java.util.function.BiConsumer;

/** One quantity table. Only keys exceeding long have a BigInteger side entry. Server-thread owned. */
final class InfiniteStorageAmounts {
    private final Object2LongOpenHashMap<AEKey> amounts = new Object2LongOpenHashMap<>();
    private final Map<AEKey, BigInteger> overflow = new Object2ObjectOpenHashMap<>();

    long visible(AEKey key) { return amounts.getLong(key); }

    HugeAmount get(AEKey key) {
        long amount = visible(key);
        BigInteger big = amount == Long.MAX_VALUE ? overflow.get(key) : null;
        return big == null ? HugeAmount.of(amount) : HugeAmount.of(big);
    }

    void set(AEKey key, HugeAmount amount) {
        if (amount.isZero()) amounts.removeLong(key);
        else amounts.put(key, amount.toLongSaturated());
        if (amount.isBig()) overflow.put(key, amount.toBigInteger());
        else overflow.remove(key);
    }

    void add(AEKey key, long amount) {
        add(key, visible(key), amount);
    }

    void add(AEKey key, long current, long amount) {
        if (amount <= Long.MAX_VALUE - current) {
            amounts.put(key, current + amount);
        } else {
            BigInteger big = overflow.get(key);
            overflow.put(key, (big == null ? BigInteger.valueOf(current) : big).add(BigInteger.valueOf(amount)));
            amounts.put(key, Long.MAX_VALUE);
        }
    }

    void add(AEKey key, BigInteger amount) {
        set(key, get(key).add(HugeAmount.of(amount)));
    }

    void subtract(AEKey key, long amount) {
        subtract(key, visible(key), amount);
    }

    /** Returns the new long projection, including a possible downgrade from the overflow table. */
    long subtract(AEKey key, long current, long amount) {
        BigInteger big = current == Long.MAX_VALUE ? overflow.get(key) : null;
        if (big != null) {
            BigInteger next = big.subtract(BigInteger.valueOf(amount));
            if (next.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                overflow.put(key, next);
                return Long.MAX_VALUE;
            }
            overflow.remove(key);
            long remaining = next.longValueExact();
            if (remaining == 0) amounts.removeLong(key);
            else amounts.put(key, remaining);
            return remaining;
        } else if (current == amount) {
            amounts.removeLong(key);
            return 0;
        } else {
            amounts.put(key, current - amount);
            return current - amount;
        }
    }

    boolean isEmpty() { return amounts.isEmpty(); }
    boolean hasOverflow() { return !overflow.isEmpty(); }

    void forEach(BiConsumer<AEKey, HugeAmount> action) {
        var iterator = amounts.object2LongEntrySet().fastIterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            long value = entry.getLongValue();
            BigInteger big = value == Long.MAX_VALUE ? overflow.get(entry.getKey()) : null;
            action.accept(entry.getKey(), big == null ? HugeAmount.of(value) : HugeAmount.of(big));
        }
    }

    void visitExact(BiConsumer<AEKey, BigInteger> action) {
        var iterator = amounts.object2LongEntrySet().fastIterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            long value = entry.getLongValue();
            BigInteger big = value == Long.MAX_VALUE ? overflow.get(entry.getKey()) : null;
            action.accept(entry.getKey(), big == null ? BigInteger.valueOf(value) : big);
        }
    }

    void visitVisible(java.util.function.ObjLongConsumer<AEKey> action) {
        var iterator = amounts.object2LongEntrySet().fastIterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            action.accept(entry.getKey(), entry.getLongValue());
        }
    }
}
