package cn.dancingsnow.neoecoae.dev;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import static cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers.grouped;
import static cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers.compact;

/** Reproducible network traffic. Timings include the key factory and storage call, not the whole tick. */
final class FeWorkload {
    interface Access {
        long insert(long amount, boolean simulate);
        long extract(long amount, boolean simulate);
        default Boolean keyReuseEnabled() { return null; }
    }

    final FeWorkloadSettings settings;
    private final Random random;
    private final GeneratorSettings generation;
    private final GeneratorSettings consumption;
    private Boolean reuseEnabled;
    private long ticks, calls, simulations, executed, callNanos, tickNanos, maxTickNanos;
    private BigInteger requestedIn = BigInteger.ZERO, acceptedIn = BigInteger.ZERO;
    private BigInteger requestedOut = BigInteger.ZERO, acceptedOut = BigInteger.ZERO;

    FeWorkload(FeWorkloadSettings settings) {
        this.settings = settings;
        random = new Random(settings.seed());
        generation = new GeneratorSettings(1, settings.produceMin(), settings.produceMax(), 1);
        consumption = new GeneratorSettings(1, settings.consumeMin(), settings.consumeMax(), 1);
    }

    void tick(Access access) {
        Boolean current = access.keyReuseEnabled();
        if (ticks == 0) reuseEnabled = current;
        else if (!java.util.Objects.equals(current, reuseEnabled))
            throw new IllegalStateException("Key reuse setting changed during this run; use fe_reset before comparing");
        long begin = System.nanoTime();
        // Interleave endpoints rather than crediting every producer before any consumer.
        for (int index = 0; index < Math.max(settings.producers(), settings.consumers()); index++) {
            if (index < settings.producers()) transfer(access, generation.nextAmount(random), true);
            if (index < settings.consumers()) transfer(access, consumption.nextAmount(random), false);
        }
        ticks++;
        long elapsed = System.nanoTime() - begin;
        tickNanos = saturatedAdd(tickNanos, elapsed);
        maxTickNanos = Math.max(maxTickNanos, elapsed);
    }

    private void transfer(Access access, long requested, boolean insert) {
        if (insert) requestedIn = requestedIn.add(BigInteger.valueOf(requested));
        else requestedOut = requestedOut.add(BigInteger.valueOf(requested));
        long allowed = settings.simulateFirst() ? call(access, requested, insert, true) : requested;
        if (allowed == 0) return;
        long actual = call(access, allowed, insert, false);
        if (insert) acceptedIn = acceptedIn.add(BigInteger.valueOf(actual));
        else acceptedOut = acceptedOut.add(BigInteger.valueOf(actual));
        // Credit and debit only the real return value. Never refund or retry rejected traffic.
    }

    private long call(Access access, long amount, boolean insert, boolean simulate) {
        calls++;
        if (simulate) simulations++; else executed++;
        long begin = System.nanoTime();
        long result;
        try {
            result = insert ? access.insert(amount, simulate) : access.extract(amount, simulate);
        } finally {
            callNanos = saturatedAdd(callNanos, System.nanoTime() - begin);
        }
        if (result < 0 || result > amount) throw new IllegalStateException("Invalid FE acceptance; paused without retry: " + result + " / " + amount);
        return result;
    }

    private static long saturatedAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    Map<String, Object> report() {
        var report = new LinkedHashMap<String, Object>();
        report.put("path", "FluxKey.of(FE) + ME network storage; logical endpoints, not physical machine topology");
        report.put("producers", settings.producers());
        report.put("produceMin", settings.produceMin());
        report.put("produceMax", settings.produceMax());
        report.put("consumers", settings.consumers());
        report.put("consumeMin", settings.consumeMin());
        report.put("consumeMax", settings.consumeMax());
        report.put("seed", settings.seed());
        report.put("simulateFirst", settings.simulateFirst());
        report.put("keyReuseEnabled", reuseEnabled);
        report.put("activeTicks", ticks);
        report.put("storageCalls", calls);
        report.put("simulationCalls", simulations);
        report.put("executionCalls", executed);
        report.put("requestedInFE", requestedIn);
        report.put("acceptedInFE", acceptedIn);
        report.put("rejectedInFE", requestedIn.subtract(acceptedIn));
        report.put("requestedOutFE", requestedOut);
        report.put("extractedFE", acceptedOut);
        report.put("unmetOutFE", requestedOut.subtract(acceptedOut));
        report.put("netAddedFE", acceptedIn.subtract(acceptedOut));
        report.put("factoryAndStorageNanos", callNanos);
        report.put("averageFactoryAndStorageNanos", calls == 0 ? 0 : (double) callNanos / calls);
        report.put("driverNanosIncludingStatistics", tickNanos);
        report.put("maxDriverTickNanos", maxTickNanos);
        report.put("limitations", "Session counters reset on reload. Timings include instrumentation and network routing; not JMH key-only ns/op or allocation measurements. Other network users are not included in netAddedFE.");
        return report;
    }

    String describe() {
        return "FE producers=" + grouped(settings.producers()) + " (" + compact(settings.produceMin()) + ".." + compact(settings.produceMax())
            + "), consumers=" + grouped(settings.consumers()) + " (" + compact(settings.consumeMin()) + ".." + compact(settings.consumeMax())
            + "), seed=" + grouped(settings.seed()) + ", probe=" + settings.simulateFirst() + ", ticks=" + grouped(ticks)
            + ", calls=" + grouped(calls) + ", in=" + compact(acceptedIn) + ", out=" + compact(acceptedOut)
            + ", rejected=" + compact(requestedIn.subtract(acceptedIn)) + ", unmet=" + compact(requestedOut.subtract(acceptedOut));
    }
}
