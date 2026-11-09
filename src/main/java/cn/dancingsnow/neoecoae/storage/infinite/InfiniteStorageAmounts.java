package cn.dancingsnow.neoecoae.storage.infinite;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.amount.U126;
import cn.dancingsnow.neoecoae.crafting.amount.UnsignedLongArray;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.math.BigInteger;
import java.util.function.BiConsumer;

/** Server-owned quantity table. Existing transfers locate an entry once and mutate its primitive quantity. */
final class InfiniteStorageAmounts {
    private final Object2ObjectOpenHashMap<AEKey, Entry> entries = new Object2ObjectOpenHashMap<>();
    private AEKey lastKey;
    private Entry lastEntry;
    private int size;
    private int oversized;
    private long changes;
    static final int CHANGE_LOG_CAPACITY = 4096;
    private Entry oldest;
    private Entry newest;
    private int logSize;
    private long logFloor;

    long changes() { return changes; }

    /** Structural visibility changes cannot be reconstructed from quantity changes. */
    void touch() {
        logFloor = ++changes;
        for (Entry entry = oldest; entry != null;) {
            Entry next = entry.next;
            entry.previous = entry.next = null;
            entry.sequence = 0;
            discardEmpty(entry);
            entry = next;
        }
        oldest = newest = null;
        logSize = 0;
    }

    private Entry find(AEKey key, boolean create) {
        Entry entry = key == lastKey ? lastEntry : entries.get(key);
        if (entry == null && create) {
            // Canonicalization belongs to admission, never an existing lookup or a simulation.
            entry = new Entry(ECOKeyFactory.canonicalize(key));
            entries.put(entry.key, entry);
        }
        lastKey = key;
        lastEntry = entry;
        return entry;
    }

    private void changed(Entry entry) {
        entry.snapshot = null;
        entry.exact = null;
        long sequence = ++changes;
        if (entry == newest) {
            entry.sequence = sequence;
            return;
        }
        if (entry.sequence != 0) {
            unlink(entry);
        } else if (logSize == CHANGE_LOG_CAPACITY) {
            Entry evicted = oldest;
            logFloor = Math.max(logFloor, evicted.sequence);
            unlink(evicted);
            evicted.sequence = 0;
            discardEmpty(evicted);
            logSize--;
        }
        if (entry.sequence == 0) logSize++;
        entry.sequence = sequence;
        entry.previous = newest;
        if (newest == null) oldest = entry;
        else newest.next = entry;
        newest = entry;
    }

    private void unlink(Entry entry) {
        if (entry.previous == null) oldest = entry.next;
        else entry.previous.next = entry.next;
        if (entry.next == null) newest = entry.previous;
        else entry.next.previous = entry.previous;
        entry.previous = entry.next = null;
    }

    /** Deleted entries remain bounded tombstones until every covered reader can observe their removal. */
    private void discardEmpty(Entry entry) {
        if (entry.amount != 0) return;
        entries.remove(entry.key);
        if (lastEntry == entry) {
            lastKey = null;
            lastEntry = null;
        }
    }

    boolean visitChangesSince(long since, long current, java.util.function.Consumer<AEKey> visitor) {
        if (current != changes || since > current || since < logFloor) return false;
        for (Entry entry = newest; entry != null && entry.sequence > since; entry = entry.previous) {
            visitor.accept(entry.key);
        }
        return true;
    }

    long visible(AEKey key) {
        Entry entry = find(key, false);
        return entry == null ? 0 : entry.amount;
    }

    HugeAmount get(AEKey key) {
        Entry entry = find(key, false);
        return entry == null ? HugeAmount.ZERO : entry.snapshot();
    }

    void set(AEKey key, HugeAmount amount) {
        Entry entry = find(key, true);
        boolean wasEmpty = entry.amount == 0;
        boolean wasWide = entry.wide != null;
        entry.amount = amount.toLongSaturated();
        if (amount.isBig()) {
            if (entry.wide == null) entry.wide = new WideAmount();
            entry.wide.set(amount);
        } else entry.wide = null;
        size += (wasEmpty ? 1 : 0) - (entry.amount == 0 ? 1 : 0);
        oversized += (entry.wide != null ? 1 : 0) - (wasWide ? 1 : 0);
        changed(entry);
        entry.snapshot = amount;
    }

    /** Returns the previous long projection, allowing the caller to update type counts without another lookup. */
    long add(AEKey key, long amount) {
        if (amount < 0) throw new IllegalArgumentException("Amount must not be negative");
        if (amount == 0) return visible(key);
        Entry entry = find(key, true);
        long previous = entry.amount;
        if (entry.wide == null && amount <= Long.MAX_VALUE - previous) {
            entry.amount = previous + amount;
        } else {
            if (entry.wide == null) {
                entry.wide = new WideAmount();
                entry.wide.low = previous;
                oversized++;
            }
            entry.wide.add(amount);
            entry.amount = Long.MAX_VALUE;
        }
        if (previous == 0) size++;
        changed(entry);
        return previous;
    }

    void add(AEKey key, BigInteger amount) {
        if (amount.bitLength() < Long.SIZE) {
            add(key, amount.longValueExact());
        } else {
            set(key, get(key).add(HugeAmount.of(amount)));
        }
    }

    /** Returns the new long projection. The preceding visible() lookup is reused by identity. */
    long subtract(AEKey key, long amount) {
        Entry entry = find(key, false);
        long previous = entry == null ? 0 : entry.amount;
        if (amount < 0 || amount > previous) throw new IllegalArgumentException("Invalid amount subtraction");
        if (amount == 0) return previous;
        if (entry.wide == null) {
            entry.amount -= amount;
        } else {
            entry.wide.subtract(amount);
            if (entry.wide.fitsLong()) {
                entry.amount = entry.wide.low;
                entry.wide = null;
                oversized--;
            }
        }
        if (entry.amount == 0) size--;
        changed(entry);
        return entry.amount;
    }

    boolean isEmpty() { return size == 0; }
    boolean hasOverflow() { return oversized != 0; }

    void forEach(BiConsumer<AEKey, HugeAmount> action) {
        if (size == 0) return;
        for (Entry entry : entries.values()) {
            if (entry.amount != 0) action.accept(entry.key, entry.snapshot());
        }
    }

    void visitExact(BiConsumer<AEKey, BigInteger> action) {
        if (size == 0) return;
        for (Entry entry : entries.values()) {
            if (entry.amount != 0) action.accept(entry.key, entry.exact());
        }
    }

    void visitVisible(java.util.function.ObjLongConsumer<AEKey> action) {
        if (size == 0) return;
        for (Entry entry : entries.values()) {
            if (entry.amount != 0) action.accept(entry.key, entry.amount);
        }
    }

    void visitAmounts(java.util.function.ObjLongConsumer<AEKey> ordinary, BiConsumer<AEKey, BigInteger> wide) {
        if (size == 0) return;
        for (Entry entry : entries.values()) {
            if (entry.wide != null) wide.accept(entry.key, entry.exact());
            else if (entry.amount != 0) ordinary.accept(entry.key, entry.amount);
        }
    }

    BigInteger overflowAmount(AEKey key) {
        Entry entry = find(key, false);
        return entry == null || entry.wide == null ? null : entry.exact();
    }

    /** Visits only quantities whose long projection is lossy. */
    void visitOverflow(BiConsumer<AEKey, BigInteger> action) {
        if (oversized == 0) return;
        for (Entry entry : entries.values()) {
            if (entry.wide != null) action.accept(entry.key, entry.exact());
        }
    }

    private static final class Entry {
        final AEKey key;
        long amount;
        WideAmount wide;
        HugeAmount snapshot;
        BigInteger exact;
        Entry previous;
        Entry next;
        long sequence;

        Entry(AEKey key) { this.key = key; }

        HugeAmount snapshot() {
            if (snapshot == null) snapshot = wide == null ? HugeAmount.of(amount) : wide.snapshot();
            return snapshot;
        }

        BigInteger exact() {
            if (exact == null) exact = wide == null ? BigInteger.valueOf(amount) : wide.exact();
            return exact;
        }
    }

    /** Allocated once on promotion. The two words are mutable only inside the server-owned ledger. */
    private static final class WideAmount {
        private static final long HIGH_LIMIT = 1L << 62;
        long high;
        long low;
        UnsignedLongArray big;

        void set(HugeAmount value) {
            U126 nativeValue = value.toU126();
            big = nativeValue == null ? value.toUnsignedLongArray() : null;
            high = nativeValue == null ? 0L : nativeValue.highWord();
            low = nativeValue == null ? 0L : nativeValue.lowWord();
        }

        void add(long value) {
            if (big != null) {
                big = big.add(value);
                return;
            }
            long next = low + value;
            long carry = Long.compareUnsigned(next, low) < 0 ? 1 : 0;
            if (high + carry == HIGH_LIMIT) {
                big = UnsignedLongArray.fromParts(high, low).add(value);
            } else {
                high += carry;
                low = next;
            }
        }

        void subtract(long value) {
            if (big != null) {
                UnsignedLongArray remaining = big.subtract(value);
                if (remaining.bitLength() > 126) big = remaining;
                else set(HugeAmount.of(remaining));
                return;
            }
            high -= Long.compareUnsigned(low, value) < 0 ? 1 : 0;
            low -= value;
        }

        boolean fitsLong() { return big == null && high == 0 && low >= 0; }

        BigInteger exact() {
            if (big != null) return big.toBigInteger();
            BigInteger lower = BigInteger.valueOf(low & Long.MAX_VALUE);
            if (low < 0) lower = lower.setBit(63);
            return high == 0 ? lower : BigInteger.valueOf(high).shiftLeft(64).or(lower);
        }

        HugeAmount snapshot() {
            return big == null ? HugeAmount.of(U126.ofParts(high, low)) : HugeAmount.of(big);
        }
    }
}
