package cn.dancingsnow.neoecoae.storage.infinite;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.math.BigInteger;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * One quantity table. Only keys exceeding long have a side entry, held as a {@link HugeAmount}: U126 below 2^126,
 * BigInteger only beyond. Server-thread owned.
 */
final class InfiniteStorageAmounts {
    private final Object2LongOpenHashMap<AEKey> amounts = new Object2LongOpenHashMap<>();
    private final Map<AEKey, HugeAmount> overflow = new Object2ObjectOpenHashMap<>();
    private long changes;
    // Bounded change log for incremental network listings: ring of (key, latest sequence), oldest first.
    static final int CHANGE_LOG_CAPACITY = 4096;
    private final AEKey[] logKeys = new AEKey[CHANGE_LOG_CAPACITY];
    private final long[] logSequences = new long[CHANGE_LOG_CAPACITY];
    private int logStart;
    private int logSize;
    // Readers at a version below this cannot be served from the log and must list again.
    private long logFloor;

    /** Advances on every quantity change and on {@link #touch()}; keys network listing caches. */
    long changes() { return changes; }

    /** Records a change to the visible listing that is not a quantity change; readers must list again. */
    void touch() {
        changes++;
        logFloor = changes;
        for (int i = 0; i < logSize; i++) logKeys[(logStart + i) % CHANGE_LOG_CAPACITY] = null;
        logStart = 0;
        logSize = 0;
    }

    private void changed(AEKey key) {
        changes++;
        if (logSize > 0) {
            int last = (logStart + logSize - 1) % CHANGE_LOG_CAPACITY;
            // Repeated transfers of one key (for example FE) share one entry.
            if (logKeys[last] == key || logKeys[last].equals(key)) {
                logSequences[last] = changes;
                return;
            }
        }
        if (logSize == CHANGE_LOG_CAPACITY) {
            // Readers that already saw the evicted change are still fully served.
            logFloor = Math.max(logFloor, logSequences[logStart]);
            logStart = (logStart + 1) % CHANGE_LOG_CAPACITY;
            logSize--;
        }
        int slot = (logStart + logSize) % CHANGE_LOG_CAPACITY;
        logKeys[slot] = key;
        logSequences[slot] = changes;
        logSize++;
    }

    /**
     * Passes each key changed after version {@code since} (possibly more than once); false when the log no longer
     * covers that version or a structural change happened since.
     */
    boolean visitChangesSince(long since, long current, java.util.function.Consumer<AEKey> visitor) {
        if (current != changes || since > current || since < logFloor) return false;
        for (int i = logSize - 1; i >= 0; i--) {
            int slot = (logStart + i) % CHANGE_LOG_CAPACITY;
            if (logSequences[slot] <= since) break;
            visitor.accept(logKeys[slot]);
        }
        return true;
    }

    long visible(AEKey key) { return amounts.getLong(key); }

    HugeAmount get(AEKey key) {
        long amount = visible(key);
        HugeAmount big = amount == Long.MAX_VALUE ? overflow.get(key) : null;
        return big == null ? HugeAmount.of(amount) : big;
    }

    void set(AEKey key, HugeAmount amount) {
        changed(key);
        if (amount.isZero()) amounts.removeLong(key);
        else amounts.put(key, amount.toLongSaturated());
        if (amount.isBig()) overflow.put(key, amount);
        else overflow.remove(key);
    }

    void add(AEKey key, long amount) {
        add(key, visible(key), amount);
    }

    void add(AEKey key, long current, long amount) {
        changed(key);
        if (amount <= Long.MAX_VALUE - current) {
            amounts.put(key, current + amount);
        } else {
            HugeAmount big = overflow.get(key);
            overflow.put(key, (big == null ? HugeAmount.of(current) : big).add(amount));
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
        changed(key);
        HugeAmount big = current == Long.MAX_VALUE ? overflow.get(key) : null;
        if (big != null) {
            HugeAmount next = big.subtract(amount);
            if (next.isBig()) {
                overflow.put(key, next);
                return Long.MAX_VALUE;
            }
            overflow.remove(key);
            long remaining = next.toLongSaturated();
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
            HugeAmount big = value == Long.MAX_VALUE ? overflow.get(entry.getKey()) : null;
            action.accept(entry.getKey(), big == null ? HugeAmount.of(value) : big);
        }
    }

    void visitExact(BiConsumer<AEKey, BigInteger> action) {
        var iterator = amounts.object2LongEntrySet().fastIterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            long value = entry.getLongValue();
            HugeAmount big = value == Long.MAX_VALUE ? overflow.get(entry.getKey()) : null;
            action.accept(entry.getKey(), big == null ? BigInteger.valueOf(value) : big.toBigInteger());
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
