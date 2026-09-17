package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.impl.storage.ECOSavedDataPersistence;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/** Repeatable workload acceptance. A frame is timed work, not an actual Minecraft server tick. */
@EnabledIfEnvironmentVariable(named = "NEOECO_STORAGE_ACCEPTANCE", matches = "1")
class InfiniteStorageAcceptanceTest {
    @TempDir
    Path directory;

    @Test
    void sustainedWorkloadMatrix() throws Exception {
        SharedConstants.tryDetectVersion();
        List<String> report = new ArrayList<>();
        List<String> samples = new ArrayList<>();
        samples.add("scenario,frame,work_ms,save_ms,total_ms");
        report.add(
                "Synthetic acceptance: validated test-key engine; no Forge registry, network, client or actual tick.");
        report.add("Java=" + System.getProperty("java.version") + ", VM=" + System.getProperty("java.vm.name")
                + ", OS=" + System.getProperty("os.name") + ", CPUs="
                + Runtime.getRuntime().availableProcessors()
                + ", maxHeap=" + Runtime.getRuntime().maxMemory());
        report.add("Per scenario: 100000 keys; 240 frames; 20000 calls/frame; synchronous save every 20 frames.");
        report.add(
                "Provisional target: frame p99 <=25ms AND max <=50ms; setup, reload and transfer listed separately.");
        try {
            for (String scenario : List.of("hot64", "rotating", "empty-refill", "wide")) {
                runScenario(scenario, report, samples);
            }
        } finally {
            Path output = Path.of("build/reports/storage-acceptance");
            Files.createDirectories(output);
            Files.write(output.resolve("summary.txt"), report);
            Files.write(output.resolve("frames.csv"), samples);
            ECOSavedDataPersistence.clear();
        }
    }

    private void runScenario(String scenario, List<String> report, List<String> samples) throws Exception {
        int count = 100_000;
        var engine =
                SavedDataInfiniteStorageEngine.createNew(UUID.randomUUID(), null, directory.resolve(scenario + ".dat"));
        var keys = new InfiniteStorageTestKey[count];
        boolean wide = scenario.equals("wide");
        long initial = scenario.equals("empty-refill") ? 64 : wide ? Long.MAX_VALUE : 1000;
        for (int i = 0; i < count; i++) {
            keys[i] = new InfiniteStorageTestKey(i);
            keys[i].cacheEncoding(engine);
            assertEquals(initial, engine.insert(keys[i], initial, Actionable.MODULATE));
            if (wide) engine.insert(keys[i], 1000, Actionable.MODULATE);
        }
        long start = System.nanoTime();
        engine.flushAndAwait();
        report.add(String.format(Locale.ROOT, "%s initial full commit: %.3f ms", scenario, elapsed(start)));
        for (int i = 0; i < 100_000; i++) {
            var key = keys[i % count];
            engine.extract(key, 64, Actionable.MODULATE);
            engine.insert(key, 64, Actionable.MODULATE);
        }
        engine.flushAndAwait(); // Remove warm-up changes from measured saves.
        long[] frames = new long[240];
        long[] work = new long[240];
        long[] saves = new long[12];
        long gcBefore = gcMillis();
        for (int frame = 0; frame < frames.length; frame++) {
            start = System.nanoTime();
            for (int j = 0; j < 10_000; j++) {
                int index = scenario.equals("hot64") ? j % 64 : (frame * 10_000 + j) % count;
                var key = keys[index];
                long removed = engine.extract(key, 64, Actionable.MODULATE);
                long inserted = engine.insert(key, 64, Actionable.MODULATE);
                if (removed != 64 || inserted != 64) fail("I/O amount changed");
            }
            work[frame] = System.nanoTime() - start;
            long saveTime = 0;
            if (frame % 20 == 19) {
                start = System.nanoTime();
                engine.save(directory.resolve(scenario + ".dat").toFile());
                saveTime = System.nanoTime() - start;
                saves[frame / 20] = saveTime;
                assertEquals(ECOInfiniteDomainState.READY, engine.getState());
                assertFalse(engine.needsPersistence());
            }
            frames[frame] = work[frame] + saveTime;
            samples.add(String.format(
                    Locale.ROOT,
                    "%s,%d,%.6f,%.6f,%.6f",
                    scenario,
                    frame,
                    work[frame] / 1e6,
                    saveTime / 1e6,
                    frames[frame] / 1e6));
        }
        report.add(summary(scenario + " I/O only", work));
        report.add(summary(scenario + " synchronous saves", saves));
        report.add(summary(scenario + " INCLUDING saves", frames));
        report.add(scenario + " measured GC time: " + (gcMillis() - gcBefore) + " ms");
        long[] sorted = frames.clone();
        Arrays.sort(sorted);
        report.add(scenario + " timing verdict: "
                + (sorted[sorted.length - 1] <= 50_000_000
                                && sorted[(int) Math.ceil(sorted.length * .99) - 1] <= 25_000_000
                        ? "PASS"
                        : "FAIL"));
        HugeAmount expected = wide ? HugeAmount.of(Long.MAX_VALUE).add(HugeAmount.of(1000)) : HugeAmount.of(initial);
        for (var key : keys) assertEquals(expected, engine.getAmount(key));
        start = System.nanoTime();
        CompoundTag recovered = InfiniteStorageSnapshot.read(directory.resolve(scenario + ".dat"));
        report.add(String.format(
                Locale.ROOT, "%s NBT read + delta merge (no AE decode): %.3f ms", scenario, elapsed(start)));
        var records = recovered.getList("entries", 10);
        assertEquals(count, records.size());
        for (int i = 0; i < records.size(); i++) {
            CompoundTag record = records.getCompound(i);
            HugeAmount actual = record.contains("amount_wide")
                    ? HugeAmount.of(new java.math.BigInteger(record.getByteArray("amount_wide")))
                    : HugeAmount.of(record.getLong("amount_long"));
            assertEquals(expected, actual);
        }
        if (scenario.equals("rotating")) {
            List<ECOInfiniteStorageEngine.HugeStack> transfer = new ArrayList<>();
            for (int i = 0; i < 10_000; i++)
                transfer.add(new ECOInfiniteStorageEngine.HugeStack(keys[i], HugeAmount.of(7)));
            UUID receipt = UUID.randomUUID();
            start = System.nanoTime();
            assertTrue(engine.applyTransferOnce(receipt, transfer));
            report.add(String.format(
                    Locale.ROOT, "10000-key atomic transfer incl. digest and commit: %.3f ms", elapsed(start)));
            assertTrue(engine.applyTransferOnce(receipt, transfer));
            for (int i = 0; i < 10_000; i++) assertEquals(expected.add(HugeAmount.of(7)), engine.getAmount(keys[i]));
        }
        engine.closeAndFlush();
        ECOSavedDataPersistence.unregister(engine);
        report.add(scenario + " correctness: PASS");
    }

    private static double elapsed(long start) {
        return (System.nanoTime() - start) / 1e6;
    }

    private static long gcMillis() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(b -> Math.max(0, b.getCollectionTime()))
                .sum();
    }

    private static String summary(String name, long[] nanos) {
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        return String.format(
                Locale.ROOT,
                "%s: n=%d p50=%.3f p95=%.3f p99=%.3f max=%.3f ms over50ms=%d",
                name,
                sorted.length,
                sorted[(sorted.length - 1) / 2] / 1e6,
                sorted[(int) Math.ceil(sorted.length * .95) - 1] / 1e6,
                sorted[(int) Math.ceil(sorted.length * .99) - 1] / 1e6,
                sorted[sorted.length - 1] / 1e6,
                Arrays.stream(sorted).filter(n -> n > 50_000_000).count());
    }
}
