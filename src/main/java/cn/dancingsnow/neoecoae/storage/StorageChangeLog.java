package cn.dancingsnow.neoecoae.storage;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import java.util.function.Consumer;

/**
 * Last-change stamps for component-backed cells. No quantities or per-operation nodes are duplicated here.
 * InfiniteStorageAmounts keeps its existing entry-linked log; ordinary cells keep their primitive quantity map.
 */
final class StorageChangeLog {
    static final int CAPACITY = 4096;
    private final Object2LongLinkedOpenHashMap<AEKey> stamps = new Object2LongLinkedOpenHashMap<>();
    private long floor;
    private long latest;

    void record(AEKey key, long revision) {
        latest = revision;
        stamps.putAndMoveToLast(key, revision);
        if (stamps.size() > CAPACITY) floor = Math.max(floor, stamps.removeFirstLong());
    }

    void barrier(long revision) {
        stamps.clear();
        floor = latest = revision;
    }

    boolean visit(long since, long current, Consumer<AEKey> visitor) {
        if (since < 0 || since < floor || since > current || current != latest) return false;
        if (stamps.isEmpty()) return true;
        // FastUtil's cursor starts after the supplied last key, so reverse traversal touches only recent keys.
        var cursor = stamps.object2LongEntrySet().fastIterator(
            new AbstractObject2LongMap.BasicEntry<>(stamps.lastKey(), 0L));
        while (cursor.hasPrevious()) {
            var entry = cursor.previous();
            if (entry.getLongValue() <= since) break;
            visitor.accept(entry.getKey());
        }
        return true;
    }
}
