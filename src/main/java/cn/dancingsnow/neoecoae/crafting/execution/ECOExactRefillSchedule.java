package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.hooks.ticking.TickHandler;
import cn.dancingsnow.neoecoae.storage.ECOCreativeSupply;
import cn.dancingsnow.neoecoae.storage.StoragePerformance;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

final class ECOExactRefillSchedule {
    static final int ATTEMPTS_PER_TICK = 32;
    private static final int NO_PROGRESS_RETRY_TICKS = 20;

    private final MEStorage storage;
    private final @Nullable ECOCreativeSupply changes;
    private final NavigableMap<Long, LinkedHashSet<AEKey>> due = new TreeMap<>();
    private final Map<AEKey, Long> deadlines = new HashMap<>();
    private final Set<AEKey> watched = new HashSet<>();
    private final Consumer<AEKey> listener = key -> wake(key, TickHandler.instance().getCurrentTick());

    ECOExactRefillSchedule(MEStorage storage, Set<AEKey> missingKeys, long now) {
        this.storage = storage;
        this.changes = ECOCreativeSupply.get(storage);
        StoragePerformance.count(StoragePerformance.Counter.EXACT_REFILL_BIND);
        for (AEKey key : missingKeys) {
            StoragePerformance.count(StoragePerformance.Counter.EXACT_REFILL_BIND_KEY_VISITS);
            schedule(key, now);
        }
    }

    boolean isBoundTo(MEStorage candidate) { return storage == candidate; }

    boolean hasDue(long now) { return !due.isEmpty() && due.firstKey() <= now; }

    @Nullable AEKey poll(long now) {
        if (!hasDue(now)) return null;
        var first = due.firstEntry();
        AEKey key = first.getValue().iterator().next();
        first.getValue().remove(key);
        if (first.getValue().isEmpty()) due.pollFirstEntry();
        deadlines.remove(key);
        if (watched.remove(key)) changes.unwatchRefill(key, listener);
        return key;
    }

    void retry(AEKey key, long now, boolean madeProgress) {
        schedule(key, now + (madeProgress ? 1 : NO_PROGRESS_RETRY_TICKS));
        if (!madeProgress && changes != null && watched.add(key)) changes.watchRefill(key, listener);
    }

    private void wake(AEKey key, long now) {
        Long previous = deadlines.get(key);
        if (previous == null || previous <= now) return;
        var bucket = due.get(previous);
        bucket.remove(key);
        if (bucket.isEmpty()) due.remove(previous);
        schedule(key, now);
    }

    private void schedule(AEKey key, long tick) {
        deadlines.put(key, tick);
        due.computeIfAbsent(tick, ignored -> new LinkedHashSet<>()).add(key);
    }

    void release() {
        if (changes != null) for (AEKey key : watched) changes.unwatchRefill(key, listener);
    }
}
