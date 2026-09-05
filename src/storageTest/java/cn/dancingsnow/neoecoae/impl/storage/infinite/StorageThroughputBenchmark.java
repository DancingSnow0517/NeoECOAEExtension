package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.impl.storage.StorageTestKey;
import java.lang.management.ManagementFactory;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Repeatable JVM smoke benchmark, not a substitute for profiling a running modpack. */
class StorageThroughputBenchmark {
    @TempDir Path directory;

    @Test
    void inMemoryOperationsAtDifferentDomainSizes() {
        for (int size : new int[]{1_000, 10_000, 100_000}) {
            var engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew(), null, directory.resolve("unused"));
            StorageTestKey[] keys = new StorageTestKey[size];
            for (int i = 0; i < size; i++) {
                keys[i] = new StorageTestKey("key" + i);
                engine.insert(keys[i], 1_000L, Actionable.MODULATE);
            }
            for (int i = 0; i < 50_000; i++) mutate(engine, keys[i % size]);
            var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
            long allocated = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
            long[] samples = new long[100];
            long total = System.nanoTime();
            for (int sample = 0; sample < samples.length; sample++) {
                long start = System.nanoTime();
                for (int i = 0; i < 1_000; i++) mutate(engine, keys[(sample * 1_000 + i) % size]);
                samples[sample] = System.nanoTime() - start;
            }
            total = System.nanoTime() - total;
            long bytes = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocated;
            Arrays.sort(samples);
            assertEquals(HugeAmount.of(1_000L), engine.getAmount(keys[size - 1]));
            System.out.printf(Locale.ROOT, "MEMORY keys=%d operations=200000 ops_per_second=%.0f p95_batch_ms=%.3f bytes_per_operation=%.1f%n",
                size, 200_000D * 1_000_000_000D / total, samples[94] / 1_000_000D, bytes / 200_000D);
        }
    }

    private static void mutate(SavedDataInfiniteStorageEngine engine, StorageTestKey key) {
        engine.insert(key, 1L, Actionable.MODULATE);
        engine.extract(key, 1L, Actionable.MODULATE);
    }

    @Test
    void durableDeltaVersusDurableFullSnapshot() throws Exception {
        ListTag entries = new ListTag();
        for (int i = 0; i < 10_000; i++) {
            CompoundTag key = new CompoundTag(); key.putString("id", "key" + i);
            CompoundTag entry = new CompoundTag(); entry.put("key", key); entry.put("amount", HugeAmount.of(100L).write());
            entries.add(entry);
        }
        CompoundTag baseline = new CompoundTag(); baseline.put("entries", entries);
        Path full = directory.resolve("full.dat");
        Path temp = directory.resolve("full.temp");
        long start = System.nanoTime();
        for (int i = 0; i < 20; i++) {
            entries.getCompound(0).put("amount", HugeAmount.of(i).write());
            NbtIo.writeCompressed(baseline, temp);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
            Files.move(temp, full, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        long fullNanos = System.nanoTime() - start;
        Path store = directory.resolve("journal");
        long deltaNanos;
        int shard = InfiniteStorageJournal.shard(entries.getCompound(0).getCompound("key"));
        try (var journal = new InfiniteStorageJournal(store)) {
            journal.initialize(baseline);
            start = System.nanoTime();
            for (int i = 0; i < 20; i++) {
                ListTag deltaEntries = new ListTag();
                CompoundTag changed = entries.getCompound(0).copy(); changed.put("amount", HugeAmount.of(i).write());
                deltaEntries.add(changed);
                CompoundTag delta = new CompoundTag(); delta.put("entries", deltaEntries);
                journal.append(shard, delta);
            }
            deltaNanos = System.nanoTime() - start;
        }
        System.out.printf(Locale.ROOT, "PERSISTENCE keys=10000 commits=20 full_snapshot_ms=%.3f journal_ms=%.3f journal_bytes=%d%n",
            fullNanos / 1_000_000D, deltaNanos / 1_000_000D, Files.size(store.resolve(shard + "/journal.log")));
    }
}
