package cn.dancingsnow.neoecoae.dev;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** Paired, warm-cache factory measurements. No inventory, world state or reuse setting is changed. */
final class KeyFactoryBenchmark {
    static final int DEFAULT_CALLS = 2048;
    static final int MAX_CALLS = 8192;
    static final int DEFAULT_TICKS = 100;
    static final int WARMUP_TICKS = 40;
    static final int ROUNDS = 3;
    // Publish the whole preallocated buffer, so every factory result escapes the measured loop.
    private static volatile AEKey[] consumed;

    record Fixture(String resource, Supplier<AEKey> nativeFactory, Supplier<AEKey> ecoFactory) {}

    interface Meter {
        long nanos();
        /** -1 means unsupported or disabled; it must never be reported as zero allocation. */
        long allocatedBytes();
    }

    private static final class Totals {
        long calls, nanos, bytes, allocationCalls;

        void add(int count, long elapsed, long allocated) {
            calls += count;
            nanos += elapsed;
            if (allocated >= 0) {
                bytes += allocated;
                allocationCalls += count;
            }
        }

        Map<String, Object> report() {
            var result = new LinkedHashMap<String, Object>();
            result.put("calls", calls);
            result.put("elapsedNanos", nanos);
            result.put("nanosPerCall", calls == 0 ? null : (double) nanos / calls);
            result.put("allocationMeasuredCalls", allocationCalls);
            result.put("allocatedBytes", allocationCalls == 0 ? null : bytes);
            result.put("bytesPerCall", allocationCalls == 0 ? null : (double) bytes / allocationCalls);
            return result;
        }
    }

    private static final class Case {
        final Fixture fixture;
        final Totals[] nativeRounds = new Totals[ROUNDS];
        final Totals[] ecoRounds = new Totals[ROUNDS];
        int pairs;

        Case(Fixture fixture) {
            this.fixture = fixture;
            for (int i = 0; i < ROUNDS; i++) {
                nativeRounds[i] = new Totals();
                ecoRounds[i] = new Totals();
            }
        }
    }

    private final List<Case> cases;
    private final int callsPerBatch, ticksPerRound;
    private final Meter meter;
    private final Supplier<Object> generation;
    private final Object epoch;
    private final long thread = Thread.currentThread().threadId();
    private final AEKey[] output;
    private int activeTicks;
    private String state = "running";
    private String failure;

    static KeyFactoryBenchmark create(String channel, int calls, int ticks) {
        var fixtures = new ArrayList<Fixture>();
        if (channel.equals("item") || channel.equals("both")) {
            var input = new ItemStack(Items.IRON_INGOT, 64);
            fixtures.add(new Fixture("minecraft:iron_ingot", () -> AEItemKey.of(input), () -> ECOKeyFactory.item(input)));
        }
        if (channel.equals("fluid") || channel.equals("both")) {
            var input = new FluidStack(Fluids.WATER, 1000);
            fixtures.add(new Fixture("minecraft:water", () -> AEFluidKey.of(input), () -> ECOKeyFactory.fluid(input)));
        }
        return new KeyFactoryBenchmark(fixtures, calls, ticks, threadMeter(), KeyReuse::epoch);
    }

    KeyFactoryBenchmark(List<Fixture> fixtures, int calls, int ticks, Meter meter, Supplier<Object> generation) {
        if (fixtures.isEmpty() || fixtures.size() > 2 || calls < 1 || calls > MAX_CALLS || ticks < 1 || ticks > 1200)
            throw new IllegalArgumentException("Require item/fluid/both, 1..8192 calls and 1..1200 ticks per round");
        this.epoch = generation.get();
        if (epoch == null) throw new IllegalArgumentException("Enable [keyReuse] enabled in neoecoae-key-reuse.toml first");
        this.generation = generation;
        this.callsPerBatch = calls;
        this.ticksPerRound = ticks;
        this.meter = meter;
        this.output = new AEKey[calls];
        this.cases = fixtures.stream().map(Case::new).toList();
        for (Fixture fixture : fixtures) {
            AEKey expected = fixture.nativeFactory.get();
            AEKey actual = fixture.ecoFactory.get();
            if (expected == null || !expected.equals(actual) || expected.hashCode() != actual.hashCode())
                throw new IllegalArgumentException("Factory results differ for " + fixture.resource);
            if (actual != fixture.ecoFactory.get())
                throw new IllegalArgumentException("ECO factory did not reuse the plain key for " + fixture.resource);
        }
    }

    void tick() {
        if (finished()) return;
        try {
            if (Thread.currentThread().threadId() != thread) throw new IllegalStateException("Benchmark changed threads");
            checkGeneration();
            Case sample = cases.get(activeTicks % cases.size());
            int round = (sample.pairs - WARMUP_TICKS) / ticksPerRound;
            boolean warmup = sample.pairs < WARMUP_TICKS;
            // Alternate AB/BA within each case, including warmup, to reduce order bias.
            boolean nativeFirst = (sample.pairs & 1) == 0;
            Totals nativeTotals = warmup ? null : sample.nativeRounds[round];
            Totals ecoTotals = warmup ? null : sample.ecoRounds[round];
            measure(nativeFirst ? sample.fixture.nativeFactory : sample.fixture.ecoFactory,
                nativeFirst ? nativeTotals : ecoTotals);
            measure(nativeFirst ? sample.fixture.ecoFactory : sample.fixture.nativeFactory,
                nativeFirst ? ecoTotals : nativeTotals);
            checkGeneration();
            sample.pairs++;
            activeTicks++;
            if (activeTicks == totalTicks()) stop("completed", null);
        } catch (RuntimeException | LinkageError problem) {
            stop("failed", problem.toString());
        }
    }

    private void checkGeneration() {
        if (generation.get() != epoch)
            throw new IllegalStateException("Key reuse configuration or generation changed; start a new benchmark");
    }

    private void measure(Supplier<AEKey> factory, Totals totals) {
        long before = meter.allocatedBytes();
        long start = meter.nanos();
        for (int i = 0; i < output.length; i++) output[i] = factory.get();
        long elapsed = meter.nanos() - start;
        long after = meter.allocatedBytes();
        consumed = output;
        checkGeneration();
        if (totals != null) totals.add(callsPerBatch, elapsed, before < 0 || after < before ? -1 : after - before);
    }

    boolean finished() { return !state.equals("running"); }
    int totalTicks() { return cases.size() * (WARMUP_TICKS + ROUNDS * ticksPerRound); }

    void stop(String state, String reason) {
        if (finished()) return;
        this.state = state;
        this.failure = reason;
        Arrays.fill(output, null);
        if (consumed == output) consumed = null;
    }

    String describe() {
        return "Key benchmark " + state + ": " + activeTicks + "/" + totalTicks() + " ticks"
            + (failure == null ? "" : "; " + failure);
    }

    Map<String, Object> report() {
        var report = new LinkedHashMap<String, Object>();
        report.put("state", state);
        report.put("failure", failure);
        report.put("keyReuseEnabledAtStart", true);
        report.put("activeTicks", activeTicks);
        report.put("plannedTicks", totalTicks());
        report.put("callsPerPathPerTick", callsPerBatch);
        report.put("warmupTicksPerResource", WARMUP_TICKS);
        report.put("measurementTicksPerRoundPerResource", ticksPerRound);
        report.put("rounds", ROUNDS);
        report.put("javaVersion", System.getProperty("java.version"));
        report.put("vmName", System.getProperty("java.vm.name"));
        var results = new ArrayList<Map<String, Object>>();
        for (Case sample : cases) {
            var result = new LinkedHashMap<String, Object>();
            result.put("resource", sample.fixture.resource);
            var rounds = new ArrayList<Map<String, Object>>();
            for (int i = 0; i < ROUNDS; i++) {
                var round = new LinkedHashMap<String, Object>();
                round.put("round", i + 1);
                round.put("complete", sample.nativeRounds[i].calls == (long) callsPerBatch * ticksPerRound
                    && sample.ecoRounds[i].calls == (long) callsPerBatch * ticksPerRound);
                round.put("nativeFactory", sample.nativeRounds[i].report());
                round.put("ecoFactory", sample.ecoRounds[i].report());
                rounds.add(round);
            }
            result.put("measurements", rounds);
            results.add(result);
        }
        report.put("results", results);
        report.put("scope", "Repeated plain iron ingot/water factory calls; native AE2 versus ECO with reuse enabled. "
            + "One resource per tick, two equal batches in alternating order. Warmup excluded; input stacks and output buffer preallocated.");
        report.put("limitations", "Not JMH or whole-network/MSPT speedup. Timings include factory dispatch and escaping output-array stores, "
            + "and may include GC/JIT/scheduling. Allocated bytes are approximate current-thread heap allocations, not object counts, "
            + "retained memory or GC pause time. Missing allocation metrics are null. Only completed runs/rounds are comparable. "
            + "No components, cold-cache, mixed-resource or storage-routing workload is measured.");
        return report;
    }

    private static Meter threadMeter() {
        var allocations = ThreadAllocationMeter.create();
        return new Meter() {
            @Override public long nanos() { return System.nanoTime(); }
            @Override public long allocatedBytes() { return allocations.getAsLong(); }
        };
    }
}
