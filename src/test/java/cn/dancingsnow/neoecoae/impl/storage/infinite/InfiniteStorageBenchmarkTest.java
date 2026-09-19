package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.impl.storage.ECOSavedDataPersistence;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/** Opt-in synthetic measurements; these are not AE network or server TPS measurements. */
@EnabledIfEnvironmentVariable(named = "NEOECO_STORAGE_BENCHMARK", matches = "1")
class InfiniteStorageBenchmarkTest {
    @TempDir
    Path directory;

    @Test
    void measureCoreIoAndSnapshots() throws Exception {
        List<String> report = new ArrayList<>();
        report.add("Synthetic benchmark: primitive quantity core and independent-record snapshot; not server TPS.");
        report.add("Runtime: " + System.getProperty("java.version"));
        for (int count : new int[] {1_000, 10_000, 100_000}) {
            HybridAmountStore<Integer> store = new HybridAmountStore<>();
            Integer[] keys = new Integer[count];
            for (int i = 0; i < count; i++) {
                keys[i] = i;
                store.add(keys[i], 1_000);
            }
            for (int i = 0; i < 100_000; i++) {
                Integer key = keys[i % count];
                store.add(key, 64);
                store.subtractAtMost(key, 64);
            }
            long start = System.nanoTime();
            for (int i = 0; i < 1_000_000; i++) {
                Integer key = keys[i % count];
                store.add(key, 64);
                store.subtractAtMost(key, 64);
            }
            double seconds = (System.nanoTime() - start) / 1e9;
            assertEquals(1_000, store.getSaturated(keys[count - 1]));
            report.add(String.format(
                    Locale.ROOT, "%d keys: quantity core %.0f operations/sec", count, 2_000_000 / seconds));
        }
        for (int count : new int[] {1_000, 10_000, 50_000}) {
            CompoundTag data = new CompoundTag();
            ListTag entries = new ListTag();
            for (int i = 0; i < count; i++) {
                CompoundTag key = new CompoundTag();
                key.putString("id", "benchmark:item_" + i);
                CompoundTag entry = new CompoundTag();
                entry.put("key", key);
                entry.putLong("amount_long", 1_000_000);
                entries.add(entry);
            }
            data.put("entries", entries);
            Path path = directory.resolve("snapshot_" + count + ".dat");
            long start = System.nanoTime();
            InfiniteStorageSnapshot.write(path, data, 3465);
            double writeMs = (System.nanoTime() - start) / 1e6;
            start = System.nanoTime();
            CompoundTag recovered = InfiniteStorageSnapshot.read(path);
            double readMs = (System.nanoTime() - start) / 1e6;
            assertEquals(data, recovered);
            report.add(String.format(
                    Locale.ROOT,
                    "%d records: snapshot verified write %.1f ms, read %.1f ms, %d bytes",
                    count,
                    writeMs,
                    readMs,
                    Files.size(path)));
        }
        SharedConstants.tryDetectVersion();
        for (int count : new int[] {10_000, 100_000}) {
            Path path = directory.resolve("engine_" + count + ".dat");
            var engine = SavedDataInfiniteStorageEngine.createNew(UUID.randomUUID(), null, path);
            InfiniteStorageTestKey[] keys = new InfiniteStorageTestKey[count];
            for (int i = 0; i < count; i++) {
                keys[i] = new InfiniteStorageTestKey(i);
                keys[i].cacheEncoding(engine);
                engine.insert(keys[i], 1000, Actionable.MODULATE);
            }
            long start = System.nanoTime();
            engine.flushAndAwait();
            double fullMs = (System.nanoTime() - start) / 1e6;
            for (int i = 0; i < 100_000; i++) {
                engine.insert(keys[i % 64], 64, Actionable.MODULATE);
                engine.extract(keys[i % 64], 64, Actionable.MODULATE);
            }
            start = System.nanoTime();
            for (int i = 0; i < 1_000_000; i++) {
                engine.insert(keys[i % 64], 64, Actionable.MODULATE);
                engine.extract(keys[i % 64], 64, Actionable.MODULATE);
            }
            double seconds = (System.nanoTime() - start) / 1e9;
            start = System.nanoTime();
            engine.flushAndAwait();
            double deltaMs = (System.nanoTime() - start) / 1e6;
            assertEquals(ECOInfiniteDomainState.READY, engine.getState());
            assertEquals(
                    count,
                    InfiniteStorageSnapshot.read(path).getList("entries", 10).size());
            assertEquals(HugeAmount.of(count * 1000L), engine.getStoredAmount());
            report.add(String.format(
                    Locale.ROOT,
                    "%d keys: engine %.0f operations/sec; full save %.1f ms; 64-key cumulative delta save %.1f ms, %d bytes",
                    count,
                    2_000_000 / seconds,
                    fullMs,
                    deltaMs,
                    Files.size(InfiniteStorageDelta.path(path))));
            for (int i = 0; i < 64; i++) engine.insert(keys[i], 1, Actionable.MODULATE);
            start = System.nanoTime();
            engine.flushAndAwait();
            report.add(String.format(
                    Locale.ROOT,
                    "%d keys: 64 changed quantities save %.1f ms, %d bytes",
                    count,
                    (System.nanoTime() - start) / 1e6,
                    Files.size(InfiniteStorageDelta.path(path))));
            for (int i = 0; i < count; i++) engine.insert(keys[i], 1, Actionable.MODULATE);
            start = System.nanoTime();
            engine.flushBudgeted(500_000L);
            double captureMs = (System.nanoTime() - start) / 1e6;
            engine.flushAndAwait();
            report.add(String.format(
                    Locale.ROOT,
                    "%d keys: dense background capture %.1f ms; capture + durable completion %.1f ms",
                    count,
                    captureMs,
                    (System.nanoTime() - start) / 1e6));
            assertEquals(HugeAmount.of(count * 1001L + 64), engine.getStoredAmount());
            engine.closeAndFlush();
        }
        ECOSavedDataPersistence.clear();
        Path output = Path.of("build/reports/infinite-storage-benchmark.txt");
        Files.createDirectories(output.getParent());
        Files.write(output, report);
    }
}
