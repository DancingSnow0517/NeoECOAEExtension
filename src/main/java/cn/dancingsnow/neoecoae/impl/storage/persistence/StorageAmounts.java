package cn.dancingsnow.neoecoae.impl.storage.persistence;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.math.BigInteger;
import java.util.Map;
import java.util.function.BiConsumer;

/** Server-thread quantity table shared by finite cells and infinite domains. */
public class StorageAmounts {
    private final Object2LongOpenHashMap<AEKey> amounts = new Object2LongOpenHashMap<>();
    private final Object2LongMap<AEKey> view = Object2LongMaps.unmodifiable(amounts);
    private final Object2ObjectOpenHashMap<AEKey, OverflowAmount> overflow = new Object2ObjectOpenHashMap<>();
    private final StorageTotal total = new StorageTotal();
    private long contentRevision;
    private long structureRevision;

    public long visible(AEKey key) { return amounts.getLong(key); }
    public Object2LongMap<AEKey> visibleAmounts() { return view; }
    public int size() { return amounts.size(); }
    public long totalVisible() { return total.visible(); }
    public BigInteger totalExact() { return total.snapshot().toBigInteger(); }
    public boolean sameContents(StorageAmounts other) { return amounts.equals(other.amounts) && overflow.equals(other.overflow); }
    public boolean isBig(AEKey key) { return overflow.containsKey(key); }
    public long contentRevision() { return contentRevision; }
    public long structureRevision() { return structureRevision; }

    public HugeAmount get(AEKey key) {
        long value = visible(key);
        OverflowAmount big = value == Long.MAX_VALUE ? overflow.get(key) : null;
        return big == null ? HugeAmount.of(value) : big.snapshot();
    }
    public void setLong(AEKey key, long value) {
        if (key == null || value < 0) throw new IllegalArgumentException("Invalid storage key or amount");
        long before = visible(key);
        if (before == Long.MAX_VALUE && overflow.containsKey(key)) { set(key, HugeAmount.of(value)); return; }
        if (before == value) return;
        if (value == 0) amounts.removeLong(key); else amounts.put(key, value);
        total.subtract(before); total.add(value);
        contentRevision++;
        if ((before == 0) != (value == 0)) structureRevision++;
    }
    public void set(AEKey key, HugeAmount value) {
        if (key == null || value == null) throw new IllegalArgumentException("Missing storage key or amount");
        if (!value.isBig() && !overflow.containsKey(key)) { setLong(key, value.toLongSaturated()); return; }
        HugeAmount before = get(key);
        if (before.equals(value)) return;
        if (value.isZero()) amounts.removeLong(key); else amounts.put(key, value.toLongSaturated());
        if (value.isBig()) overflow.put(key, new OverflowAmount(value)); else overflow.remove(key);
        total.subtract(before); total.add(value);
        contentRevision++;
        if (before.isZero() != value.isZero()) structureRevision++;
    }
    public void add(AEKey key, long amount) { add(key, visible(key), amount); }
    public void add(AEKey key, long current, long amount) {
        if (amount <= 0) return;
        OverflowAmount big = current == Long.MAX_VALUE ? overflow.get(key) : null;
        if (big != null) { big.add(amount); total.add(amount); contentRevision++; return; }
        if (amount <= Long.MAX_VALUE - current) setLong(key, current + amount);
        else set(key, get(key).add(amount));
    }
    public void add(AEKey key, BigInteger amount) {
        if (amount.signum() > 0) set(key, get(key).add(HugeAmount.of(amount)));
    }
    public void subtract(AEKey key, long amount) { subtract(key, visible(key), amount); }
    public long subtract(AEKey key, long current, long amount) {
        if (amount < 0 || amount > current) throw new IllegalArgumentException("Invalid extraction");
        OverflowAmount big = current == Long.MAX_VALUE ? overflow.get(key) : null;
        if (big == null) setLong(key, current - amount);
        else {
            long remaining = big.subtract(amount);
            total.subtract(amount); contentRevision++;
            if (remaining < Long.MAX_VALUE || !big.isBig()) {
                overflow.remove(key);
                if (remaining == 0) { amounts.removeLong(key); structureRevision++; }
                else amounts.put(key, remaining);
            }
        }
        return visible(key);
    }
    public void clear() {
        if (amounts.isEmpty()) return;
        amounts.clear(); overflow.clear(); amounts.trim(); overflow.trim(); total.clear();
        contentRevision++; structureRevision++;
    }
    public boolean isEmpty() { return amounts.isEmpty(); }
    public boolean hasOverflow() { return !overflow.isEmpty(); }
    public void forEach(BiConsumer<AEKey, HugeAmount> action) {
        var iterator = amounts.object2LongEntrySet().fastIterator();
        while (iterator.hasNext()) { var entry = iterator.next(); action.accept(entry.getKey(), get(entry.getKey())); }
    }
    public void visitExact(BiConsumer<AEKey, BigInteger> action) {
        var iterator = amounts.object2LongEntrySet().fastIterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            OverflowAmount big = entry.getLongValue() == Long.MAX_VALUE ? overflow.get(entry.getKey()) : null;
            action.accept(entry.getKey(), big == null ? BigInteger.valueOf(entry.getLongValue()) : big.snapshot().toBigInteger());
        }
    }
    public void visitVisible(java.util.function.ObjLongConsumer<AEKey> action) {
        var iterator = amounts.object2LongEntrySet().fastIterator();
        while (iterator.hasNext()) { var entry = iterator.next(); action.accept(entry.getKey(), entry.getLongValue()); }
    }

    /** Small transfers into an enormous stack accumulate in a primitive delta until an exact read or boundary. */
    private static final class OverflowAmount {
        private final StorageTotal value = new StorageTotal();
        private long untilLong;
        private boolean big = true;
        OverflowAmount(HugeAmount amount) { value.add(amount); updateThreshold(amount); }
        private void updateThreshold(HugeAmount amount) {
            big = amount.isBig();
            untilLong = big ? amount.subtract(Long.MAX_VALUE).toLongSaturated() : 0;
        }
        void add(long amount) {
            value.add(amount);
            untilLong = amount > Long.MAX_VALUE - untilLong ? Long.MAX_VALUE : untilLong + amount;
        }
        long subtract(long amount) {
            value.subtract(amount);
            if (amount < untilLong) { untilLong -= amount; return Long.MAX_VALUE; }
            HugeAmount exact = value.snapshot(); updateThreshold(exact); return exact.toLongSaturated();
        }
        boolean isBig() { return big; }
        HugeAmount snapshot() { return value.snapshot(); }
        @Override public boolean equals(Object other) {
            return other instanceof OverflowAmount amount && snapshot().equals(amount.snapshot());
        }
        @Override public int hashCode() { return snapshot().hashCode(); }
    }
}
