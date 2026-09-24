package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.impl.storage.ECOSavedDataPersistence;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real Forge registry + AE2 storage facade acceptance, isolated from user saves. */
@GameTestHolder("neoecoae")
@PrefixGameTestTemplate(false)
public final class InfiniteStorageAcceptanceGameTest {
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void crossFileRestoreCommitsRealCellBeforeSourceRetirement(GameTestHelper helper) throws Exception {
        UUID domain = UUID.randomUUID();
        var server = helper.getLevel().getServer();
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("data/neoecoae_infinite/domain_" + domain + ".dat");
        var engine = SavedDataInfiniteStorageEngine.createNew(
                domain, server.overworld().getDataStorage(), file);
        var iron = AEItemKey.of(Items.IRON_INGOT);
        engine.insert(iron, 321, Actionable.MODULATE);
        engine.flushAndAwait();
        var cellStack = NEItems.ECO_ITEM_CELL_256M.asStack();
        var cell = new ECOStorageCell(cellStack, () -> {});
        KeyCounter planned = new KeyCounter();
        planned.add(iron, 321);
        var target = cell.transferSnapshot(planned);
        helper.assertTrue(
                cell.extract(iron, 321, Actionable.SIMULATE, IActionSource.empty()) == 0,
                "planning mutated the destination before commit");
        helper.assertTrue(engine.restoreTo(List.of(target)), "cross-file restore failed");
        cell.acceptTransferSnapshot(target);
        helper.assertTrue(
                !ECOStorageCell.canStoreKeyInsideStorageCell(AEItemKey.of(cellStack)),
                "filled storage cell bypassed nesting guard");
        helper.assertTrue(engine.isEmpty(), "live source was not retired");
        helper.assertTrue(
                InfiniteStorageSnapshot.read(file).getList("entries", 10).isEmpty(), "source retirement not durable");
        var targetData = cn.dancingsnow.neoecoae.impl.storage.AtomicSavedDataFile.read(target.file());
        helper.assertTrue(
                targetData.getList("entries", 10).getCompound(0).getLong("amount") == 321,
                "target quantity not durable");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void sustainedIoAndWorldSave(GameTestHelper helper) throws Exception {
        UUID domain = UUID.randomUUID();
        String dataName = "neoecoae_performance_" + domain;
        Path directory = helper.getLevel()
                .getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .toAbsolutePath();
        Files.createDirectories(directory);
        Path path = directory.resolve(dataName + ".dat");
        var dataStorage = helper.getLevel().getServer().overworld().getDataStorage();
        var engine = SavedDataInfiniteStorageEngine.createNew(domain, dataStorage, path);
        dataStorage.set(dataName, engine);
        var storage = new ECOInfiniteStorage(engine, Component.literal("acceptance"));
        List<String> report = new ArrayList<>();
        report.add("Forge 1.20.1 acceptance; actual AE item/fluid keys and MEStorage facade, not a mounted network.");
        report.add("Java " + System.getProperty("java.version") + "; domain=" + directory);
        int count = 100_000;
        AEKey[] keys = new AEKey[count];
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            ItemStack stack = new ItemStack(Items.COBBLESTONE);
            stack.getOrCreateTag().putInt("acceptance_id", i);
            keys[i] = AEItemKey.of(stack);
            helper.assertTrue(
                    storage.insert(keys[i], 1000, Actionable.MODULATE, IActionSource.empty()) == 1000,
                    "first insert rejected");
        }
        AEKey water = AEFluidKey.of(Fluids.WATER);
        helper.assertTrue(
                storage.insert(water, 1_000_000, Actionable.MODULATE, IActionSource.empty()) == 1_000_000,
                "fluid insert rejected");
        report.add(String.format(
                Locale.ROOT,
                "First-time encoding and insertion of %d keys: %.3f ms",
                count,
                (System.nanoTime() - start) / 1e6));
        start = System.nanoTime();
        engine.flushAndAwait();
        report.add(String.format(Locale.ROOT, "Initial full commit: %.3f ms", (System.nanoTime() - start) / 1e6));
        long[] tickWork = new long[400];
        long[] operationWork = new long[tickWork.length];
        long[] saveWork = new long[tickWork.length];
        long[] gcMillis = new long[tickWork.length];
        var collectors = ManagementFactory.getGarbageCollectorMXBeans();
        List<Long> saves = new ArrayList<>();
        for (int tick = 0; tick < tickWork.length; tick++) {
            final int index = tick;
            helper.runAtTickTime(tick + 1, () -> {
                long gcBefore = gcTime(collectors);
                long began = System.nanoTime();
                // 20,000 facade calls/tick, distributed across the full inventory.
                for (int j = 0; j < 10_000; j++) {
                    AEKey key = keys[(index * 10_000 + j) % keys.length];
                    helper.assertTrue(
                            storage.extract(key, 64, Actionable.MODULATE, IActionSource.empty()) == 64,
                            "extraction differs");
                    helper.assertTrue(
                            storage.insert(key, 64, Actionable.MODULATE, IActionSource.empty()) == 64,
                            "insertion differs");
                }
                operationWork[index] = System.nanoTime() - began;
                if (index % 40 == 39) {
                    long saving = System.nanoTime();
                    dataStorage.save();
                    saveWork[index] = System.nanoTime() - saving;
                    saves.add(saveWork[index]);
                    helper.assertTrue(engine.isHealthy() && !engine.needsPersistence(), "commit failed");
                }
                tickWork[index] = System.nanoTime() - began;
                gcMillis[index] = gcTime(collectors) - gcBefore;
            });
        }
        helper.runAtTickTime(402, () -> {
            try {
                report.add(summary("20k calls/tick INCLUDING save", tickWork));
                Integer[] slowest = new Integer[tickWork.length];
                for (int i = 0; i < slowest.length; i++) slowest[i] = i;
                Arrays.sort(slowest, (left, right) -> Long.compare(tickWork[right], tickWork[left]));
                for (int i = 0; i < 5; i++) {
                    int tick = slowest[i];
                    report.add(String.format(
                            Locale.ROOT,
                            "Slow tick %d: total=%.3f ms, operations=%.3f ms, save=%.3f ms, JVM GC=%d ms",
                            tick,
                            tickWork[tick] / 1e6,
                            operationWork[tick] / 1e6,
                            saveWork[tick] / 1e6,
                            gcMillis[tick]));
                }
                engine.verifyPersistence();
                report.add(summary(
                        "World saves after net-zero I/O",
                        saves.stream().mapToLong(Long::longValue).toArray()));
                long reading = System.nanoTime();
                var reloaded = SavedDataInfiniteStorageEngine.load(
                        InfiniteStorageSnapshot.read(path),
                        domain,
                        helper.getLevel().getDataStorage(),
                        path);
                report.add(String.format(
                        Locale.ROOT, "Read + AE key decode: %.3f ms", (System.nanoTime() - reading) / 1e6));
                for (AEKey key : keys)
                    helper.assertTrue(reloaded.getAmount(key).equals(HugeAmount.of(1000)), "reload amount");
                helper.assertTrue(reloaded.getAmount(water).equals(HugeAmount.of(1_000_000)), "fluid reload");
                helper.assertTrue(reloaded.getStoredTypes() == count + 1, "reload type count");
                // Exercise the same normal-matrix capacity preflight used by controller restoration.
                for (int size : new int[] {1000, 5000, 10000}) {
                    var cell = new ECOStorageCell(NEItems.ECO_ITEM_CELL_256M.asStack(), null);
                    KeyCounter simulated = new KeyCounter();
                    long planning = System.nanoTime();
                    long accepted = 0;
                    for (int i = 0; i < size; i++) {
                        long amount = cell.simulateInsertForMigration(keys[i], 1000, simulated);
                        simulated.add(keys[i], amount);
                        accepted += amount;
                    }
                    report.add(String.format(
                            Locale.ROOT,
                            "Native L9 restore capacity preflight %d keys: %.3f ms; accepted=%d",
                            size,
                            (System.nanoTime() - planning) / 1e6,
                            accepted));
                }
                List<ECOInfiniteStorageEngine.HugeStack> transfer = new ArrayList<>();
                for (int i = 0; i < 10000; i++)
                    transfer.add(new ECOInfiniteStorageEngine.HugeStack(keys[i], HugeAmount.of(7)));
                long migrating = System.nanoTime();
                UUID transaction = UUID.randomUUID();
                helper.assertTrue(engine.applyTransferOnce(transaction, transfer), "transfer commit");
                report.add(String.format(
                        Locale.ROOT, "10000-key atomic transfer: %.3f ms", (System.nanoTime() - migrating) / 1e6));
                helper.assertTrue(engine.applyTransferOnce(transaction, transfer), "transfer replay");
                helper.assertTrue(engine.getAmount(keys[0]).equals(HugeAmount.of(1007)), "transfer duplicated");
                storage.insert(water, 1, Actionable.MODULATE, IActionSource.empty());
                long worldSave = System.nanoTime();
                helper.getLevel().getServer().saveEverything(false, true, true);
                report.add(String.format(
                        Locale.ROOT, "World save with flush: %.3f ms", (System.nanoTime() - worldSave) / 1e6));
                helper.assertTrue(!engine.needsPersistence(), "world save returned before domain commit");
                engine.closeAndFlush();
                reloaded.closeAndFlush();
                ECOSavedDataPersistence.unregister(engine);
                ECOSavedDataPersistence.unregister(reloaded);
                report.add("Correctness: PASS. Timing acceptance requires max workload <= 50 ms, p99 <= 25 ms.");
                long[] sorted = tickWork.clone();
                Arrays.sort(sorted);
                report.add("Timing verdict: "
                        + (sorted[sorted.length - 1] <= 50_000_000
                                        && sorted[(int) Math.ceil(sorted.length * .99) - 1] <= 25_000_000
                                ? "PASS"
                                : "FAIL"));
                Files.write(Path.of("forge-storage-acceptance.txt"), report);
                helper.succeed();
            } catch (Exception e) {
                helper.fail(e.toString());
            }
        });
    }

    private static String summary(String name, long[] nanos) {
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        return String.format(
                Locale.ROOT,
                "%s: n=%d, p50=%.3f ms, p95=%.3f ms, p99=%.3f ms, max=%.3f ms, over50ms=%d",
                name,
                sorted.length,
                sorted[(sorted.length - 1) / 2] / 1e6,
                sorted[(int) Math.ceil(sorted.length * .95) - 1] / 1e6,
                sorted[(int) Math.ceil(sorted.length * .99) - 1] / 1e6,
                sorted[sorted.length - 1] / 1e6,
                Arrays.stream(sorted).filter(n -> n > 50_000_000).count());
    }

    private static long gcTime(List<GarbageCollectorMXBean> collectors) {
        long millis = 0;
        for (var collector : collectors) {
            millis += Math.max(0L, collector.getCollectionTime());
        }
        return millis;
    }
}
