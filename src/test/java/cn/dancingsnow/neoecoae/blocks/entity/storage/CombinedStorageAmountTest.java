package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountCollector;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountSource;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.nio.file.Path;
import java.nio.file.Files;
import cn.dancingsnow.neoecoae.storage.StorageTestContext;
import cn.dancingsnow.neoecoae.storage.StorageWorkloadBenchmark;
import cn.dancingsnow.neoecoae.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorage;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageData;
import cn.dancingsnow.neoecoae.storage.infinite.SavedDataInfiniteStorageEngine;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CombinedStorageAmountTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() throws Exception { StorageTestContext.bootstrap(); }

    @Test void typedInsertCandidatesPreserveSimulationAndRecoveryOfLoadedStock() throws Exception {
        try (var context = new StorageTestContext(directory)) {
            var items = spy(context.cell);
            var fluids = spy(context.newFluidCell());
            var stone = AEItemKey.of(Items.STONE);
            var water = AEFluidKey.of(Fluids.WATER);
            var storage = new ECOStorageInterfaceTransfer.CombinedStorage(List.of(fluids, items), Component.empty());
            for (Actionable mode : new Actionable[]{Actionable.SIMULATE, Actionable.MODULATE}) {
                assertEquals(64, storage.insert(stone, 64, mode, IActionSource.empty()));
                assertEquals(1000, storage.insert(water, 1000, mode, IActionSource.empty()));
                assertEquals(mode == Actionable.SIMULATE ? 0 : 64, items.getAvailableStacks().get(stone));
                assertEquals(mode == Actionable.SIMULATE ? 0 : 1000, fluids.getAvailableStacks().get(water));
            }
            verify(fluids, never()).insert(eq(stone), anyLong(), any(), any());
            verify(items, never()).insert(eq(water), anyLong(), any(), any());
            // A legacy/migrated entry is still extractable even when this cell rejects new entries of its type.
            items.storageData().amounts().setLong(water, 13);
            items.storageData().setDirty();
            assertEquals(1013, storage.extract(water, 2000, Actionable.SIMULATE, IActionSource.empty()));
            assertEquals(1013, storage.extract(water, 2000, Actionable.MODULATE, IActionSource.empty()));
            assertEquals(0, storage.getAvailableStacks().get(water));
        }
    }

    @Test void specializedAndUnknownStoresKeepDynamicRulesAndAccessChecks() throws Exception {
        try (var context = new StorageTestContext(directory)) {
            boolean[] allowed = {true}, specializedAccepts = {false};
            ECOStorageCell specialized;
            try (var hooks = mockStatic(ServerLifecycleHooks.class)) {
                hooks.when(ServerLifecycleHooks::getCurrentServer).thenReturn(context.server);
                specialized = new ECOStorageCell(new ItemStack(context.stack.getItem()), context.owner) {
                    @Override protected boolean acceptsKey(AEKey key) { return specializedAccepts[0]; }
                };
            }
            var engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
            var unknown = new ECOInfiniteStorage(engine, Component.empty(), () -> true);
            var mounts = new ArrayList<MEStorage>(List.of(specialized, unknown));
            var storage = new ECOStorageInterfaceTransfer.CombinedStorage(mounts, Component.empty(), () -> allowed[0]);
            var water = AEFluidKey.of(Fluids.WATER);
            assertEquals(17, storage.insert(water, 17, Actionable.MODULATE, IActionSource.empty()));
            assertEquals(17, engine.extract(water, 100, Actionable.SIMULATE));
            specializedAccepts[0] = true;
            assertEquals(23, storage.insert(water, 23, Actionable.MODULATE, IActionSource.empty()));
            assertEquals(23, specialized.getAvailableStacks().get(water));
            allowed[0] = false;
            assertEquals(0, storage.insert(water, 3, Actionable.MODULATE, IActionSource.empty()));
            assertEquals(0, storage.extract(water, 100, Actionable.MODULATE, IActionSource.empty()));
            allowed[0] = true;
            assertEquals(40, storage.extract(water, 100, Actionable.MODULATE, IActionSource.empty()));
            assertTrue(storage.hasInventories(mounts));
            mounts.remove(0);
            assertFalse(storage.hasInventories(mounts));
            assertTrue(storage.hasInventories(List.of(specialized, unknown)), "owned mount list is immutable");
        }
    }

    /** Real cells, preserved main-class JARs, and the same full insert/extract cycle on both sides. */
    public static void benchmark(Path directory) throws Exception {
        if (!System.getProperty("neoecoae.benchmark.only", "").startsWith("candidate")) return;
        for (int count : new int[]{1, 16, 64}) {
            try (var context = new StorageTestContext(Files.createTempDirectory(directory, "candidates-"))) {
                var mounts = new ArrayList<MEStorage>();
                for (int i = 1; i < count; i++) mounts.add(context.newFluidCell());
                mounts.add(context.cell);
                var storage = new ECOStorageInterfaceTransfer.CombinedStorage(mounts, Component.empty());
                var keys = StorageTestContext.keys(500, false);
                for (AEKey key : keys) context.cell.insert(key, 1000, Actionable.MODULATE, IActionSource.empty());
                StorageWorkloadBenchmark.measure("candidate-insert-" + count + "-mounts", 500, 20, 40, cycle -> {
                    long moved = 0;
                    for (int i = 0; i < 20; i++) {
                        AEKey key = keys[(cycle * 31 + i) % keys.length];
                        moved += storage.insert(key, 2, Actionable.MODULATE, IActionSource.empty());
                        moved += storage.extract(key, 1, Actionable.MODULATE, IActionSource.empty());
                    }
                    if (moved != 60) throw new AssertionError("candidate routing");
                    return moved;
                });
            }
        }
    }
    @Test void infiniteResourceAndOrdinaryCellNeverWrapInEitherOrder() {
        AEKey key = mock(AEKey.class);
        when(key.getPrimaryKey()).thenReturn(new Object());
        when(key.getFuzzySearchMaxValue()).thenReturn(0);
        MEStorage infinite = listing(key, Long.MAX_VALUE);
        MEStorage ordinary = listing(key, 64);
        for (List<MEStorage> order : List.of(List.of(infinite, ordinary), List.of(ordinary, infinite))) {
            var storage = new ECOStorageInterfaceTransfer.CombinedStorage(order, Component.empty());
            assertEquals(Long.MAX_VALUE, storage.getAvailableStacks().get(key));
        }
    }

    @Test void mixedHostPreservesInfinityAndTheExactFiniteTotal() {
        AEKey key = mock(AEKey.class);
        when(key.getPrimaryKey()).thenReturn(new Object());
        when(key.getFuzzySearchMaxValue()).thenReturn(0);
        var infinite = mock(MEStorage.class, withSettings().extraInterfaces(ExactAmountSource.class));
        doAnswer(call -> {
            KeyCounter out = call.getArgument(0);
            out.set(key, Long.MAX_VALUE);
            return null;
        }).when(infinite).getAvailableStacks(any());
        doAnswer(call -> {
            java.util.function.BiConsumer<AEKey, ExactAmount> visitor = call.getArgument(0);
            visitor.accept(key, ExactAmount.unbounded());
            return null;
        }).when((ExactAmountSource) infinite).neoecoae$visitExactAmounts(any());
        var ordinary = listing(key, 64);
        for (List<MEStorage> order : List.of(List.of(infinite, ordinary), List.of(ordinary, infinite))) {
            var storage = new ECOStorageInterfaceTransfer.CombinedStorage(order, Component.empty());
            ExactAmountCollector.begin();
            try {
                var out = new KeyCounter();
                ExactAmountCollector.collect(storage, out, storage::getAvailableStacks);
                assertEquals(Long.MAX_VALUE, out.get(key));
                assertEquals(Map.of(key, ExactAmount.unbounded()), ExactAmountCollector.finish());
            } finally { ExactAmountCollector.abort(); }
        }
        var finite = new ECOStorageInterfaceTransfer.CombinedStorage(
            List.of(listing(key, Long.MAX_VALUE), ordinary), Component.empty());
        Map<AEKey, ExactAmount> amounts = new HashMap<>();
        finite.neoecoae$visitExactAmounts(amounts::put);
        assertEquals(Map.of(key, ExactAmount.finite(BigInteger.valueOf(Long.MAX_VALUE)
            .add(BigInteger.valueOf(64)))), amounts);
    }

    private static MEStorage listing(AEKey key, long amount) {
        MEStorage storage = mock(MEStorage.class);
        doAnswer(call -> {
            appeng.api.stacks.KeyCounter out = call.getArgument(0);
            out.add(key, amount);
            return null;
        }).when(storage).getAvailableStacks(any());
        return storage;
    }
}
