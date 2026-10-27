package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountCollector;
import cn.dancingsnow.neoecoae.crafting.display.terminal.StorageListingAccumulator;
import cn.dancingsnow.neoecoae.impl.storage.transfer.StorageBusTransferFilter;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.math.BigInteger;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ObjLongConsumer;

/**
 * Keeps AE2's per-grid cached inventory current by listing each mount separately and applying only
 * the per-key differences. A mount with an unchanged {@link StorageListingVersion} is not listed at all;
 * every other mount, including all third-party storage, is still listed on each refresh.
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
    }

    private static final class Mount {
        private KeyCounter content = new KeyCounter();
        private long version;
        private int epoch;
    }

    private final Reference2ObjectOpenHashMap<MEStorage, Mount> mounts = new Reference2ObjectOpenHashMap<>();
    /** Exact network totals for keys whose sum exceeds {@code long}; their published amount saturates. */
    private final Object2ObjectOpenHashMap<AEKey, BigInteger> oversized = new Object2ObjectOpenHashMap<>();
    /** Published amount before the current refresh, for every key it touched. */
    private final Object2LongOpenHashMap<AEKey> previous = new Object2LongOpenHashMap<>();
    private final ReferenceOpenHashSet<MEStorage> seen = new ReferenceOpenHashSet<>();
    private final StorageListingAccumulator listing = new StorageListingAccumulator();
    private KeyCounter scratch = new KeyCounter();
    // Volatile: a stray off-thread AE2 refresh invalidates the server thread's per-mount state.
    private volatile boolean valid;
    private boolean refreshed;
    private long generation;
    private int epoch;
    private int refreshes;

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
        // Both scopes change what a listing returns for the current caller only.
        if (ExactAmountCollector.isActive() || StorageBusTransferFilter.isActive()) {
            valid = false;
            return false;
        }
        refreshed = false;
        boolean locked = storage.neoecoae$withMounts(inventories -> {
            if (!distinct(inventories)) return;
            long mountGeneration = storage.neoecoae$mountGeneration();
            if (!valid || ++refreshes >= FULL_REBUILD_INTERVAL) {
                valid = false;
                refreshes = 0;
                rebuild(inventories, published, amounts, changes);
            } else {
                try {
                    update(inventories, mountGeneration != generation, amounts);
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
            ObjLongConsumer<AEKey> changes) {
        mounts.clear();
        oversized.clear();
        previous.clear();
        published.clear();
        int current = ++epoch;
        for (MEStorage inventory : inventories) {
            Mount mount = new Mount();
            mount.version = versionOf(inventory);
            mount.epoch = current;
            list(inventory, mount.content);
            mounts.put(inventory, mount);
            for (var entry : mount.content) {
                AEKey key = entry.getKey();
                published.set(key, combine(key, published.get(key), entry.getLongValue()));
            }
        }
        published.removeEmptySubmaps();

        // Same notifications as AE2's full refresh.
        for (var entry : published) {
            if (entry.getLongValue() != amounts.getLong(entry.getKey())) {
                changes.accept(entry.getKey(), entry.getLongValue());
            }
        }
        for (AEKey key : amounts.keySet()) {
            if (published.get(key) == 0) changes.accept(key, 0);
        }
        amounts.clear();
        for (var entry : published) amounts.put(entry.getKey(), entry.getLongValue());
    }

    private void update(List<MEStorage> inventories, boolean remounted, Object2LongMap<AEKey> amounts) {
        int current = ++epoch;
        for (MEStorage inventory : inventories) {
            long version = versionOf(inventory);
            Mount mount = mounts.get(inventory);
            if (mount == null) {
                mount = new Mount();
                list(inventory, mount.content);
                mounts.put(inventory, mount);
                for (var entry : mount.content) adjust(amounts, entry.getKey(), entry.getLongValue());
            } else if (remounted || version == StorageListingVersion.UNKNOWN || version != mount.version) {
                KeyCounter fresh = scratch;
                fresh.clear();
                list(inventory, fresh);
                KeyCounter old = mount.content;
                // Contents never hold zeros, so a missing key reads as 0 on either side.
                for (var entry : old) {
                    long now = fresh.get(entry.getKey());
                    if (now != entry.getLongValue()) adjust(amounts, entry.getKey(), now - entry.getLongValue());
                }
                for (var entry : fresh) {
                    if (old.get(entry.getKey()) == 0) adjust(amounts, entry.getKey(), entry.getLongValue());
                }
                old.clear();
                scratch = old;
                mount.content = fresh;
            }
            mount.version = version;
            mount.epoch = current;
        }
        if (mounts.size() != inventories.size()) {
            for (var iterator = mounts.values().iterator(); iterator.hasNext();) {
                Mount mount = iterator.next();
                if (mount.epoch == current) continue;
                for (var entry : mount.content) adjust(amounts, entry.getKey(), -entry.getLongValue());
                iterator.remove();
            }
        }
    }

    private void publish(KeyCounter published, Object2LongMap<AEKey> amounts, ObjLongConsumer<AEKey> changes) {
        boolean removed = false;
        int touched = previous.size();
        for (var entry : Object2LongMaps.fastIterable(previous)) {
            AEKey key = entry.getKey();
            long next = amounts.getLong(key);
            if (next == 0) {
                published.remove(key);
                removed = true;
            } else {
                published.set(key, next);
            }
            if (next != entry.getLongValue()) changes.accept(key, next);
        }
        previous.clear();
        // A cleared open-hash table keeps its capacity, and iterating it would cost that much every tick.
        if (touched > TRIM_THRESHOLD) previous.trim();
        if (removed) published.removeEmptySubmaps();
    }

    private void revert(Object2LongMap<AEKey> amounts) {
        for (var entry : Object2LongMaps.fastIterable(previous)) {
            if (entry.getLongValue() == 0) amounts.removeLong(entry.getKey());
            else amounts.put(entry.getKey(), entry.getLongValue());
        }
        previous.clear();
    }

    private void adjust(Object2LongMap<AEKey> amounts, AEKey key, long delta) {
        long current = amounts.getLong(key);
        if (!previous.containsKey(key)) previous.put(key, current);
        long next = combine(key, current, delta);
        if (next == 0) amounts.removeLong(key);
        else amounts.put(key, next);
    }

    /** Adds a mount's contribution to a nonnegative total, saturating the published value like a full listing. */
    private long combine(AEKey key, long current, long delta) {
        BigInteger exact = oversized.isEmpty() ? null : oversized.get(key);
        if (exact == null) {
            long next = current + delta;
            if (((current ^ next) & (delta ^ next)) >= 0) return next;
            exact = BigInteger.valueOf(current);
        }
        exact = exact.add(BigInteger.valueOf(delta));
        if (exact.bitLength() < Long.SIZE) {
            oversized.remove(key);
            return exact.longValue();
        }
        oversized.put(key, exact);
        return Long.MAX_VALUE;
    }

    private void list(MEStorage inventory, KeyCounter out) {
        listing.append(inventory, out, inventory::getAvailableStacks);
        out.removeZeros();
    }

    private static long versionOf(MEStorage inventory) {
        return inventory instanceof StorageListingVersion versioned
            ? versioned.neoecoae$listingVersion() : StorageListingVersion.UNKNOWN;
    }
}
