package cn.dancingsnow.neoecoae.core.storage;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.math.BigInteger;
import cn.dancingsnow.neoecoae.core.amount.PlannerAmount;
import java.util.Map;
import java.util.function.BiConsumer;

/** One quantity table. Only keys exceeding long have a BigInteger side entry. Server-thread owned. */
public final class ExactStorageEngine<K> {
    private final Object2LongOpenHashMap<K> amounts = new Object2LongOpenHashMap<>();
    private final Map<K, PlannerAmount> overflow = new Object2ObjectOpenHashMap<>();

    public long visible(K key) { return amounts.getLong(key); }

    public BigInteger exact(K key) {
        long amount = visible(key);
        PlannerAmount big = amount == Long.MAX_VALUE ? overflow.get(key) : null;
        return big == null ? BigInteger.valueOf(amount) : big.toBigInteger();
    }

    public void set(K key, BigInteger amount) {
        if (amount.signum() < 0) throw new IllegalArgumentException("Negative storage quantity");
        if (amount.signum() == 0) amounts.removeLong(key);
        else amounts.put(key, amount.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue());
        if (amount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) overflow.put(key, PlannerAmount.of(amount));
        else overflow.remove(key);
    }

    public void add(K key, long amount) {
        add(key, visible(key), amount);
    }

    /** The caller may reuse its current visible projection to avoid a second lookup. */
    public void add(K key, long current, long amount) {
        if (amount < 0 || current < 0) throw new IllegalArgumentException("Negative storage quantity");
        if (amount == 0) return;
        if (amount <= Long.MAX_VALUE - current) {
            amounts.put(key, current + amount);
        } else {
            PlannerAmount big = overflow.get(key);
            overflow.put(key, (big == null ? PlannerAmount.of(current) : big).add(amount));
            amounts.put(key, Long.MAX_VALUE);
        }
    }

    public void add(K key, BigInteger amount) {
        if (amount.signum() < 0) throw new IllegalArgumentException("Negative storage quantity");
        set(key, PlannerAmount.of(exact(key)).add(PlannerAmount.of(amount)).toBigInteger());
    }

    public void subtract(K key, long amount) {
        subtract(key, visible(key), amount);
    }

    /** Returns the new long projection, including a possible downgrade from the overflow table. */
    public long subtract(K key, long current, long amount) {
        if (amount < 0 || current < 0 || amount > current) throw new IllegalArgumentException("Invalid storage subtraction");
        if (amount == 0) return current;
        PlannerAmount big = current == Long.MAX_VALUE ? overflow.get(key) : null;
        if (big != null) {
            PlannerAmount next = big.subtract(amount);
            if (!next.fitsLong()) {
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

    public boolean isEmpty() { return amounts.isEmpty(); }
    public boolean hasOverflow() { return !overflow.isEmpty(); }

    public void visitExact(BiConsumer<K, BigInteger> action) {
        var iterator = amounts.object2LongEntrySet().fastIterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            long value = entry.getLongValue();
            PlannerAmount big = value == Long.MAX_VALUE ? overflow.get(entry.getKey()) : null;
            action.accept(entry.getKey(), big == null ? BigInteger.valueOf(value) : big.toBigInteger());
        }
    }

    public void visitVisible(java.util.function.ObjLongConsumer<K> action) {
        var iterator = amounts.object2LongEntrySet().fastIterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            action.accept(entry.getKey(), entry.getLongValue());
        }
    }
}
