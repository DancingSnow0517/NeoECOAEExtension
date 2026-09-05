package cn.dancingsnow.neoecoae.impl.storage.transfer;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Persistent round-robin traversal for sources that only expose AE2's synchronous enumeration API. */
public final class ECOGenericTransfer {
    private final ArrayDeque<AEKey> queue = new ArrayDeque<>();
    private final Set<AEKey> queued = new HashSet<>();
    private final Set<AEKey> halted = new HashSet<>();
    private final Map<AEKey, Long> retries = new HashMap<>();
    private MEStorage previousSource;
    private long lastScan = Long.MIN_VALUE;

    public Set<AEKey> haltedKeys() { return Set.copyOf(halted); }
    public void restoreHalted(Set<AEKey> keys) { halted.addAll(keys); }

    public long tick(MEStorage from, MEStorage to, IActionSource source, boolean skipInfinite,
        long tick, int keyBudget, long nanosBudget, long amountBudget, Consumer<String> failure) {
        long start = System.nanoTime();
        if (previousSource != from) {
            previousSource = from;
            queue.clear();
            queued.clear();
            retries.clear();
            lastScan = Long.MIN_VALUE;
        }
        if (lastScan == Long.MIN_VALUE || tick - lastScan >= 200L) {
            lastScan = tick;
            try {
                KeyCounter available = new KeyCounter();
                from.getAvailableStacks(available);
                for (var entry : available) if (entry.getLongValue() > 0 && !halted.contains(entry.getKey())) enqueue(entry.getKey());
            } catch (RuntimeException e) {
                failure.accept("source enumeration: " + e);
            }
        }
        long moved = 0L;
        int visits = Math.min(keyBudget, queue.size());
        while (visits-- > 0 && moved < amountBudget && System.nanoTime() - start < nanosBudget) {
            AEKey key = queue.removeFirst();
            queued.remove(key);
            if (halted.contains(key)) continue;
            if (tick < retries.getOrDefault(key, Long.MIN_VALUE)) { enqueue(key); continue; }
            try {
                long available = Math.min(amountBudget - moved,
                    from.extract(key, amountBudget - moved, Actionable.SIMULATE, source));
                if (available <= 0) { retries.remove(key); continue; }
                if (skipInfinite && ECOStorageSourceSafety.isEffectivelyInfiniteSource(from, key, available, source)) {
                    retries.put(key, tick + 200L);
                    enqueue(key);
                    continue;
                }
                long accepted = Math.min(available, to.insert(key, available, Actionable.SIMULATE, source));
                if (accepted <= 0) { retries.put(key, tick + 20L); enqueue(key); continue; }
                long extracted = from.extract(key, accepted, Actionable.MODULATE, source);
                if (extracted < 0L || extracted > accepted) throw new IllegalStateException("Invalid source acknowledgement");
                long inserted = to.insert(key, extracted, Actionable.MODULATE, source);
                if (inserted < 0 || inserted > extracted) throw new IllegalStateException("Invalid destination acknowledgement");
                if (inserted < extracted) {
                    long restored = from.insert(key, extracted - inserted, Actionable.MODULATE, source);
                    if (restored != extracted - inserted) throw new IllegalStateException("Rollback incomplete: " + restored + "/" + (extracted - inserted));
                }
                moved += inserted;
                retries.remove(key);
                enqueue(key);
            } catch (RuntimeException e) {
                halted.add(key);
                failure.accept("transfer outcome uncertain for " + key + ": " + e);
            }
        }
        return moved;
    }

    private void enqueue(AEKey key) { if (queued.add(key)) queue.addLast(key); }
}
