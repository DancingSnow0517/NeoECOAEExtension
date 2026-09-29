package cn.dancingsnow.neoecoae.impl.storage.transfer;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Forge AE2 IO port transfer order, with a round-robin key budget. */
public final class ECOIOPortTransfer {
    private static final int KEYS_PER_TICK = 5;
    private final ArrayDeque<AEKey> queue = new ArrayDeque<>();
    private final Set<AEKey> queued = new HashSet<>();
    private MEStorage previousSource;
    private Object previousHostIdentity;
    private boolean previousFillHost;

    public long transfer(IGrid grid, MEStorage hostStorage, boolean fillHost,
            boolean ignoreCreativeInput, IActionSource source) {
        MEStorage network = grid.getStorageService().getInventory();
        MEStorage from = fillHost ? network : hostStorage;
        MEStorage destination = fillHost ? hostStorage : network;
        KeyCounter available = new KeyCounter();
        from.getAvailableStacks(available);
        Object hostIdentity = hostStorage instanceof HostInventories host ? host.inventories() : hostStorage;
        if (!hostIdentity.equals(previousHostIdentity) || previousFillHost != fillHost
                || fillHost && from != previousSource) {
            previousSource = from;
            previousHostIdentity = hostIdentity;
            previousFillHost = fillHost;
            queue.clear();
            queued.clear();
        }
        for (var entry : available) {
            if (entry.getLongValue() > 0L) enqueue(entry.getKey());
        }

        long moved = 0L;
        int keysRemaining = Math.min(KEYS_PER_TICK, queue.size());
        while (keysRemaining-- > 0) {
            AEKey key = queue.removeFirst();
            queued.remove(key);
            long amount = available.get(key);
            if (amount <= 0L) continue;
            long possible;
            try (StorageExtractionExclusions ignored = !fillHost
                    ? StorageExtractionExclusions.open(hostStorage instanceof HostInventories host
                            ? host.inventories() : List.of(hostStorage)) : StorageExtractionExclusions.open(List.of())) {
                possible = checked(amount, destination.insert(key, amount, Actionable.SIMULATE, source));
            }
            if (possible <= 0L) {
                enqueue(key);
                continue;
            }
            long extracted;
            try (StorageExtractionExclusions ignored = fillHost
                    ? StorageExtractionExclusions.open(hostStorage instanceof HostInventories host
                            ? host.inventories() : List.of(hostStorage)) : StorageExtractionExclusions.open(List.of())) {
                if (fillHost && !ignoreCreativeInput) {
                    try (var allowed = ECOCreativeExtractionFilter.allowAll()) {
                        extracted = checked(possible, from.extract(key, possible, Actionable.MODULATE, source));
                    }
                } else {
                    extracted = checked(possible, fillHost
                            ? ECOCreativeExtractionFilter.extract(from, key, possible, source)
                            : from.extract(key, possible, Actionable.MODULATE, source));
                }
            }
            if (extracted <= 0L) continue;
            long inserted;
            try (StorageExtractionExclusions ignored = !fillHost
                    ? StorageExtractionExclusions.open(hostStorage instanceof HostInventories host
                            ? host.inventories() : List.of(hostStorage)) : StorageExtractionExclusions.open(List.of())) {
                inserted = checked(extracted, StorageHelper.poweredInsert(
                        grid.getEnergyService(), destination, key, extracted, source));
            }
            if (inserted < extracted) {
                long remainder = extracted - inserted;
                long restored;
                try (StorageExtractionExclusions ignored = StorageExtractionExclusions.open(
                        fillHost && hostStorage instanceof HostInventories host
                                ? host.inventories() : List.of())) {
                    restored = checked(remainder, fillHost && ignoreCreativeInput
                            ? ECOCreativeExtractionFilter.returnRemainder(from, key, remainder, source)
                            : from.insert(key, remainder, Actionable.MODULATE, source));
                }
                if (restored != remainder) throw new IllegalStateException(
                        "ME IO transfer return incomplete: " + restored + "/" + remainder);
            }
            moved = Long.MAX_VALUE - moved < inserted ? Long.MAX_VALUE : moved + inserted;
            if (inserted < amount) enqueue(key);
        }
        return moved;
    }

    private void enqueue(AEKey key) {
        if (queued.add(key)) queue.addLast(key);
    }

    private static long checked(long offered, long result) {
        if (result < 0L || result > offered) throw new IllegalStateException(
                "Invalid ME IO transfer acknowledgement: " + result + "/" + offered);
        return result;
    }

    /** Exposes exact host mounts for self-network exclusion without depending on a wrapper's identity. */
    public interface HostInventories {
        List<MEStorage> inventories();
    }
}
