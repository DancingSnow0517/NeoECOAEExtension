package cn.dancingsnow.neoecoae.impl.storage.transfer;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;

/** AE2 ME IO Port-style full-key transfer with a small round-robin key budget. */
public final class ECOIOPortTransfer {
    private static final int KEYS_PER_TICK = 5;
    private final ArrayDeque<AEKey> queue = new ArrayDeque<>();
    private final Set<AEKey> queued = new HashSet<>();
    private MEStorage previousSource;

    public long transfer(IGrid grid, MEStorage hostStorage, boolean fillHost, IActionSource source) {
        MEStorage networkStorage = grid.getStorageService().getInventory();
        MEStorage from;
        MEStorage destination;
        KeyCounter available;
        if (fillHost) {
            from = networkStorage;
            destination = hostStorage;
            available = grid.getStorageService().getCachedInventory();
        } else {
            from = hostStorage;
            destination = networkStorage;
            available = new KeyCounter();
            hostStorage.getAvailableStacks(available);
        }

        if (from != previousSource) {
            previousSource = from;
            queue.clear();
            queued.clear();
        }
        for (var entry : available) {
            if (entry.getLongValue() > 0L && queued.add(entry.getKey())) {
                queue.addLast(entry.getKey());
            }
        }

        long moved = 0L;
        int keysRemaining = Math.min(KEYS_PER_TICK, queue.size());
        while (keysRemaining-- > 0) {
            var key = queue.removeFirst();
            queued.remove(key);
            long amount = available.get(key);
            if (amount <= 0L) continue;

            long possible = destination.insert(key, amount, Actionable.SIMULATE, source);
            if (possible <= 0L) {
                enqueue(key);
                continue;
            }

            long extracted = from.extract(key, possible, Actionable.MODULATE, source);
            if (extracted <= 0L) {
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
        if (queued.add(key)) queue.addLast(key);
    }
}
