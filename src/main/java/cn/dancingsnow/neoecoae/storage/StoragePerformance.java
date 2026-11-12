package cn.dancingsnow.neoecoae.storage;

import com.google.gson.JsonObject;

/** Optional server-thread counters. Enable at JVM startup; no logging or allocation on the counted paths. */
public final class StoragePerformance {
    public static final boolean ENABLED = Boolean.getBoolean("neoecoae.storagePerformance");

    public enum Counter {
        CACHE_FULL, CACHE_DELTA, CACHE_UNCHANGED, CACHE_UNKNOWN_VERSION,
        CACHE_EXPIRED_DELTA, CACHE_NO_DELTA, CACHE_REMOUNT, CACHE_REPAIR, CACHE_SCOPED_FALLBACK,
        CACHE_LISTED_KEYS, CACHE_CHANGED_KEYS, TERMINAL_PUBLISH, TERMINAL_COPIED_KEYS
    }

    public enum Phase {
        CACHE_REFRESH, TERMINAL_CAPTURE
    }

    // Client rendering and background planners may also call key helpers. Keep their diagnostics
    // separate instead of racing on the server's counters. The ThreadLocal is absent when disabled.
    private static final ThreadLocal<Counters> COUNTERS = ENABLED ? ThreadLocal.withInitial(Counters::new) : null;

    private static final class Counters {
        final long[] total = new long[Counter.values().length];
        final long[][] times = new long[Phase.values().length][67];
    }

    private StoragePerformance() {}

    public static void count(Counter counter) { add(counter, 1); }

    public static void add(Counter counter, long amount) {
        if (ENABLED) {
            Counters counters = COUNTERS.get();
            counters.total[counter.ordinal()] += amount;
        }
    }

    public static long start() { return ENABLED ? System.nanoTime() : 0; }

    public static void end(Phase phase, long started) {
        if (ENABLED) {
            long elapsed = Math.max(0, System.nanoTime() - started);
            long[] stats = COUNTERS.get().times[phase.ordinal()];
            stats[0]++;
            stats[1] += elapsed;
            stats[2] = Math.max(stats[2], elapsed);
            stats[3 + (elapsed == 0 ? 0 : 64 - Long.numberOfLeadingZeros(elapsed))]++;
        }
    }

    /** Run on the owning server thread, outside a measured interval. */
    public static JsonObject snapshot() {
        JsonObject result = new JsonObject();
        result.addProperty("enabled", ENABLED);
        if (!ENABLED) return result;
        result.addProperty("thread", Thread.currentThread().getName());
        Counters counters = COUNTERS.get();
        JsonObject counts = new JsonObject(), timing = new JsonObject();
        for (Counter counter : Counter.values()) {
            int i = counter.ordinal();
            JsonObject row = new JsonObject();
            row.addProperty("total", counters.total[i]);
            counts.add(counter.name(), row);
        }
        for (Phase phase : Phase.values()) {
            long[] stats = COUNTERS.get().times[phase.ordinal()];
            JsonObject row = new JsonObject();
            row.addProperty("samples", stats[0]);
            row.addProperty("totalNs", stats[1]);
            row.addProperty("maxNs", stats[2]);
            row.addProperty("p50UpperNs", percentile(stats, 50));
            row.addProperty("p95UpperNs", percentile(stats, 95));
            row.addProperty("p99UpperNs", percentile(stats, 99));
            timing.add(phase.name(), row);
        }
        result.add("counters", counts);
        result.add("timings", timing);
        return result;
    }

    private static long percentile(long[] stats, int percent) {
        long target = (stats[0] * percent + 99) / 100, seen = 0;
        if (target == 0) return 0;
        for (int bucket = 0; bucket < 64; bucket++) {
            seen += stats[bucket + 3];
            if (seen >= target) return bucket >= 63 ? Long.MAX_VALUE : (1L << bucket) - 1;
        }
        return Long.MAX_VALUE;
    }

    public static void reset() {
        if (!ENABLED) return;
        COUNTERS.remove();
    }

}
