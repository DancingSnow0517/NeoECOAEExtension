package cn.dancingsnow.neoecoae.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.util.Arrays;

/** Read-side layout prototypes only. Neither implementation replaces the production StorageAmounts ledger. */
final class StorageCounterLayouts {
    private static volatile Counter escaped;

    interface Counter {
        long get(AEKey key);
        void set(AEKey key, long amount);
        Counter copy();
        long sum();
        void clear();
        default void add(AEKey key, long delta) {
            long current = get(key);
            set(key, delta > 0 && current > Long.MAX_VALUE - delta ? Long.MAX_VALUE : Math.max(0, current + delta));
        }
    }

    private static final class Native implements Counter {
        private final KeyCounter values = new KeyCounter();
        public long get(AEKey key) { return values.get(key); }
        public void set(AEKey key, long amount) { if (amount == 0) values.remove(key); else values.set(key, amount); }
        public Counter copy() { var result = new Native(); result.values.addAll(values); return result; }
        public long sum() { long result = 0; for (var entry : values) result += entry.getLongValue(); return result; }
        public void clear() { values.clear(); }
    }

    /** This flat primitive layout is already used by ECO's private mount mirrors; no extra mirror is needed. */
    private static final class ECOFlatKeyCounterPrototype implements Counter {
        private Object2LongOpenHashMap<AEKey> values = new Object2LongOpenHashMap<>();
        public long get(AEKey key) { return values.getLong(key); }
        public void set(AEKey key, long amount) { if (amount == 0) values.removeLong(key); else values.put(key, amount); }
        public Counter copy() { var result = new ECOFlatKeyCounterPrototype(); result.values = values.clone(); return result; }
        public long sum() { long result = 0; for (var entry : Object2LongMaps.fastIterable(values)) result += entry.getLongValue(); return result; }
        public void clear() { values.clear(); }
    }

    /** Open addressing, content equality and backward-shift deletion; copies own every mutable amount. */
    private static final class EntryArray implements Counter {
        private static final class Entry {
            final AEKey key;
            long amount;
            Entry(AEKey key, long amount) { this.key = key; this.amount = amount; }
        }
        private Entry[] entries = new Entry[16];
        private int size;

        private int slot(AEKey key) {
            int mask = entries.length - 1, slot = HashCommon.mix(key.hashCode()) & mask;
            while (entries[slot] != null && !entries[slot].key.equals(key)) slot = (slot + 1) & mask;
            return slot;
        }
        public long get(AEKey key) { var entry = entries[slot(key)]; return entry == null ? 0 : entry.amount; }
        public void set(AEKey key, long amount) {
            if (amount < 0) throw new IllegalArgumentException("negative amount");
            int slot = slot(key);
            if (entries[slot] != null) {
                if (amount != 0) entries[slot].amount = amount;
                else { remove(slot); size--; }
            } else if (amount != 0) {
                entries[slot] = new Entry(key, amount);
                if (++size * 4 >= entries.length * 3) {
                    Entry[] previous = entries;
                    entries = new Entry[Math.multiplyExact(entries.length, 2)];
                    for (Entry entry : previous) if (entry != null) entries[slot(entry.key)] = entry;
                }
            }
        }
        private void remove(int hole) {
            int mask = entries.length - 1;
            for (int next = (hole + 1) & mask; entries[next] != null; next = (next + 1) & mask) {
                int home = HashCommon.mix(entries[next].key.hashCode()) & mask;
                if (((next - home) & mask) >= ((next - hole) & mask)) {
                    entries[hole] = entries[next];
                    hole = next;
                }
            }
            entries[hole] = null;
        }
        public Counter copy() {
            var result = new EntryArray();
            result.entries = new Entry[entries.length]; result.size = size;
            for (int i = 0; i < entries.length; i++) if (entries[i] != null)
                result.entries[i] = new Entry(entries[i].key, entries[i].amount);
            return result;
        }
        public long sum() { long result = 0; for (Entry entry : entries) if (entry != null) result += entry.amount; return result; }
        public void clear() { Arrays.fill(entries, null); size = 0; }
    }

    static void run(int[][] matrix) {
        String only = System.getProperty("neoecoae.benchmark.only", "");
        if (!only.startsWith("layout")) return;
        for (int[] workload : matrix) {
            int n = workload[0], k = workload[1];
            AEKey[] keys = StorageTestContext.keys(n, false);
            String[] names = {"native", "eco-flat", "entry-array"};
            Counter[] counters = {new Native(), new ECOFlatKeyCounterPrototype(), new EntryArray()};
            for (int implementation = 0; implementation < counters.length; implementation++) {
                Counter counter = counters[implementation];
                check(counter, keys);
                for (AEKey key : keys) counter.set(key, 1000);
                String prefix = "layout-" + names[implementation];
                StorageWorkloadBenchmark.measure(prefix + "-delta", n, k, 2 * k, cycle -> {
                    long sum = 0;
                    for (int i = 0; i < k; i++) {
                        AEKey key = keys[(cycle * 31 + i) % n];
                        counter.add(key, 1); counter.add(key, -1); sum += counter.get(key);
                    }
                    return sum;
                });
                if (n <= 300 || k == n) {
                    StorageWorkloadBenchmark.measure(prefix + "-traverse", n, n, 1, cycle -> counter.sum());
                    StorageWorkloadBenchmark.measure(prefix + "-clone", n, n, 1, cycle -> {
                        escaped = counter.copy();
                        return escaped.get(keys[cycle % n]);
                    });
                }
            }
        }
    }

    private static void check(Counter counter, AEKey[] keys) {
        for (int i = 0; i < keys.length; i++) counter.set(keys[i], i + 1);
        Counter before = counter.copy();
        for (int i = 0; i < keys.length; i += 2) counter.set(keys[i], 0);
        for (int i = 0; i < keys.length; i++) {
            if (counter.get(keys[i]) != (i % 2 == 0 ? 0 : i + 1) || before.get(keys[i]) != i + 1)
                throw new AssertionError("Deletion/independent copy mismatch");
            counter.set(keys[i], i + 2);
        }
        counter.set(keys[0], Long.MAX_VALUE);
        counter.add(keys[0], 1);
        if (counter.get(keys[0]) != Long.MAX_VALUE) throw new AssertionError("Overflow");
        counter.add(keys[0], Long.MIN_VALUE);
        if (counter.get(keys[0]) != 0) throw new AssertionError("Underflow");
        counter.clear();
        if (counter.sum() != 0) throw new AssertionError("Clear");
    }
}
