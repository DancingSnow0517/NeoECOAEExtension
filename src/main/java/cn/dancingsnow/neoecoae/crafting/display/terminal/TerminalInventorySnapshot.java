package cn.dancingsnow.neoecoae.crafting.display.terminal;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.SupplierStorageAccessor;
import cn.dancingsnow.neoecoae.network.MenuDataTransport;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Server-thread snapshots shared by viewers of the same storage. Published counters are read-only. */
public record TerminalInventorySnapshot(KeyCounter inventory, Map<AEKey, ExactAmount> amounts) {
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    private static final Map<Object, Cached> CACHE = new WeakHashMap<>();

    public static TerminalInventorySnapshot sample(Object storage, long tick, boolean exact,
            Supplier<KeyCounter> listing) {
        Cached cached = CACHE.computeIfAbsent(storage, ignored -> new Cached());
        boolean collect = exact && (cached.exactTick == null || tick < cached.exactTick
            || tick - cached.exactTick >= MenuDataTransport.UPDATE_INTERVAL);
        if (cached.snapshot == null || cached.tick != tick || collect) {
            KeyCounter inventory;
            Map<AEKey, ExactAmount> amounts = cached.snapshot == null ? Map.of() : cached.snapshot.amounts();
            if (collect) {
                ExactAmountCollector.begin();
                try {
                    inventory = listing.get();
                    Map<AEKey, ExactAmount> collected = ExactAmountCollector.finish();
                    Map<AEKey, ExactAmount> needed = new HashMap<>();
                    // AE2 already synchronizes faithful finite-long projections. Preserve
                    // the side channel for sources whose legacy API clamps even below long.
                    collected.forEach((key, amount) -> {
                        if (amount.infinite() || amount.value().compareTo(MAX_LONG) > 0
                                || inventory.get(key) != amount.value().longValueExact()) needed.put(key, amount);
                    });
                    if (!needed.equals(amounts)) amounts = Map.copyOf(needed);
                } catch (RuntimeException | Error failure) {
                    ExactAmountCollector.abort();
                    throw failure;
                }
                cached.exactTick = tick;
            } else {
                inventory = listing.get();
            }
            cached.snapshot = new TerminalInventorySnapshot(inventory, amounts);
            cached.tick = tick;
        }
        return cached.snapshot;
    }

    /** Unwrap only AE2's transparent supplier; filtered inventory handlers keep separate caches. */
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

    public static void clear() { CACHE.clear(); }

    private static final class Cached {
        private long tick;
        private Long exactTick;
        private TerminalInventorySnapshot snapshot;
    }
}
