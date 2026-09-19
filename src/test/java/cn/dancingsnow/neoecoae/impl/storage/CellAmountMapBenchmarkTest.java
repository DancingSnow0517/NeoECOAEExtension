package cn.dancingsnow.neoecoae.impl.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.management.ThreadMXBean;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Isolates quantity-map allocation; excludes AEKey hashing, NBT, notifications and server ticks. */
@EnabledIfEnvironmentVariable(named = "NEOECO_STORAGE_BENCHMARK", matches = "1")
class CellAmountMapBenchmarkTest {
    @Test
    void compareBoxedAndPrimitiveQuantityUpdates() throws Exception {
        var report = new ArrayList<String>();
        report.add("Quantity-map microbenchmark only; no end-to-end TPS claim. Java "
                + System.getProperty("java.version"));
        var bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        Integer[] keys = new Integer[10000];
        var boxed = new LinkedHashMap<Integer, Long>();
        var primitive = new Object2LongLinkedOpenHashMap<Integer>();
        for (int i = 0; i < keys.length; i++) {
            keys[i] = i;
            boxed.put(keys[i], 1000L);
            primitive.put(keys[i], 1000L);
        }
        for (int round = 0; round < 5; round++) {
            // Alternate measurement order after warmup to reduce ordering bias.
            for (int j = 0; j < 2; j++) {
                boolean usePrimitive = (round + j) % 2 == 0;
                long allocation = bean.getThreadAllocatedBytes(thread);
                long start = System.nanoTime();
                if (usePrimitive) {
                    for (int i = 0; i < 1000000; i++) {
                        Integer key = keys[i % keys.length];
                        primitive.put(key, primitive.getLong(key) + 64);
                        primitive.put(key, primitive.getLong(key) - 64);
                    }
                } else {
                    for (int i = 0; i < 1000000; i++) {
                        Integer key = keys[i % keys.length];
                        boxed.put(key, boxed.getOrDefault(key, 0L) + 64);
                        boxed.put(key, boxed.getOrDefault(key, 0L) - 64);
                    }
                }
                long elapsed = System.nanoTime() - start;
                long allocated = bean.getThreadAllocatedBytes(thread) - allocation;
                if (round > 0)
                    report.add(String.format(
                            Locale.ROOT,
                            "%s: 2,000,000 updates, %.3f ms, %.3f bytes/update",
                            usePrimitive ? "primitive" : "boxed",
                            elapsed / 1e6,
                            allocated / 2000000.0));
            }
        }
        for (Integer key : keys) {
            assertEquals(1000L, boxed.get(key).longValue());
            assertEquals(1000L, primitive.getLong(key));
        }
        Path output = Path.of("build/reports/cell-amount-map-benchmark.txt");
        Files.createDirectories(output.getParent());
        Files.write(output, report);
    }
}
