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
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
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

    /**
     * Synchronously freezes quantities without publishing a terminal counter. The callback must only read
     * these server-thread maps and copy everything it retains; they remain owned by the network cache.
     */
    public static <T> T captureAmounts(Object storage, long tick, Supplier<KeyCounter> listing,
            BiFunction<Object2LongMap<AEKey>, Map<AEKey, ExactAmount>, T> capture) {
        TerminalInventorySnapshot fallback;
        if (!ExactAmountCollector.isActive() && !StorageBusTransferFilter.isActive()
                && storage instanceof Source && storage instanceof NetworkInventoryCache.Mounts mounts) {
            Cache cached = cache(storage, tick);
            try {
                if (cached.refreshNetwork(mounts, true, null)) {
                    // The next viewer publishes all changes accumulated since its previous snapshot.
                    cached.snapshot = null;
                    cached.tick = cached.exactTick = tick;
                    return capture.apply(cached.amounts, cached.exactAmounts);
                }
            } catch (RuntimeException | Error failure) {
                cached.invalidateNetwork();
                throw failure;
            }
            cached.invalidateNetwork();
            fallback = cached.sampleLegacy(true, listing);
        } else {
            fallback = sample(storage, tick, true, true, listing);
        }
        var amounts = new Object2LongOpenHashMap<AEKey>(fallback.inventory().size());
        for (var entry : fallback.inventory()) {
            if (entry.getLongValue() > 0) amounts.put(entry.getKey(), entry.getLongValue());
        }
        return capture.apply(amounts, fallback.amounts());
    }

    private static Cache cache(Object storage, long tick) {
        Cache cached = storage instanceof Source source ? source.neoecoae$terminalCache()
                : CACHE.computeIfAbsent(storage, ignored -> new Cache());
        if (cached.generation != generation || tick < cached.tick) cached.reset();
        return cached;
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
        Cache cached = cache(storage, tick);
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
            cached.exactAmounts = next.amounts();
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
        private TerminalInventorySnapshot snapshot;
        private Map<AEKey, ExactAmount> exactAmounts = Map.of();
        private boolean workingCurrent = true;

        public Cache() {}

        private void reset() {
            invalidateNetwork();
            working.clear();
            working.removeEmptySubmaps();
            amounts.clear();
            snapshot = null;
            exactAmounts = Map.of();
            workingCurrent = true;
            tick = exactTick = Long.MIN_VALUE;
            generation = TerminalInventorySnapshot.generation;
        }

        private void invalidateNetwork() {
            network.invalidate();
            precision.reset();
            workingCurrent = false;
            invalidatePublication();
        }

        private void invalidatePublication() {
            snapshot = null;
            changed.clear();
            if (published != null) {
                published = null;
                historyFloor = ++version;
                history.clear();
                historyKeys = 0;
                mergedChanges.clear();
            }
        }

        private TerminalInventorySnapshot sampleNetwork(NetworkInventoryCache.Mounts storage, boolean collect) {
            long measured = StoragePerformance.start();
            try {
                syncWorking();
                if (!refreshNetwork(storage, collect, working)) return null;
                if (published == null || !changed.isEmpty()) publish();
                if (snapshot != null && snapshot.inventory() == published && snapshot.amounts() == exactAmounts) return snapshot;
                return new TerminalInventorySnapshot(published, exactAmounts);
            } finally {
                StoragePerformance.end(StoragePerformance.Phase.TERMINAL_CAPTURE, measured);
            }
        }

        private boolean refreshNetwork(NetworkInventoryCache.Mounts storage, boolean collect, KeyCounter target) {
            precision.begin(collect);
            if (!network.refresh(storage, target, amounts, (key, amount) -> {
                if (target == null) workingCurrent = false;
                if (published != null) {
                    changed.add(key);
                    // Planners may run for a long time without a terminal viewer. Bound pending key churn.
                    if (changed.size() > MAX_HISTORY_KEYS) invalidatePublication();
                }
            }, precision::list)) return false;
            if (collect) {
                Map<AEKey, ExactAmount> exact = precision.sample(storage, amounts, exactAmounts);
                if (exact == null) return false;
                exactAmounts = exact;
            }
            return true;
        }

        /** Catch up only when planning advanced quantities without needing AE2's grouped counter. */
        private void syncWorking() {
            if (workingCurrent) return;
            boolean removedKeys = published == null;
            if (published == null) {
                working.clear();
                for (var entry : Object2LongMaps.fastIterable(amounts)) working.set(entry.getKey(), entry.getLongValue());
            } else for (AEKey key : changed) {
                long amount = amounts.getLong(key);
                if (amount == 0) { working.remove(key); removedKeys = true; }
                else working.set(key, amount);
            }
            if (removedKeys) working.removeEmptySubmaps();
            workingCurrent = true;
        }

        private void publish() {
            StoragePerformance.count(StoragePerformance.Counter.TERMINAL_PUBLISH);
            KeyCounter next = KeyCounterSnapshots.copy(working, published, changed);
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
            for (var iterator = changed.iterator(); iterator.hasNext();) {
                iterator.next();
                iterator.remove();
            }
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
            Map<AEKey, ExactAmount> exact = exactAmounts;
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
