package cn.dancingsnow.neoecoae.storage.transfer;

import java.util.function.Predicate;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import cn.dancingsnow.neoecoae.storage.StorageInterfaceFilter;
import cn.dancingsnow.neoecoae.storage.StorageListingDelta;
import cn.dancingsnow.neoecoae.storage.StorageListingVersion;
import cn.dancingsnow.neoecoae.storage.NetworkInventoryCache;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountCollector;

/** AE2 ME IO Port-style full-key transfer with a small round-robin key budget. */
public final class ECOIOPortTransfer {
    private static final int KEYS_PER_TICK = 5;
    private final ObjectLinkedOpenHashSet<AEKey> queue = new ObjectLinkedOpenHashSet<>();
    private MEStorage previousSource;
    private final KeyCounter outputListing = new KeyCounter();
    private MEStorage listedSource;
    private Object listedIdentity;
    private long listedVersion = StorageListingVersion.UNKNOWN;
    private boolean listedBusScope;
    private Predicate<AEKey> previousFilter;
    private NetworkInventoryCache mountListing;
    private final Object2LongOpenHashMap<AEKey> listedAmounts = new Object2LongOpenHashMap<>();

    /** Only candidate keys are buffered; all resources remain in the source's authoritative inventory. */
    public void reset() {
        queue.clear(); previousSource = listedSource = null;
        listedIdentity = null; listedVersion = StorageListingVersion.UNKNOWN; previousFilter = null;
        outputListing.clear(); outputListing.removeEmptySubmaps();
        mountListing = null;
        listedAmounts.clear();
    }

    public long transfer(IGrid grid, MEStorage hostStorage, boolean fillHost, IActionSource source) {
        return transfer(grid, hostStorage, fillHost, false, source);
    }

    public long transfer(IGrid grid, MEStorage hostStorage, boolean fillHost,
                         boolean ignoreCreativeInput, IActionSource source) {
        return transfer(grid, hostStorage, fillHost, ignoreCreativeInput, source, key -> true);
    }

    public long transfer(IGrid grid, MEStorage hostStorage, boolean fillHost,
                         boolean ignoreCreativeInput, IActionSource source, Predicate<AEKey> filter) {
        return transfer(grid, hostStorage, fillHost, ignoreCreativeInput, true, source, filter);
    }

    public long transfer(IGrid grid, MEStorage hostStorage, boolean fillHost,
                         boolean ignoreCreativeInput, boolean excludeStorageBuses,
                         IActionSource source, Predicate<AEKey> filter) {
        return StorageBusTransferFilter.transfer(excludeStorageBuses,
            () -> transferContents(grid, hostStorage, fillHost, ignoreCreativeInput, excludeStorageBuses, source, filter));
    }

    private long transferContents(IGrid grid, MEStorage hostStorage, boolean fillHost,
                                  boolean ignoreCreativeInput, boolean excludeStorageBuses,
                                  IActionSource source, Predicate<AEKey> filter) {
        MEStorage networkStorage = grid.getStorageService().getInventory();
        MEStorage from;
        MEStorage destination;
        KeyCounter available;
        boolean discoverAll = true;
        boolean stableFilter = filter instanceof StorageInterfaceFilter && filter == previousFilter;
        from = fillHost ? networkStorage : hostStorage;
        destination = fillHost ? hostStorage : networkStorage;
        if (from != previousSource) {
            previousSource = from;
            listedSource = null;
            queue.clear();
            stableFilter = false;
            if (mountListing != null) mountListing.invalidate();
        }

        if (from instanceof NetworkInventoryCache.Mounts mounts
                && refreshMountListing(mounts, stableFilter, filter)) {
            available = null;
            discoverAll = false;
        } else if (fillHost) {
            if (excludeStorageBuses) {
                // The shared cache includes bus inventories and must stay unchanged for ordinary network users.
                available = new KeyCounter();
                networkStorage.getAvailableStacks(available);
            } else {
                available = grid.getStorageService().getCachedInventory();
            }
        } else {
            available = null;
            discoverAll = refreshOutputListing(from, stableFilter, filter);
        }
        if (discoverAll || !stableFilter) {
            int listed = 0;
            Iterable<it.unimi.dsi.fastutil.objects.Object2LongMap.Entry<AEKey>> entries =
                available == null ? Object2LongMaps.fastIterable(listedAmounts) : available;
            for (var entry : entries) {
                if (entry.getLongValue() > 0L) {
                    listed++;
                    if (filter.test(entry.getKey())) queue.add(entry.getKey());
                }
            }
            // High key churn must not leave an unbounded backlog of vanished candidate IDs.
            if (queue.size() > listed) queue.removeIf(key -> availableAmount(available, key) <= 0);
        }
        previousFilter = filter;

        long moved = 0L;
        int keysRemaining = Math.min(KEYS_PER_TICK, queue.size());
        while (keysRemaining-- > 0) {
            var key = queue.removeFirst();
            // Also recheck pending keys after a filter or interface change.
            if (!filter.test(key)) continue;
            long amount = availableAmount(available, key);
            if (amount <= 0L) continue;

            long possible = destination.insert(key, amount, Actionable.SIMULATE, source);
            if (possible <= 0L) {
                enqueue(key);
                continue;
            }

            long extracted = fillHost && ignoreCreativeInput
                ? ECOCreativeExtractionFilter.extract(from, key, possible, source)
                : from.extract(key, possible, Actionable.MODULATE, source);
            if (extracted <= 0L) {
                // Listing versions describe quantities, not whether an extraction is temporarily permitted.
                enqueue(key);
                continue;
            }

            long inserted = StorageHelper.poweredInsert(
                grid.getEnergyService(), destination, key, extracted, source
            );
            if (inserted < extracted) {
                from.insert(key, extracted - inserted, Actionable.MODULATE, source);
            }
            if (inserted > 0L) {
                moved = inserted > Long.MAX_VALUE - moved ? Long.MAX_VALUE : moved + inserted;
            }
            if (inserted < amount) {
                enqueue(key);
            }
        }
        return moved;
    }

    private void enqueue(AEKey key) {
        queue.add(key);
    }

    private long availableAmount(KeyCounter external, AEKey key) {
        return external == null ? listedAmounts.getLong(key) : external.get(key);
    }

    private boolean refreshMountListing(NetworkInventoryCache.Mounts mounts, boolean discoverChanges,
            Predicate<AEKey> filter) {
        if (mountListing == null) {
            mountListing = new NetworkInventoryCache(false, true);
        }
        return mountListing.refresh(mounts, listedAmounts, (key, amount) -> {
            if (amount <= 0) queue.remove(key);
            else if (discoverChanges && filter.test(key)) enqueue(key);
        });
    }

    private boolean refreshOutputListing(MEStorage source, boolean discoverChanges, Predicate<AEKey> filter) {
        StorageListingVersion versioned = source instanceof StorageListingVersion value ? value : null;
        long version = versioned == null ? StorageListingVersion.UNKNOWN : versioned.neoecoae$listingVersion();
        Object identity = versioned == null ? source : versioned.neoecoae$listingIdentity();
        boolean busScope = StorageBusTransferFilter.isActive();
        boolean sameView = source == listedSource && identity == listedIdentity && busScope == listedBusScope
            && !ExactAmountCollector.isActive();
        if (sameView && version != StorageListingVersion.UNKNOWN) {
            if (version == listedVersion) return false;
            if (version >= 0 && listedVersion >= 0 && source instanceof StorageListingDelta delta
                && delta.neoecoae$visitListingChanges(listedVersion, version, (key, amount) -> {
                    if (amount == 0) { listedAmounts.removeLong(key); queue.remove(key); }
                    else listedAmounts.put(key, amount);
                    if (discoverChanges && amount > 0 && filter.test(key)) enqueue(key);
                })) {
                listedVersion = version;
                return false;
            }
        }
        outputListing.clear();
        source.getAvailableStacks(outputListing);
        outputListing.removeZeros();
        listedAmounts.clear();
        for (var entry : outputListing) listedAmounts.put(entry.getKey(), entry.getLongValue());
        // Known delta sources do not need to retain another full grouped quantity mirror.
        if (source instanceof StorageListingDelta && version >= 0) {
            outputListing.clear();
            outputListing.removeEmptySubmaps();
        }
        listedSource = source; listedIdentity = identity; listedBusScope = busScope;
        // A scoped listing must not become the next ordinary caller's versioned baseline.
        listedVersion = ExactAmountCollector.isActive() ? StorageListingVersion.UNKNOWN : version;
        return true;
    }

}
