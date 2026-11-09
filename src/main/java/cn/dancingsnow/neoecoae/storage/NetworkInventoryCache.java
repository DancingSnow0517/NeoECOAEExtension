package cn.dancingsnow.neoecoae.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountCollector;
import cn.dancingsnow.neoecoae.crafting.display.terminal.StorageListingAccumulator;
import cn.dancingsnow.neoecoae.storage.transfer.StorageBusTransferFilter;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.math.BigInteger;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.ObjLongConsumer;

/**
 * Keeps AE2's per-grid cached inventory current by listing each mount separately and applying only
 * the per-key differences. A mount with an unchanged {@link StorageListingVersion} is not listed at all, and a
 * {@link StorageListingDelta} mount reports only its changed keys; every other mount, including all third-party
 * storage, is still listed on each refresh.
 * Server-thread owned, apart from {@link #invalidate()}.
 */
public final class NetworkInventoryCache {
    /** Periodic stock-equivalent rebuild. It also repairs callers that modified AE2's published counter. */
    static final int FULL_REBUILD_INTERVAL = 200;
    private static final int TRIM_THRESHOLD = 4096;

    /** Implemented by AE2's network storage. */
    public interface Mounts {
        /** Changes whenever an inventory is mounted or unmounted. */
        long neoecoae$mountGeneration();

        /** Passes the mounted inventories in AE2's listing order while they are locked; false if already in use. */
        boolean neoecoae$withMounts(Consumer<List<MEStorage>> action);

        /** True only when the current locked mount list has already been checked for duplicate identities. */
        default boolean neoecoae$distinctMountsGuaranteed() { return false; }
    }

    private static final class Mount {
        private Object2LongOpenHashMap<AEKey> content = new Object2LongOpenHashMap<>();
        private final StorageListingVersion versioned;
        private final StorageListingDelta delta;
        private long version;
        private Object identity;
        private int epoch;

        Mount(MEStorage inventory) {
            versioned = inventory instanceof StorageListingVersion value ? value : null;
            delta = inventory instanceof StorageListingDelta value ? value : null;
        }

        long currentVersion() { return versioned == null ? StorageListingVersion.UNKNOWN : versioned.neoecoae$listingVersion(); }
        Object currentIdentity() { return versioned == null ? this : versioned.neoecoae$listingIdentity(); }
    }

    private final Reference2ObjectOpenHashMap<MEStorage, Mount> mounts = new Reference2ObjectOpenHashMap<>();
    /** Exact network totals for keys whose sum exceeds {@code long}; their published amount saturates. */
    private final Object2ObjectOpenHashMap<AEKey, PlannerAmount> oversized = new Object2ObjectOpenHashMap<>();
    /** Dense pending batches avoid hash-table scans and per-key linked-list maintenance. */
    private final Object2IntOpenHashMap<AEKey> pendingEpochs = new Object2IntOpenHashMap<>();
    private final ObjectArrayList<AEKey> pendingKeys = new ObjectArrayList<>();
    /** Old totals during update/rollback, then committed totals during notification. */
    private final LongArrayList pendingAmounts = new LongArrayList();
    private final ReferenceOpenHashSet<MEStorage> seen = new ReferenceOpenHashSet<>();
    private final StorageListingAccumulator listing = new StorageListingAccumulator();
    private KeyCounter scratch = new KeyCounter();
    private Object2LongOpenHashMap<AEKey> flatScratch = new Object2LongOpenHashMap<>();
    // Volatile: a stray off-thread AE2 refresh invalidates the server thread's per-mount state.
    private volatile boolean valid;
    private boolean refreshed;
    private boolean refreshing;
    private long generation;
    private int epoch;
    private int refreshes;
    private final boolean repairPublished;
    private final boolean allowBusScopedViews;
    private boolean busScoped;

    public NetworkInventoryCache() { this(true); }

    /** Private working counters are never exposed to other callers and do not need periodic repair. */
    public NetworkInventoryCache(boolean repairPublished) { this(repairPublished, false); }

    /** A private IO-port cache may retain a bus-filtered view, but never share it with ordinary readers. */
    public NetworkInventoryCache(boolean repairPublished, boolean allowBusScopedViews) {
        this.repairPublished = repairPublished;
        this.allowBusScopedViews = allowBusScopedViews;
    }

    /** Forgets all per-mount state; the next refresh rebuilds everything. */
    public void invalidate() {
        valid = false;
    }

    /**
     * Brings {@code published} and AE2's private {@code amounts} up to date, reporting every changed total to
     * {@code changes}. Returns false, changing nothing, when this refresh must use AE2's own full listing.
     */
    public boolean refresh(Mounts storage, KeyCounter published, Object2LongMap<AEKey> amounts,
            ObjLongConsumer<AEKey> changes) {
        return refresh(storage, published, amounts, changes, null);
    }

    /** Private IO consumers need quantities and changes, but no grouped/fuzzy counter. */
    public boolean refresh(Mounts storage, Object2LongMap<AEKey> amounts, ObjLongConsumer<AEKey> changes) {
        return refresh(storage, null, amounts, changes, null);
    }

    /** An optional listing callback can collect exact values during the same required full traversal. */
    public boolean refresh(Mounts storage, KeyCounter published, Object2LongMap<AEKey> amounts,
            ObjLongConsumer<AEKey> changes, BiConsumer<MEStorage, KeyCounter> fullListing) {
        if (refreshing) return false;
        long measured = StoragePerformance.start();
        // Both scopes change what a listing returns for the current caller only.
        boolean scoped = StorageBusTransferFilter.isActive();
        if (ExactAmountCollector.isActive() || scoped && !allowBusScopedViews) {
            StoragePerformance.count(StoragePerformance.Counter.CACHE_SCOPED_FALLBACK);
            valid = false;
            return false;
        }
        if (scoped != busScoped) {
            valid = false;
            busScoped = scoped;
        }
        refreshed = false;
        refreshing = true;
        try {
            boolean locked = storage.neoecoae$withMounts(inventories -> {
            if (!storage.neoecoae$distinctMountsGuaranteed() && !distinct(inventories)) return;
            long mountGeneration = storage.neoecoae$mountGeneration();
            if (!valid || repairPublished && ++refreshes >= FULL_REBUILD_INTERVAL) {
                if (valid) StoragePerformance.count(StoragePerformance.Counter.CACHE_REPAIR);
                valid = false;
                refreshes = 0;
                rebuild(inventories, published, amounts, changes, fullListing);
            } else {
                try {
                    update(inventories, mountGeneration != generation, amounts, fullListing);
                } catch (RuntimeException | Error failure) {
                    revert(amounts);
                    valid = false;
                    throw failure;
                }
                publish(published, amounts, changes);
            }
            generation = mountGeneration;
            valid = true;
            refreshed = true;
            });
            if (!locked || !refreshed) valid = false;
            return locked && refreshed;
        } catch (RuntimeException | Error failure) {
            valid = false;
            throw failure;
        } finally {
            refreshing = false;
            StoragePerformance.end(StoragePerformance.Phase.CACHE_REFRESH, measured);
        }
    }

    private boolean distinct(List<MEStorage> inventories) {
        // The same inventory mounted twice is counted twice by AE2; leave that rare case to AE2.
        try {
            for (MEStorage inventory : inventories) {
                if (!seen.add(inventory)) return false;
            }
            return true;
        } finally {
            seen.clear();
        }
    }

    private void rebuild(List<MEStorage> inventories, KeyCounter published, Object2LongMap<AEKey> amounts,
            ObjLongConsumer<AEKey> changes, BiConsumer<MEStorage, KeyCounter> fullListing) {
        mounts.clear();
        oversized.clear();
        pendingKeys.clear();
        pendingAmounts.clear();
        Object2LongOpenHashMap<AEKey> totals = flatScratch;
        totals.clear();
        int current = nextEpoch();
        for (MEStorage inventory : inventories) {
            Mount mount = new Mount(inventory);
            mount.version = mount.currentVersion();
            mount.identity = mount.currentIdentity();
            mount.epoch = current;
            list(inventory, mount.content, fullListing);
            mounts.put(inventory, mount);
            for (var entry : Object2LongMaps.fastIterable(mount.content)) {
                AEKey key = entry.getKey();
                totals.put(key, combine(key, totals.getLong(key), entry.getLongValue()));
            }
        }

        // Capture the old totals, then publish both representations before any watcher can reenter.
        for (var entry : Object2LongMaps.fastIterable(totals)) {
            long before = amounts.getLong(entry.getKey());
            if (entry.getLongValue() != before) recordPending(entry.getKey(), entry.getLongValue());
        }
        for (AEKey key : amounts.keySet()) {
            if (totals.getLong(key) == 0) recordPending(key, 0);
        }
        if (published != null) {
            published.clear();
            for (var entry : Object2LongMaps.fastIterable(totals)) published.set(entry.getKey(), entry.getLongValue());
            published.removeEmptySubmaps();
        }
        amounts.clear();
        amounts.putAll(totals);
        totals.clear();
        notifyChanges(changes, amounts.size(), pendingKeys.size());
    }

    private void update(List<MEStorage> inventories, boolean remounted, Object2LongMap<AEKey> amounts,
            BiConsumer<MEStorage, KeyCounter> fullListing) {
        int current = nextEpoch();
        for (MEStorage inventory : inventories) {
            Mount mount = mounts.get(inventory);
            boolean added = mount == null;
            if (added) mount = new Mount(inventory);
            long version = mount.currentVersion();
            Object identity = mount.currentIdentity();
            boolean viewChanged = !added && identity != mount.identity;
            if (added) {
                list(inventory, mount.content, fullListing);
                mounts.put(inventory, mount);
                for (var entry : Object2LongMaps.fastIterable(mount.content)) adjust(amounts, entry.getKey(), entry.getLongValue());
            } else if (remounted || viewChanged || version == StorageListingVersion.UNKNOWN || version != mount.version) {
                if (remounted) StoragePerformance.count(StoragePerformance.Counter.CACHE_REMOUNT);
                if (version == StorageListingVersion.UNKNOWN) StoragePerformance.count(StoragePerformance.Counter.CACHE_UNKNOWN_VERSION);
                if (!remounted && !viewChanged && mount.delta != null
                        && applyChanges(mount.delta, mount, version, amounts)) {
                    mount.version = version;
                    mount.epoch = current;
                    continue;
                }
                StoragePerformance.count(mount.delta == null ? StoragePerformance.Counter.CACHE_NO_DELTA
                    : StoragePerformance.Counter.CACHE_EXPIRED_DELTA);
                Object2LongOpenHashMap<AEKey> fresh = flatScratch;
                fresh.clear();
                list(inventory, fresh, fullListing);
                Object2LongOpenHashMap<AEKey> old = mount.content;
                // Contents never hold zeros, so a missing key reads as 0 on either side.
                for (var entry : Object2LongMaps.fastIterable(old)) {
                    long now = fresh.getLong(entry.getKey());
                    if (now != entry.getLongValue()) adjust(amounts, entry.getKey(), now - entry.getLongValue());
                }
                for (var entry : Object2LongMaps.fastIterable(fresh)) {
                    if (old.getLong(entry.getKey()) == 0) adjust(amounts, entry.getKey(), entry.getLongValue());
                }
                old.clear();
                flatScratch = old;
                mount.content = fresh;
            } else {
                StoragePerformance.count(StoragePerformance.Counter.CACHE_UNCHANGED);
            }
            mount.version = version;
            mount.identity = identity;
            mount.epoch = current;
        }
        if (mounts.size() != inventories.size()) {
            for (var iterator = mounts.values().iterator(); iterator.hasNext();) {
                Mount mount = iterator.next();
                if (mount.epoch == current) continue;
                for (var entry : Object2LongMaps.fastIterable(mount.content)) adjust(amounts, entry.getKey(), -entry.getLongValue());
                iterator.remove();
            }
        }
    }

    /** Applies a mount's reported key changes; false when it must be listed in full instead. */
    private boolean applyChanges(StorageListingDelta delta, Mount mount, long version, Object2LongMap<AEKey> amounts) {
        if (version < 0L || mount.version < 0L
                || version < mount.version) {
            return false;
        }
        Object2LongOpenHashMap<AEKey> content = mount.content;
        boolean applied = delta.neoecoae$visitListingChanges(mount.version, version, (key, now) -> {
            StoragePerformance.count(StoragePerformance.Counter.CACHE_CHANGED_KEYS);
            long before = content.getLong(key);
            if (now == before) return;
            adjust(amounts, key, now - before);
            // Contents never hold zeros, matching a full listing after removeZeros().
            if (now == 0L) content.removeLong(key);
            else content.put(key, now);
        });
        if (applied) StoragePerformance.count(StoragePerformance.Counter.CACHE_DELTA);
        return applied;
    }

    private void publish(KeyCounter published, Object2LongMap<AEKey> amounts, ObjLongConsumer<AEKey> changes) {
        boolean removed = false;
        int touched = pendingKeys.size(), changed = 0;
        for (int i = 0; i < touched; i++) {
            AEKey key = pendingKeys.get(i);
            long next = amounts.getLong(key);
            if (next == pendingAmounts.getLong(i)) continue;
            if (published != null && next == 0) {
                published.remove(key);
                removed = true;
            } else if (published != null) {
                published.set(key, next);
            }
            pendingKeys.set(changed, key);
            pendingAmounts.set(changed++, next);
        }
        pendingKeys.size(changed);
        pendingAmounts.size(changed);
        if (removed) published.removeEmptySubmaps();
        // A watcher may synchronously read another changed key: publish the entire batch first.
        notifyChanges(changes, amounts.size(), touched);
    }

    private void notifyChanges(ObjLongConsumer<AEKey> changes, int liveResources, int touched) {
        try {
            for (int i = 0; i < pendingKeys.size(); i++) changes.accept(pendingKeys.get(i), pendingAmounts.getLong(i));
        } catch (RuntimeException | Error failure) {
            // The batch is already published. Do not reuse partially delivered notification state.
            valid = false;
            throw failure;
        } finally {
            clearPending(liveResources, touched);
        }
    }

    private void revert(Object2LongMap<AEKey> amounts) {
        int touched = pendingKeys.size();
        for (int i = 0; i < touched; i++) {
            long before = pendingAmounts.getLong(i);
            if (before == 0) amounts.removeLong(pendingKeys.get(i));
            else amounts.put(pendingKeys.get(i), before);
        }
        clearPending(amounts.size(), touched);
    }

    private void recordPending(AEKey key, long amount) {
        if (pendingEpochs.put(key, epoch) == epoch) return;
        pendingKeys.add(key);
        pendingAmounts.add(amount);
    }

    private int nextEpoch() {
        int current = ++epoch;
        // The absent marker must differ even in the epoch that wraps through zero.
        pendingEpochs.defaultReturnValue(current - 1);
        if (current == 0) pendingEpochs.clear();
        return current;
    }

    private void clearPending(int liveResources, int touched) {
        pendingKeys.clear();
        pendingAmounts.clear();
        // Bound old epoch marks under key churn without clearing a large hash table every tick.
        if (pendingEpochs.size() > Math.max((long) TRIM_THRESHOLD, 2L * liveResources) || liveResources == 0) {
            pendingEpochs.clear();
            pendingEpochs.trim();
            int retained = liveResources == 0 ? 16 : Math.max(16, touched);
            pendingKeys.trim(retained);
            pendingAmounts.trim(retained);
        }
    }

    private void adjust(Object2LongMap<AEKey> amounts, AEKey key, long delta) {
        long current = amounts.getLong(key);
        recordPending(key, current);
        long next = combine(key, current, delta);
        if (next == 0) amounts.removeLong(key);
        else amounts.put(key, next);
    }

    /** Adds a mount's contribution to a nonnegative total, saturating the published value like a full listing. */
    private long combine(AEKey key, long current, long delta) {
        PlannerAmount exact = oversized.isEmpty() ? null : oversized.get(key);
        if (exact == null) {
            long next = current + delta;
            if (((current ^ next) & (delta ^ next)) >= 0) return next;
            exact = PlannerAmount.of(current);
        }
        exact = exact.add(delta);
        if (exact.fitsLong()) {
            oversized.remove(key);
            return exact.longValue();
        }
        oversized.put(key, exact);
        return Long.MAX_VALUE;
    }

    private void list(MEStorage inventory, Object2LongOpenHashMap<AEKey> out, BiConsumer<MEStorage, KeyCounter> fullListing) {
        StoragePerformance.count(StoragePerformance.Counter.CACHE_FULL);
        // Only the public listing boundary needs AE2's grouped/fuzzy counter. Mounts retain a flat long map.
        scratch.clear();
        if (fullListing == null) listing.append(inventory, scratch, inventory::getAvailableStacks);
        else fullListing.accept(inventory, scratch);
        scratch.removeZeros();
        out.clear();
        for (var entry : scratch) out.put(entry.getKey(), entry.getLongValue());
        if (StoragePerformance.ENABLED) StoragePerformance.add(StoragePerformance.Counter.CACHE_LISTED_KEYS, out.size());
    }

    public long mountedVersion(MEStorage inventory) {
        Mount mount = mounts.get(inventory);
        return mount == null ? StorageListingVersion.UNKNOWN : mount.version;
    }

    public Object mountedIdentity(MEStorage inventory) {
        Mount mount = mounts.get(inventory);
        return mount == null ? null : mount.identity;
    }

    public long mountedAmount(MEStorage inventory, AEKey key) {
        Mount mount = mounts.get(inventory);
        return mount == null ? 0 : mount.content.getLong(key);
    }

    public BigInteger listedTotal(AEKey key, long projection) {
        PlannerAmount total = oversized.get(key);
        return total == null ? BigInteger.valueOf(projection) : total.toBigInteger();
    }

    public void visitOversizedTotals(BiConsumer<AEKey, BigInteger> visitor) {
        oversized.forEach((key, amount) -> visitor.accept(key, amount.toBigInteger()));
    }
}
