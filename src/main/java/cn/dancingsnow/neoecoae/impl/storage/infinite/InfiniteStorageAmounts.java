package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/** One quantity table. Only keys exceeding long have a BigInteger side entry. Server-thread owned. */
final class InfiniteStorageAmounts {
    private final Object2LongOpenHashMap<AEKey> amounts = new Object2LongOpenHashMap<>();
    private final Map<AEKey, BigInteger> overflow = new HashMap<>();

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
        long current = visible(key);
        if (amount <= Long.MAX_VALUE - current) {
            amounts.put(key, current + amount);
        } else {
            BigInteger big = overflow.get(key);
            overflow.put(key, (big == null ? BigInteger.valueOf(current) : big).add(BigInteger.valueOf(amount)));
            amounts.put(key, Long.MAX_VALUE);
        }
    }

    void subtract(AEKey key, long amount) {
        long current = visible(key);
        BigInteger big = current == Long.MAX_VALUE ? overflow.get(key) : null;
        if (big != null) {
            set(key, HugeAmount.of(big.subtract(BigInteger.valueOf(amount))));
        } else if (current == amount) {
            amounts.removeLong(key);
        } else {
            amounts.put(key, current - amount);
        }
    }

    boolean isEmpty() { return amounts.isEmpty(); }
    boolean hasOverflow() { return !overflow.isEmpty(); }

    void forEach(BiConsumer<AEKey, HugeAmount> action) {
        for (var entry : amounts.object2LongEntrySet()) action.accept(entry.getKey(), get(entry.getKey()));
    }

    void visitVisible(java.util.function.ObjLongConsumer<AEKey> action) {
        for (var entry : amounts.object2LongEntrySet()) action.accept(entry.getKey(), entry.getLongValue());
    }
}
