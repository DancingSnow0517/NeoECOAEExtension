package cn.dancingsnow.neoecoae.storage.persistence;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.storage.StoragePerformance;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory;
import cn.dancingsnow.neoecoae.storage.infinite.HugeAmount;
import cn.dancingsnow.neoecoae.crafting.amount.U128;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongMap;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.math.BigInteger;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;

/** Shared cell/domain quantity table. Existing transfers locate an entry once and mutate its primitive quantity. */
public class StorageAmounts {
    private final Object2ObjectOpenHashMap<AEKey, Entry> entries = new Object2ObjectOpenHashMap<>();
    private AEKey lastKey;
    private Entry lastEntry;
    private int size;
    private int oversized;
    private long changes;
    public static final int CHANGE_LOG_CAPACITY = 4096;
    private int changeLogCapacity;
    private long structureRevision;
    private final StorageTotal total = new StorageTotal();
    private final Object2LongMap<AEKey> view = Object2LongMaps.unmodifiable(new VisibleAmounts());
    private Entry oldest;
    private Entry newest;
    private int logSize;
    private long logFloor;

    public long changes() { return changes; }
    public long contentRevision() { return changes; }
    public long structureRevision() { return structureRevision; }
    public int size() { return size; }
    public long totalVisible() { return total.visible(); }
    public BigInteger totalExact() { return total.snapshot().toBigInteger(); }
    public Object2LongMap<AEKey> visibleAmounts() { return view; }
    public boolean isBig(AEKey key) { Entry entry = find(key, false); return entry != null && entry.wide != null; }

    public boolean sameContents(StorageAmounts other) {
        if (size != other.size) return false;
        for (Entry entry : entries.values()) {
            if (entry.amount != 0 && !entry.snapshot().equals(other.get(entry.key))) return false;
        }
        return true;
    }

    public void enableChangeLog(int capacity) {
        clearLog();
        changeLogCapacity = Math.max(1, capacity);
    }

    public boolean visitChangedSince(long since, java.util.function.Consumer<AEKey> visitor) {
        return changeLogCapacity != 0 && visitChangesSince(since, changes, visitor);
    }

    public void clear() {
        if (size == 0) return;
        StoragePerformance.add(StoragePerformance.Counter.REMOVED_KEY, size);
        StoragePerformance.add(StoragePerformance.Counter.ENTRY_DISCARDED, entries.size());
        entries.clear();
        entries.trim();
        lastKey = null;
        lastEntry = null;
        size = oversized = 0;
        total.clear();
        oldest = newest = null;
        logSize = 0;
        logFloor = ++changes;
        structureRevision++;
    }

    public void setLong(AEKey key, long amount) {
        if (key == null || amount < 0) throw new IllegalArgumentException("Invalid storage key or amount");
        Entry entry = find(key, false);
        long previous = entry == null ? 0 : entry.amount;
        if (entry != null && entry.wide != null) { set(key, HugeAmount.of(amount)); return; }
        if (previous == amount) return;
        if (entry == null) entry = find(key, true);
        entry.amount = amount;
        total.subtract(previous);
        total.add(amount);
        if ((previous == 0) != (amount == 0)) {
            size += previous == 0 ? 1 : -1;
            structureRevision++;
            StoragePerformance.count(previous == 0 ? StoragePerformance.Counter.NEW_KEY : StoragePerformance.Counter.REMOVED_KEY);
        }
        changed(entry);
    }

    // Keep the existing public descriptors used by cell implementations and optional integrations.
    public void add(AEKey key, long amount) { if (amount > 0) addAndGetPrevious(key, amount); }
    public void add(AEKey key, long current, long amount) { add(key, amount); }
    public void subtract(AEKey key, long amount) { subtractAndGetRemaining(key, amount); }
    public long subtract(AEKey key, long current, long amount) { return subtractAndGetRemaining(key, amount); }
    /** Structural visibility changes cannot be reconstructed from quantity changes. */
    public void touch() {
        changes++;
        clearLog();
    }

    private void clearLog() {
        logFloor = changes;
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
        StoragePerformance.count(key == lastKey ? StoragePerformance.Counter.LAST_KEY_HIT : StoragePerformance.Counter.LOOKUP);
        Entry entry = key == lastKey ? lastEntry : entries.get(key);
        if (entry == null && create) {
            // Canonicalization belongs to admission, never an existing lookup or a simulation.
            entry = new Entry(ECOKeyFactory.canonicalize(key));
            entries.put(entry.key, entry);
            StoragePerformance.count(StoragePerformance.Counter.ENTRY_CREATED);
        }
        lastKey = key;
        lastEntry = entry;
        return entry;
    }

    private void changed(Entry entry) {
        entry.snapshot = null;
        entry.exact = null;
        long sequence = ++changes;
        if (changeLogCapacity == 0) { discardEmpty(entry); return; }
        if (entry == newest) {
            entry.sequence = sequence;
            return;
        }
        if (entry.sequence != 0) {
            unlink(entry);
        } else if (logSize == changeLogCapacity) {
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
        StoragePerformance.count(StoragePerformance.Counter.ENTRY_DISCARDED);
        if (lastEntry == entry) {
            lastKey = null;
            lastEntry = null;
        }
    }

    public boolean visitChangesSince(long since, long current, java.util.function.Consumer<AEKey> visitor) {
        if (current != changes || since > current || since < logFloor) return false;
        for (Entry entry = newest; entry != null && entry.sequence > since; entry = entry.previous) {
            visitor.accept(entry.key);
        }
        return true;
    }

    /** Reads the already located Entry amount; no second hash lookup for each changed key. */
    public boolean visitListingChangesSince(long since, long current, java.util.function.ObjLongConsumer<AEKey> visitor) {
        if (changeLogCapacity == 0 || current != changes || since > current || since < logFloor) return false;
        for (Entry entry = newest; entry != null && entry.sequence > since; entry = entry.previous) {
            visitor.accept(entry.key, entry.amount);
        }
        return true;
    }

    public long visible(AEKey key) {
        Entry entry = find(key, false);
        return entry == null ? 0 : entry.amount;
    }

    public HugeAmount get(AEKey key) {
        Entry entry = find(key, false);
        return entry == null ? HugeAmount.ZERO : entry.snapshot();
    }

    public BigInteger exact(AEKey key) {
        Entry entry = find(key, false);
        return entry == null ? BigInteger.ZERO : entry.exact();
    }

    public void set(AEKey key, HugeAmount amount) {
        if (key == null || amount == null) throw new IllegalArgumentException("Missing storage key or amount");
        Entry entry = find(key, false);
        if (entry == null && amount.isZero()) return;
        if (!amount.isBig() && (entry == null || entry.wide == null)) { setLong(key, amount.toLongSaturated()); return; }
        HugeAmount before = entry == null ? HugeAmount.ZERO : entry.snapshot();
        if (before.equals(amount)) return;
        if (entry == null) entry = find(key, true);
        total.subtract(before);
        total.add(amount);
        if (before.isZero() != amount.isZero()) {
            structureRevision++;
            StoragePerformance.count(before.isZero() ? StoragePerformance.Counter.NEW_KEY : StoragePerformance.Counter.REMOVED_KEY);
        }
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
    public long addAndGetPrevious(AEKey key, long amount) {
        if (key == null) throw new IllegalArgumentException("Missing storage key");
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
        if (previous == 0) {
            size++; structureRevision++;
            StoragePerformance.count(StoragePerformance.Counter.NEW_KEY);
        }
        total.add(amount);
        changed(entry);
        return previous;
    }

    public void add(AEKey key, BigInteger amount) {
        if (amount.signum() <= 0) return;
        if (amount.bitLength() < Long.SIZE) {
            add(key, amount.longValueExact());
        } else {
            set(key, get(key).add(HugeAmount.of(amount)));
        }
    }

    /** Returns the new long projection. The preceding visible() lookup is reused by identity. */
    public long subtractAndGetRemaining(AEKey key, long amount) {
        if (key == null) throw new IllegalArgumentException("Missing storage key");
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
        if (entry.amount == 0) {
            size--; structureRevision++;
            StoragePerformance.count(StoragePerformance.Counter.REMOVED_KEY);
        }
        total.subtract(amount);
        changed(entry);
        return entry.amount;
    }

    public boolean isEmpty() { return size == 0; }
    public boolean hasOverflow() { return oversized != 0; }

    public void forEach(BiConsumer<AEKey, HugeAmount> action) {
        if (size == 0) return;
        for (Entry entry : entries.values()) {
            if (entry.amount != 0) action.accept(entry.key, entry.snapshot());
        }
    }

    public void visitExact(BiConsumer<AEKey, BigInteger> action) {
        if (size == 0) return;
        for (Entry entry : entries.values()) {
            if (entry.amount != 0) action.accept(entry.key, entry.exact());
        }
    }

    public void visitVisible(java.util.function.ObjLongConsumer<AEKey> action) {
        if (size == 0) return;
        for (Entry entry : entries.values()) {
            if (entry.amount != 0) action.accept(entry.key, entry.amount);
        }
    }

    public void visitAmounts(java.util.function.ObjLongConsumer<AEKey> ordinary, BiConsumer<AEKey, BigInteger> wide) {
        if (size == 0) return;
        for (Entry entry : entries.values()) {
            if (entry.wide != null) wide.accept(entry.key, entry.exact());
            else if (entry.amount != 0) ordinary.accept(entry.key, entry.amount);
        }
    }

    public BigInteger overflowAmount(AEKey key) {
        Entry entry = find(key, false);
        return entry == null || entry.wide == null ? null : entry.exact();
    }

    /** Visits only quantities whose long projection is lossy. */
    public void visitOverflow(BiConsumer<AEKey, BigInteger> action) {
        if (oversized == 0) return;
        for (Entry entry : entries.values()) {
            if (entry.wide != null) action.accept(entry.key, entry.exact());
        }
    }

    /** A live read-only projection; quantities remain owned exclusively by their entries. */
    private final class VisibleAmounts extends AbstractObject2LongMap<AEKey> {
        @Override public int size() { return StorageAmounts.this.size; }
        @Override public long getLong(Object key) {
            return key instanceof AEKey resource ? visible(resource) : 0;
        }
        @Override public boolean containsKey(Object key) { return getLong(key) != 0; }
        @Override public ObjectSet<Object2LongMap.Entry<AEKey>> object2LongEntrySet() {
            return new AbstractObjectSet<>() {
                @Override public int size() { return StorageAmounts.this.size; }
                @Override public ObjectIterator<Object2LongMap.Entry<AEKey>> iterator() {
                    var source = entries.values().iterator();
                    return new ObjectIterator<>() {
                        private StorageAmounts.Entry next;
                        @Override public boolean hasNext() {
                            while (next == null && source.hasNext()) {
                                StorageAmounts.Entry candidate = source.next();
                                if (candidate.amount != 0) next = candidate;
                            }
                            return next != null;
                        }
                        @Override public Object2LongMap.Entry<AEKey> next() {
                            if (!hasNext()) throw new NoSuchElementException();
                            StorageAmounts.Entry entry = next;
                            next = null;
                            return new BasicEntry<>(entry.key, entry.amount);
                        }
                    };
                }
            };
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
        long high;
        long low;
        BigInteger big;

        void set(HugeAmount value) {
            U128 nativeValue = value.toU128();
            big = nativeValue == null ? value.toBigInteger() : null;
            high = nativeValue == null ? 0L : nativeValue.highWord();
            low = nativeValue == null ? 0L : nativeValue.lowWord();
        }

        void add(long value) {
            if (big != null) {
                big = big.add(BigInteger.valueOf(value));
                return;
            }
            long next = low + value;
            long carry = Long.compareUnsigned(next, low) < 0 ? 1 : 0;
            if (high == -1L && carry != 0L) {
                big = exact().add(BigInteger.valueOf(value));
            } else {
                high += carry;
                low = next;
            }
        }

        void subtract(long value) {
            if (big != null) {
                set(HugeAmount.of(big.subtract(BigInteger.valueOf(value))));
                return;
            }
            high -= Long.compareUnsigned(low, value) < 0 ? 1 : 0;
            low -= value;
        }

        boolean fitsLong() { return big == null && high == 0 && low >= 0; }

        BigInteger exact() {
            return snapshot().toBigInteger();
        }

        HugeAmount snapshot() {
            return big == null ? HugeAmount.of(U128.ofParts(high, low)) : HugeAmount.of(big);
        }
    }
}
