package cn.dancingsnow.neoecoae.storage;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;

/** Bounded per-network query results, valid only for one completed cached-inventory refresh. */
public final class StorageThresholdCache {
    private static final int CAPACITY = 256;
    private final Object2LongLinkedOpenHashMap<Query> results = new Object2LongLinkedOpenHashMap<>();
    private volatile boolean invalid = true;
    private KeyCounter inventory;

    public interface Source {
        StorageThresholdCache neoecoae$thresholdCache();
    }

    /** A query is retained by its emitter so repeated notifications do not construct keys. */
    public record Query(AEKey filter, FuzzyMode fuzzy, long threshold) {}

    public void invalidate() { invalid = true; }

    public long amount(KeyCounter current, Query query) {
        if (invalid || inventory != current) {
            results.clear();
            inventory = current;
            invalid = false;
        }
        if (results.containsKey(query)) return results.getAndMoveToLast(query);
        long total = evaluate(current, query);
        if (results.size() == CAPACITY) results.removeFirstLong();
        results.put(query, total);
        return total;
    }

    static long evaluate(KeyCounter inventory, Query query) {
        if (query.filter() != null && query.fuzzy() == null) return inventory.get(query.filter());
        Iterable<it.unimi.dsi.fastutil.objects.Object2LongMap.Entry<AEKey>> matches = query.filter() == null
                ? inventory : inventory.findFuzzy(query.filter(), query.fuzzy());
        long total = 0;
        for (var entry : matches) {
            long amount = Math.max(0L, entry.getLongValue());
            total = amount > Long.MAX_VALUE - total ? Long.MAX_VALUE : total + amount;
            if (total > query.threshold()) break;
        }
        return total;
    }
}
