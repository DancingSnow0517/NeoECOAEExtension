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
    private MEStorage previousDestination;
    private long nextOutputTick = Long.MIN_VALUE;
    private long lastOutputTick = Long.MIN_VALUE;
    private boolean transferring;
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
        previousDestination = null;
        nextOutputTick = lastOutputTick = Long.MIN_VALUE;
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
        return transfer(grid, hostStorage, fillHost, ignoreCreativeInput, excludeStorageBuses,
            source, filter, Long.MIN_VALUE, 1, Long.MAX_VALUE);
    }

    /** Output batching retains all resources in the host's existing authoritative storage. */
    public long transfer(IGrid grid, MEStorage hostStorage, boolean fillHost,
                         boolean ignoreCreativeInput, boolean excludeStorageBuses,
                         IActionSource source, Predicate<AEKey> filter,
                         long tick, int outputIntervalTicks, long outputFlushThreshold) {
        if (transferring) return 0;
        transferring = true;
        try {
            return StorageBusTransferFilter.transfer(excludeStorageBuses,
                () -> transferContents(grid, hostStorage, fillHost, ignoreCreativeInput, excludeStorageBuses,
                    source, filter, tick, Math.max(1, Math.min(20, outputIntervalTicks)), Math.max(1, outputFlushThreshold)));
        } finally {
            transferring = false;
        }
    }

    private long transferContents(IGrid grid, MEStorage hostStorage, boolean fillHost,
                                  boolean ignoreCreativeInput, boolean excludeStorageBuses,
                                  IActionSource source, Predicate<AEKey> filter,
                                  long tick, int outputIntervalTicks, long outputFlushThreshold) {
        MEStorage networkStorage = grid.getStorageService().getInventory();
        MEStorage from;
        MEStorage destination;
        KeyCounter available;
        boolean discoverAll = true;
        boolean stableFilter = filter instanceof StorageInterfaceFilter && filter == previousFilter;
        from = fillHost ? networkStorage : hostStorage;
        destination = fillHost ? hostStorage : networkStorage;
        boolean scheduled = !fillHost && tick != Long.MIN_VALUE && outputIntervalTicks > 1;
        if (destination != previousDestination || tick < lastOutputTick) nextOutputTick = Long.MIN_VALUE;
        previousDestination = destination;
        lastOutputTick = tick;
        boolean outputDue = !scheduled || tick >= nextOutputTick;
        if (from != previousSource) {
            previousSource = from;
            nextOutputTick = Long.MIN_VALUE;
            outputDue = true;
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
        boolean attempted = false;
        int keysRemaining = Math.min(KEYS_PER_TICK, queue.size());
        while (keysRemaining-- > 0 && !queue.isEmpty()) {
            var key = queue.first();
            // Also recheck pending keys after a filter or interface change.
            if (!filter.test(key)) { queue.removeFirst(); continue; }
            long amount = availableAmount(available, key);
            if (amount <= 0L) { queue.removeFirst(); continue; }
            // Keep the queue position while waiting: rotating on skipped ticks can starve some keys.
            if (!outputDue && amount < outputFlushThreshold) break;
            queue.removeFirst();
            attempted = true;

            long possible = destination.insert(key, amount, Actionable.SIMULATE, source);
            if (possible <= 0L) {
                enqueue(key);
                continue;
            }

            // Do not remove stock from a possibly extract-only source just to discover there is no power.
            // poweredInsert still rechecks power and acceptance after extraction, as required by AE2.
            double energyFactor = Math.max(1.0, key.getAmountPerOperation());
            double power = grid.getEnergyService().extractAEPower(possible / energyFactor,
                Actionable.SIMULATE, appeng.api.config.PowerMultiplier.CONFIG);
            possible = Math.min(possible, (long) (power * energyFactor + 0.9));
            if (possible <= 0) { enqueue(key); break; }

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
            // Keep a candidate until the next refreshed listing says it is empty. Consumption and
            // replenishment can restore the same total before then, producing no aggregate delta.
            enqueue(key);
        }
        if (scheduled && attempted) nextOutputTick = tick > Long.MAX_VALUE - outputIntervalTicks
            ? Long.MAX_VALUE : tick + outputIntervalTicks;
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
        // This counter is only a public-API scratch buffer; quantities live in listedAmounts.
        outputListing.clear();
        listedSource = source; listedIdentity = identity; listedBusScope = busScope;
        // A scoped listing must not become the next ordinary caller's versioned baseline.
        listedVersion = ExactAmountCollector.isActive() ? StorageListingVersion.UNKNOWN : version;
        return true;
    }

}
