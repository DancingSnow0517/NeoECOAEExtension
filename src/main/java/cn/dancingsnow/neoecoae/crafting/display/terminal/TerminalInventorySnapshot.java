package cn.dancingsnow.neoecoae.crafting.display.terminal;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.SupplierStorageAccessor;
import cn.dancingsnow.neoecoae.network.MenuDataTransport;
import cn.dancingsnow.neoecoae.storage.NetworkInventoryCache;
import cn.dancingsnow.neoecoae.storage.StoragePerformance;
import cn.dancingsnow.neoecoae.storage.transfer.StorageBusTransferFilter;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Server-thread snapshots shared by viewers of the same storage. Published counters are never mutated. */
public record TerminalInventorySnapshot(KeyCounter inventory, Map<AEKey, ExactAmount> amounts) {
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    private static final Map<Object, Cache> CACHE = new WeakHashMap<>();
    private static final Map<KeyCounter, Stamp> STAMPS = new WeakHashMap<>();
    private static Object generation = new Object();

    /** The network owns caches that refer to its mounts; a global weak-key map must not retain that graph. */
    public interface Source {
        Cache neoecoae$terminalCache();
    }

    /** Implemented only by the optional AE2 counter bridge. Unmarked counters keep AE2's native behavior. */
    public interface SnapshotCounter {
        void neoecoae$useSnapshot(Contents contents);
        void neoecoae$materializeSnapshot();
        boolean neoecoae$wasMaterialized();
    }

    /** Immutable buckets: a new publication copies only buckets containing changed keys. */
    public static final class Contents {
        private static final int BUCKETS = 32;
        private final Object2LongOpenHashMap<AEKey>[] buckets;
        private final int size;

        private Contents(Object2LongOpenHashMap<AEKey>[] buckets, int size) {
            this.buckets = buckets;
            this.size = size;
        }

        @SuppressWarnings("unchecked")
        private static Contents copy(Object2LongOpenHashMap<AEKey> amounts) {
            if (StoragePerformance.ENABLED) StoragePerformance.add(StoragePerformance.Counter.TERMINAL_COW_COPIED_KEYS, amounts.size());
            var buckets = (Object2LongOpenHashMap<AEKey>[]) new Object2LongOpenHashMap<?>[BUCKETS];
            int expected = Math.max(2, (amounts.size() + BUCKETS - 1) / BUCKETS);
            for (var entry : Object2LongMaps.fastIterable(amounts)) {
                int bucket = bucket(entry.getKey());
                if (buckets[bucket] == null) buckets[bucket] = new Object2LongOpenHashMap<>(expected);
                buckets[bucket].put(entry.getKey(), entry.getLongValue());
            }
            return new Contents(buckets, amounts.size());
        }

        private Contents update(Object2LongOpenHashMap<AEKey> amounts, Set<AEKey> changed) {
            // Dense batches would otherwise copy the old buckets and overwrite most of them again.
            if ((long) changed.size() * 2 >= amounts.size()) return copy(amounts);
            var next = buckets.clone();
            int copied = 0;
            for (AEKey key : changed) {
                int bucket = bucket(key), mask = 1 << bucket;
                if ((copied & mask) == 0) {
                    if (StoragePerformance.ENABLED && buckets[bucket] != null)
                        StoragePerformance.add(StoragePerformance.Counter.TERMINAL_COW_COPIED_KEYS, buckets[bucket].size());
                    next[bucket] = buckets[bucket] == null ? new Object2LongOpenHashMap<>(2) : buckets[bucket].clone();
                    copied |= mask;
                }
                long amount = amounts.getLong(key);
                if (amount == 0) next[bucket].removeLong(key);
                else next[bucket].put(key, amount);
            }
            return new Contents(next, amounts.size());
        }

        private static int bucket(AEKey key) { return HashCommon.mix(key.hashCode()) & (BUCKETS - 1); }
        public long get(AEKey key) {
            var bucket = buckets[bucket(key)];
            return bucket == null ? 0 : bucket.getLong(key);
        }
        public int size() { return size; }

        /** Populates a fresh native counter after its snapshot marker has been removed. */
        public void copyInto(KeyCounter counter) {
            for (var bucket : buckets) {
                if (bucket != null) for (var entry : Object2LongMaps.fastIterable(bucket))
                    counter.set(entry.getKey(), entry.getLongValue());
            }
        }
    }

    public static TerminalInventorySnapshot sample(Object storage, long tick, boolean exact,
            Supplier<KeyCounter> listing) {
        return sample(storage, tick, exact, false, listing);
    }

    /** Planning freezes ordinary and exact stock in the same sampling pass. */
    public static TerminalInventorySnapshot sample(Object storage, long tick, boolean exact, boolean refreshExact,
            Supplier<KeyCounter> listing) {
        // Scoped callers must neither consume an unfiltered cached result nor publish their
        // filtered result to ordinary viewers, even when both calls occur in the same tick.
        if (ExactAmountCollector.isActive()) return new TerminalInventorySnapshot(listing.get(), Map.of());
        if (StorageBusTransferFilter.isActive()) return new Cache().sampleLegacy(exact, listing);
        Cache cached = storage instanceof Source source ? source.neoecoae$terminalCache()
                : CACHE.computeIfAbsent(storage, ignored -> new Cache());
        if (cached.generation != generation || tick < cached.tick) cached.reset();
        boolean collect = exact && (refreshExact || cached.exactTick == Long.MIN_VALUE
                || tick - cached.exactTick >= MenuDataTransport.UPDATE_INTERVAL);
        if (cached.snapshot == null || cached.tick != tick || collect) {
            TerminalInventorySnapshot next = null;
            if (storage instanceof Source && storage instanceof NetworkInventoryCache.Mounts mounts
                    && !ExactAmountCollector.isActive() && !StorageBusTransferFilter.isActive()) {
                try {
                    next = cached.sampleNetwork(mounts, collect);
                } catch (RuntimeException | Error failure) {
                    cached.invalidateNetwork();
                    throw failure;
                }
            }
            if (next == null) {
                cached.invalidateNetwork();
                next = cached.sampleLegacy(collect, listing);
            }
            cached.snapshot = next;
            cached.tick = tick;
            if (collect) cached.exactTick = tick;
        }
        return cached.snapshot;
    }

    /** A shared delta exists only for counters published by the same live network cache. */
    static Set<AEKey> changesBetween(KeyCounter previous, KeyCounter current) {
        Stamp before = STAMPS.get(previous), after = STAMPS.get(current);
        if (before == null || after == null) return null;
        Cache owner = after.owner.get();
        if (owner == null || owner != before.owner.get()) return null;
        if (previous == current) return Set.of();
        return owner.changesBetween(before.version, after.version, previous, current);
    }

    /** Unwrap only AE2's transparent supplier; filtered handlers keep separate caches. */
    public static MEStorage resolve(MEStorage storage) {
        MEStorage current = storage;
        for (int depth = 0; depth < 16; depth++) {
            if (!(current instanceof SupplierStorageAccessor supplier)) return current;
            MEStorage delegate = supplier.neoecoae$getDelegate();
            if (delegate == null || delegate == current) return current;
            current = delegate;
        }
        return storage;
    }

    public static void clear() {
        CACHE.clear();
        STAMPS.clear();
        generation = new Object();
    }

    private record Stamp(WeakReference<Cache> owner, long version) {}
    private record Change(long from, long to, Set<AEKey> keys) {}

    /** Internal state owned by a network, with bounded history for viewers at different versions. */
    public static final class Cache {
        private static final int MAX_HISTORY_KEYS = 65_536;
        private static final int MAX_HISTORY_FRAMES = 32;
        private static final int FULL_READ_PROBE_INTERVAL = 32;
        private final NetworkInventoryCache network = new NetworkInventoryCache(false);
        private final TerminalExactInventory precision = new TerminalExactInventory(network);
        private final KeyCounter working = new KeyCounter();
        private final Object2LongOpenHashMap<AEKey> amounts = new Object2LongOpenHashMap<>();
        private final ObjectLinkedOpenHashSet<AEKey> changed = new ObjectLinkedOpenHashSet<>();
        private final ArrayDeque<Change> history = new ArrayDeque<>();
        private final Long2ObjectOpenHashMap<Set<AEKey>> mergedChanges = new Long2ObjectOpenHashMap<>();
        private final WeakReference<Cache> reference = new WeakReference<>(this);
        private Object generation = TerminalInventorySnapshot.generation;
        private long tick = Long.MIN_VALUE;
        private long exactTick = Long.MIN_VALUE;
        private long version;
        private long historyFloor;
        private int historyKeys;
        private KeyCounter published;
        private Contents publishedContents;
        private int nativePublications;
        private int consecutiveFullReads;
        private TerminalInventorySnapshot snapshot;

        public Cache() {}

        private void reset() {
            invalidateNetwork();
            working.clear();
            working.removeEmptySubmaps();
            amounts.clear();
            snapshot = null;
            tick = exactTick = Long.MIN_VALUE;
            generation = TerminalInventorySnapshot.generation;
        }

        private void invalidateNetwork() {
            network.invalidate();
            precision.reset();
            if (published != null) {
                published = null;
                publishedContents = null;
                nativePublications = 0;
                consecutiveFullReads = 0;
                historyFloor = ++version;
                history.clear();
                historyKeys = 0;
                mergedChanges.clear();
            }
        }

        private TerminalInventorySnapshot sampleNetwork(NetworkInventoryCache.Mounts storage, boolean collect) {
            long measured = StoragePerformance.start();
            try {
                for (var iterator = changed.iterator(); iterator.hasNext();) {
                    iterator.next();
                    iterator.remove();
                }
                precision.begin(collect);
                if (!network.refresh(storage, working, amounts, (key, amount) -> changed.add(key), precision::list)) return null;
                Map<AEKey, ExactAmount> exact = snapshot == null ? Map.of() : snapshot.amounts();
                if (collect) {
                    exact = precision.sample(storage, working, exact);
                    if (exact == null) return null;
                }
                if (published == null || !changed.isEmpty()) publish();
                if (snapshot != null && snapshot.inventory() == published && snapshot.amounts() == exact) return snapshot;
                return new TerminalInventorySnapshot(published, exact);
            } finally {
                StoragePerformance.end(StoragePerformance.Phase.TERMINAL_CAPTURE, measured);
            }
        }

        private void publish() {
            StoragePerformance.count(StoragePerformance.Counter.TERMINAL_PUBLISH);
            consecutiveFullReads = (Object) published instanceof SnapshotCounter previous && previous.neoecoae$wasMaterialized()
                ? Math.min(2, consecutiveFullReads + 1) : 0;
            // A terminal's initial full sync alone should not disable sharing on subsequent delta updates.
            if (consecutiveFullReads == 2) {
                nativePublications = FULL_READ_PROBE_INTERVAL;
                publishedContents = null;
            }
            KeyCounter next = new KeyCounter();
            if ((Object) next instanceof SnapshotCounter snapshotCounter) {
                if (nativePublications > 0) {
                    nativePublications--;
                    // Full/fuzzy readers already need a native counter. Avoid building two representations.
                    for (var entry : Object2LongMaps.fastIterable(amounts)) next.set(entry.getKey(), entry.getLongValue());
                    if (StoragePerformance.ENABLED)
                        StoragePerformance.add(StoragePerformance.Counter.TERMINAL_COPIED_KEYS, amounts.size());
                } else {
                    publishedContents = publishedContents == null ? Contents.copy(amounts) : publishedContents.update(amounts, changed);
                    snapshotCounter.neoecoae$useSnapshot(publishedContents);
                }
            } else {
                // Unknown AE2 versions keep the public-API copy; no reflective access to native internals.
                if (StoragePerformance.ENABLED) StoragePerformance.add(StoragePerformance.Counter.TERMINAL_COPIED_KEYS, working.size());
                next.addAll(working);
            }
            long from = version++;
            if (changed.size() > MAX_HISTORY_KEYS) {
                history.clear();
                historyKeys = 0;
                historyFloor = version;
            } else {
                Set<AEKey> keys = Set.copyOf(changed);
                history.addLast(new Change(from, version, keys));
                historyKeys += keys.size();
                while (history.size() > MAX_HISTORY_FRAMES || historyKeys > MAX_HISTORY_KEYS) {
                    Change removed = history.removeFirst();
                    historyKeys -= removed.keys.size();
                    historyFloor = removed.to;
                }
            }
            mergedChanges.clear();
            published = next;
            STAMPS.put(next, new Stamp(reference, version));
        }

        private Set<AEKey> changesBetween(long before, long after, KeyCounter old, KeyCounter current) {
            if (after != version || before < historyFloor || before > after) return null;
            Change latest = history.peekLast();
            if (latest != null && latest.from == before && latest.to == after) return latest.keys;
            Set<AEKey> cached = mergedChanges.get(before);
            if (cached != null) return cached;
            ObjectOpenHashSet<AEKey> keys = new ObjectOpenHashSet<>();
            for (Change change : history) {
                if (change.to > before && change.to <= after) keys.addAll(change.keys);
            }
            keys.removeIf(key -> old.get(key) == current.get(key));
            Set<AEKey> result = Set.copyOf(keys);
            if (mergedChanges.size() >= MAX_HISTORY_FRAMES) mergedChanges.clear();
            mergedChanges.put(before, result);
            return result;
        }

        private TerminalInventorySnapshot sampleLegacy(boolean collect, Supplier<KeyCounter> listing) {
            KeyCounter inventory;
            Map<AEKey, ExactAmount> exact = snapshot == null ? Map.of() : snapshot.amounts();
            if (collect) {
                ExactAmountCollector.begin();
                try {
                    inventory = listing.get();
                    Map<AEKey, ExactAmount> collected = ExactAmountCollector.finish();
                    Map<AEKey, ExactAmount> needed = new HashMap<>();
                    collected.forEach((key, amount) -> {
                        if (amount.infinite() || amount.value().compareTo(MAX_LONG) > 0
                                || inventory.get(key) != amount.value().longValueExact()) needed.put(key, amount);
                    });
                    if (!needed.equals(exact)) exact = Map.copyOf(needed);
                } catch (RuntimeException | Error failure) {
                    ExactAmountCollector.abort();
                    throw failure;
                }
            } else inventory = listing.get();
            return new TerminalInventorySnapshot(inventory, exact);
        }
    }
}
