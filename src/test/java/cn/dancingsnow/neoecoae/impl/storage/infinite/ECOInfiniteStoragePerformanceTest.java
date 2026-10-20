package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.lang.management.ManagementFactory;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Opt-in measurements: timings are reports, never correctness assertions. */
@EnabledIfSystemProperty(named = "storage.performance", matches = "true")
class ECOInfiniteStoragePerformanceTest {
    private static volatile long consumed;

    @Test void measure() throws Exception {
        InventoryTestBootstrap.initialize();
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        List<String> rows = new ArrayList<>();
        rows.add("case,keys,round,ns_per_operation,bytes_per_operation");
        boolean extended = Boolean.getBoolean("storage.performance.extended");
        for (int count : extended ? new int[] {1, 1_000, 10_000, 100_000} : new int[] {1, 1_000, 10_000}) {
            AEKey[] keys = new AEKey[count];
            var engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
            for (int i = 0; i < count; i++) {
                var stack = new ItemStack(Items.STONE);
                stack.set(DataComponents.CUSTOM_NAME, Component.literal("bench" + i));
                keys[i] = AEItemKey.of(stack);
                engine.insert(keys[i], 1_000, Actionable.MODULATE);
            }
            report(rows, bean, "existing_long", count, 200_000, () -> transfer(engine, keys, 100_000));
            engine.insert(keys[0], BigInteger.TEN.pow(40), Actionable.MODULATE);
            report(rows, bean, "huge_channel_total", count, 200_000, () -> transfer(engine, keys, 100_000));
            report(rows, bean, "simulate", count, 200_000, () -> {
                long result = 0;
                for (int i = 0; i < 100_000; i++) {
                    AEKey key = keys[i % keys.length];
                    result += engine.insert(key, 1, Actionable.SIMULATE);
                    result += engine.extract(key, 1, Actionable.SIMULATE);
                }
                consumed = result;
            });
            var storage = new ECOInfiniteStorage(engine, Component.empty(), () -> true);
            int scans = extended ? Math.max(10, Math.min(10_000, 200_000 / count)) : 30;
            report(rows, bean, "exact_scan", count, scans, () -> {
                for (int i = 0; i < scans; i++) storage.neoecoae$visitExactAmounts((key, amount) -> consumed = amount.value().bitLength());
            });
            report(rows, bean, "visible_scan", count, scans, () -> {
                for (int i = 0; i < scans; i++) {
                    var out = new KeyCounter();
                    engine.getAvailableStacks(out);
                    consumed = out.get(keys[0]);
                }
            });
            if (extended) {
                report(rows, bean, "existing_view", count, 200_000, () -> {
                    long result = 0;
                    var source = appeng.api.networking.security.IActionSource.empty();
                    for (int i = 0; i < 100_000; i++) {
                        AEKey key = keys[i % keys.length];
                        result += storage.insert(key, 1, Actionable.MODULATE, source);
                        result += storage.extract(key, 1, Actionable.MODULATE, source);
                    }
                    consumed = result;
                });
                AEKey turnover = AEItemKey.of(Items.DIRT);
                report(rows, bean, "new_key_then_empty", count, 200_000, () -> {
                    for (int i = 0; i < 100_000; i++) {
                        engine.insert(turnover, 1, Actionable.MODULATE);
                        consumed = engine.extract(turnover, 1, Actionable.MODULATE);
                    }
                });
                report(rows, bean, "statistics_snapshot", count, 100, () -> {
                    for (int i = 0; i < 100; i++) {
                        engine.insert(keys[0], 1, Actionable.MODULATE);
                        consumed = engine.getTypeStats().iterator().next().storedAmount().toBigInteger().bitLength();
                        engine.extract(keys[0], 1, Actionable.MODULATE);
                    }
                });
            }
        }
        Path output = Path.of("build/reports/storage-performance-" + System.getProperty("storage.performance.label", "current") + ".csv");
        Files.createDirectories(output.getParent());
        Files.write(output, rows);
    }

    private static void transfer(SavedDataInfiniteStorageEngine engine, AEKey[] keys, int iterations) {
        long result = 0;
        for (int i = 0; i < iterations; i++) {
            AEKey key = keys[i % keys.length];
            result += engine.insert(key, 1, Actionable.MODULATE);
            result += engine.extract(key, 1, Actionable.MODULATE);
        }
        consumed = result;
    }

    private static void report(List<String> rows, com.sun.management.ThreadMXBean bean, String name,
                               int count, int operations, Runnable action) {
        for (int i = 0; i < 8; i++) action.run();
        long thread = Thread.currentThread().threadId();
        for (int round = 0; round < 5; round++) {
            long allocated = bean.getThreadAllocatedBytes(thread);
            long started = System.nanoTime();
            action.run();
            long elapsed = System.nanoTime() - started;
            allocated = bean.getThreadAllocatedBytes(thread) - allocated;
            rows.add(name + "," + count + "," + round + "," + (double) elapsed / operations + "," + (double) allocated / operations);
        }
    }
}
