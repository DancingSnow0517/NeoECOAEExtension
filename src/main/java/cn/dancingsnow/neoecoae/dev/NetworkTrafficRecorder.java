package cn.dancingsnow.neoecoae.dev;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Observes one generator's real calls without retrying, simulating extra calls, or changing their return values. */
final class NetworkTrafficRecorder {
    private static final String[] PATHS = {"insertSimulate", "insertExecute", "extractSimulate", "extractExecute"};

    /** Keep common additions allocation-free while preserving exact totals beyond long. */
    private static final class Amount {
        long small;
        BigInteger overflow = BigInteger.ZERO;

        void add(long value) {
            if (value > Long.MAX_VALUE - small) {
                overflow = overflow.add(BigInteger.valueOf(small));
                small = 0;
            }
            small += value;
        }

        BigInteger value() { return overflow.add(BigInteger.valueOf(small)); }
    }

    private static final class Stats {
        long calls, exceptions, invalidReturns, nanos, maxNanos, allocatedBytes, allocationCalls;
        final Amount requested = new Amount();
        final Amount returned = new Amount();

        void add(long amount, long result, boolean success, long elapsed, long allocated) {
            calls++;
            requested.add(Math.max(0, amount));
            if (!success) exceptions++;
            else if (result < 0 || result > amount) invalidReturns++;
            else returned.add(result);
            nanos += elapsed;
            maxNanos = Math.max(maxNanos, elapsed);
            if (allocated >= 0) {
                allocatedBytes += allocated;
                allocationCalls++;
            }
        }

        Map<String, Object> report() {
            var result = new LinkedHashMap<String, Object>();
            result.put("calls", calls);
            result.put("requested", requested.value());
            result.put("returned", returned.value());
            result.put("exceptionsWithUnknownAcceptance", exceptions);
            result.put("invalidReturns", invalidReturns);
            result.put("elapsedNanos", nanos);
            result.put("averageNanosPerCall", calls == 0 ? null : (double) nanos / calls);
            result.put("maxCallNanos", calls == 0 ? null : maxNanos);
            result.put("allocationMeasuredCalls", allocationCalls);
            result.put("allocatedBytes", allocationCalls == calls && calls > 0 ? allocatedBytes : null);
            result.put("bytesPerCall", allocationCalls == calls && calls > 0 ? (double) allocatedBytes / calls : null);
            return result;
        }
    }

    private final LongSupplier clock, allocations;
    private final long started, duration;
    private final Map<Object, Stats[]> channels = new LinkedHashMap<>();
    private boolean closed;
    private long activeGeneratorTicks;

    NetworkTrafficRecorder(long started, long duration) {
        this(started, duration, System::nanoTime, ThreadAllocationMeter.create());
    }

    NetworkTrafficRecorder(long started, long duration, LongSupplier clock, LongSupplier allocations) {
        this.started = started;
        this.duration = duration;
        this.clock = clock;
        this.allocations = allocations;
    }

    boolean active() { return !closed && clock.getAsLong() - started < duration; }
    void close() { closed = true; }
    void tick() { if (active()) activeGeneratorTicks++; }

    private Stats stats(Object channel, boolean insert, boolean simulate) {
        Stats[] paths = channels.computeIfAbsent(channel, ignored -> new Stats[] {new Stats(), new Stats(), new Stats(), new Stats()});
        return paths[(insert ? 0 : 2) + (simulate ? 0 : 1)];
    }

    long insert(MEStorage inventory, AEKey key, long amount, Actionable mode, IActionSource source) {
        if (!active()) return inventory.insert(key, amount, mode, source);
        Stats stats = stats(key.getType(), true, mode == Actionable.SIMULATE);
        long before = allocations.getAsLong();
        long begin = clock.getAsLong();
        long result = 0;
        boolean success = false;
        try {
            result = inventory.insert(key, amount, mode, source);
            success = true;
            return result;
        } finally {
            long elapsed = clock.getAsLong() - begin;
            long after = allocations.getAsLong();
            stats.add(amount, result, success, elapsed, before < 0 || after < before ? -1 : after - before);
        }
    }

    FeWorkload.Access wrap(FeWorkload.Access delegate) {
        return new FeWorkload.Access() {
            @Override public long insert(long amount, boolean simulate) { return fe(delegate, amount, simulate, true); }
            @Override public long extract(long amount, boolean simulate) { return fe(delegate, amount, simulate, false); }
            @Override public Boolean keyReuseEnabled() { return delegate.keyReuseEnabled(); }
        };
    }

    private long fe(FeWorkload.Access delegate, long amount, boolean simulate, boolean insert) {
        if (!active()) return insert ? delegate.insert(amount, simulate) : delegate.extract(amount, simulate);
        Stats stats = stats("appflux:FE", insert, simulate);
        long before = allocations.getAsLong();
        long begin = clock.getAsLong();
        long result = 0;
        boolean success = false;
        try {
            result = insert ? delegate.insert(amount, simulate) : delegate.extract(amount, simulate);
            success = true;
            return result;
        } finally {
            long elapsed = clock.getAsLong() - begin;
            long after = allocations.getAsLong();
            stats.add(amount, result, success, elapsed, before < 0 || after < before ? -1 : after - before);
        }
    }

    /** Called on the server thread after detaching; only the resulting plain data leaves that thread. */
    Map<String, Object> snapshot() {
        var result = new LinkedHashMap<String, Object>();
        result.put("activeGeneratorTicks", activeGeneratorTicks);
        var rows = new ArrayList<Map<String, Object>>();
        channels.forEach((channel, paths) -> {
            var row = new LinkedHashMap<String, Object>();
            row.put("channel", channel instanceof AEKeyType type ? type.getId().toString() : channel.toString());
            for (int i = 0; i < paths.length; i++) row.put(PATHS[i], paths[i].report());
            rows.add(row);
        });
        result.put("channels", rows);
        result.put("scope", "Only calls initiated by the selected generator, grouped by native resource channel and simulation/execution. "
            + "Timing/allocation covers the real network call (FE also includes its key factory). Calls admitted before the deadline "
            + "are recorded in full. activeGeneratorTicks counts observed device ticks, not requested wall seconds; dividing it "
            + "by wall time is not server TPS. Simulation returns are possibilities, not resources delivered. Exceptions leave acceptance unknown; no retry is made.");
        return result;
    }
}
