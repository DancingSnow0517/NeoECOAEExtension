package cn.dancingsnow.neoecoae.dev;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuse;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class KeyFactoryBenchmarkTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }
    @AfterEach void stopReuse() { KeyReuse.configure(false); }

    @Test void warmupIsExcludedAndEveryRoundHasEqualCallsInAlternatingOrder() {
        var meter = new FakeMeter();
        var fixture = new CountingFixture(meter);
        var bench = new KeyFactoryBenchmark(List.of(fixture.fixture), 2, 2, meter, () -> this);
        fixture.calls.clear();
        for (int i = 0; i < KeyFactoryBenchmark.WARMUP_TICKS; i++) bench.tick();
        assertEquals(List.of("native", "native", "eco", "eco", "eco", "eco", "native", "native"), fixture.calls.subList(0, 8));
        assertEquals(0L, path(bench, 0, "nativeFactory").get("calls"));
        while (!bench.finished()) bench.tick();
        assertEquals("completed", bench.report().get("state"));
        assertEquals(46, bench.report().get("activeTicks"));
        for (int round = 0; round < 3; round++) {
            var nativePath = path(bench, round, "nativeFactory");
            var ecoPath = path(bench, round, "ecoFactory");
            assertEquals(4L, nativePath.get("calls"));
            assertEquals(4L, ecoPath.get("calls"));
            assertEquals(10.0, nativePath.get("nanosPerCall"));
            assertEquals(2.0, ecoPath.get("nanosPerCall"));
            assertEquals(8.0, nativePath.get("bytesPerCall"));
            assertEquals(0.0, ecoPath.get("bytesPerCall"));
        }
        int calls = fixture.calls.size();
        bench.tick();
        assertEquals(calls, fixture.calls.size());
    }

    @Test void twoResourcesShareTheTickBudgetInsteadOfDoublingIt() {
        var meter = new FakeMeter();
        var item = new CountingFixture(meter);
        var fluid = new CountingFixture(meter);
        var bench = new KeyFactoryBenchmark(List.of(item.fixture, fluid.fixture), 2, 1, meter, () -> this);
        item.calls.clear();
        fluid.calls.clear();
        bench.tick();
        assertEquals(4, item.calls.size());
        assertTrue(fluid.calls.isEmpty());
        bench.tick();
        assertEquals(4, fluid.calls.size());
        while (!bench.finished()) bench.tick();
        assertEquals(86, bench.report().get("activeTicks"));
    }

    @Test void missingAllocationTrackingIsNullRatherThanZero() {
        var meter = new FakeMeter();
        meter.supported = false;
        var fixture = new CountingFixture(meter);
        var bench = new KeyFactoryBenchmark(List.of(fixture.fixture), 1, 1, meter, () -> this);
        while (!bench.finished()) bench.tick();
        assertEquals(0L, path(bench, 0, "nativeFactory").get("allocationMeasuredCalls"));
        assertNull(path(bench, 0, "nativeFactory").get("allocatedBytes"));
        assertNull(path(bench, 0, "nativeFactory").get("bytesPerCall"));
        assertEquals(10.0, path(bench, 0, "nativeFactory").get("nanosPerCall"));
    }

    @Test void reloadStopsBeforeCollectingAnotherSampleAndCancelDoesNotResume() {
        var meter = new FakeMeter();
        var fixture = new CountingFixture(meter);
        var epoch = new AtomicReference<Object>(new Object());
        var bench = new KeyFactoryBenchmark(List.of(fixture.fixture), 1, 1, meter, epoch::get);
        bench.tick();
        int calls = fixture.calls.size();
        epoch.set(new Object());
        bench.tick();
        assertEquals(calls, fixture.calls.size());
        assertEquals("failed", bench.report().get("state"));
        assertTrue(bench.report().get("failure").toString().contains("generation changed"));
        bench.stop("cancelled", "late cancellation");
        assertEquals("failed", bench.report().get("state"));
    }

    @Test void switchingGenerationInsideAFactoryDoesNotCreditThatBatch() {
        var meter = new FakeMeter();
        var fixture = new CountingFixture(meter);
        var epoch = new AtomicReference<Object>(new Object());
        boolean[] reload = {false};
        var changing = new KeyFactoryBenchmark.Fixture("fixture", () -> {
            AEKey result = fixture.fixture.nativeFactory().get();
            if (reload[0]) epoch.set(new Object());
            return result;
        }, fixture.fixture.ecoFactory());
        var bench = new KeyFactoryBenchmark(List.of(changing), 1, 1, meter, epoch::get);
        for (int i = 0; i < 40; i++) bench.tick();
        reload[0] = true;
        bench.tick();
        assertEquals("failed", bench.report().get("state"));
        assertEquals(0L, path(bench, 0, "nativeFactory").get("calls"));
        assertEquals(0L, path(bench, 0, "ecoFactory").get("calls"));
    }

    @Test void refusesDisabledReuseAndUnboundedWork() {
        var meter = new FakeMeter();
        var fixture = new CountingFixture(meter);
        assertThrows(IllegalArgumentException.class, () -> new KeyFactoryBenchmark(List.of(fixture.fixture), 1, 1, meter, () -> null));
        assertThrows(IllegalArgumentException.class, () -> new KeyFactoryBenchmark(List.of(fixture.fixture), 8193, 1, meter, () -> this));
        assertThrows(IllegalArgumentException.class, () -> new KeyFactoryBenchmark(List.of(fixture.fixture), 1, 1201, meter, () -> this));
    }

    @Test void cancellationRetainsOnlyPartialCounts() {
        var meter = new FakeMeter();
        var fixture = new CountingFixture(meter);
        var bench = new KeyFactoryBenchmark(List.of(fixture.fixture), 2, 2, meter, () -> this);
        for (int i = 0; i < 41; i++) bench.tick();
        bench.stop("cancelled", "test");
        bench.tick();
        assertEquals("cancelled", bench.report().get("state"));
        assertEquals(41, bench.report().get("activeTicks"));
        assertEquals(2L, path(bench, 0, "nativeFactory").get("calls"));
        assertEquals(false, rounds(bench).getFirst().get("complete"));
    }

    @Test void realItemAndFluidFixturesFinishWithoutMutatingTheReuseGeneration() {
        KeyReuse.configure(true);
        Object epoch = KeyReuse.epoch();
        var bench = KeyFactoryBenchmark.create("both", 8, 1);
        while (!bench.finished()) bench.tick();
        assertEquals("completed", bench.report().get("state"), bench.describe());
        assertSame(epoch, KeyReuse.epoch());
        assertEquals(2, ((List<?>) bench.report().get("results")).size());
        for (var round : rounds(bench)) assertEquals(true, round.get("complete"));
        // Performance is measured in the game; this contract test deliberately asserts no speed threshold.
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rounds(KeyFactoryBenchmark bench) {
        var results = (List<Map<String, Object>>) bench.report().get("results");
        return (List<Map<String, Object>>) results.getFirst().get("measurements");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> path(KeyFactoryBenchmark bench, int round, String path) {
        return (Map<String, Object>) rounds(bench).get(round).get(path);
    }

    private static final class FakeMeter implements KeyFactoryBenchmark.Meter {
        long nanos, bytes;
        boolean supported = true;
        @Override public long nanos() { return nanos; }
        @Override public long allocatedBytes() { return supported ? bytes : -1; }
    }

    private static final class CountingFixture {
        final List<String> calls = new ArrayList<>();
        final KeyFactoryBenchmark.Fixture fixture;

        CountingFixture(FakeMeter meter) {
            AEKey key = mock(AEKey.class);
            fixture = new KeyFactoryBenchmark.Fixture("fixture", () -> {
                calls.add("native");
                meter.nanos += 10;
                meter.bytes += 8;
                return key;
            }, () -> {
                calls.add("eco");
                meter.nanos += 2;
                return key;
            });
        }
    }
}
