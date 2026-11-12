package cn.dancingsnow.neoecoae.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.StorageCell;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountCollector;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ECOInfiniteStorageHotPathTest {
    private static final BigInteger MAX = BigInteger.valueOf(Long.MAX_VALUE);

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void primitiveAndOverflowTransfersMatchExactReferenceAndImmutableStatistics() {
        var engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        AEKey[] keys = {AEItemKey.of(Items.STONE), AEItemKey.of(Items.DIRT), AEItemKey.of(Items.COBBLESTONE)};
        BigInteger[] amounts = {BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO};
        Random random = new Random(741852);
        for (int i = 0; i < 2_000; i++) {
            int index = random.nextInt(keys.length);
            long delta = i % 29 == 0 ? Long.MAX_VALUE : random.nextInt(1_000) + 1;
            if (random.nextBoolean()) {
                assertEquals(delta, engine.insert(keys[index], delta, Actionable.MODULATE));
                amounts[index] = amounts[index].add(BigInteger.valueOf(delta));
            } else {
                long expected = amounts[index].min(BigInteger.valueOf(delta)).longValueExact();
                assertEquals(expected, engine.extract(keys[index], delta, Actionable.MODULATE));
                amounts[index] = amounts[index].subtract(BigInteger.valueOf(expected));
            }
            assertEquals(amounts[index], engine.getAmount(keys[index]).toBigInteger());
            if (i % 31 == 0) {
                BigInteger sum = amounts[0].add(amounts[1]).add(amounts[2]);
                long types = java.util.Arrays.stream(amounts).filter(a -> a.signum() > 0).count();
                if (types == 0) {
                    assertTrue(engine.getTypeStats().isEmpty());
                    continue;
                }
                var stats = engine.getTypeStats().iterator().next();
                assertEquals(sum, stats.storedAmount().toBigInteger());
                assertEquals(types, stats.storedTypes());
                engine.insert(keys[index], 1, Actionable.MODULATE);
                amounts[index] = amounts[index].add(BigInteger.ONE);
                assertEquals(sum, stats.storedAmount().toBigInteger(), "published snapshots stay immutable");
            }
        }
        for (int i = 0; i < keys.length; i++) {
            while (amounts[i].signum() > 0) {
                long taken = engine.extract(keys[i], Long.MAX_VALUE, Actionable.MODULATE);
                amounts[i] = amounts[i].subtract(BigInteger.valueOf(taken));
            }
        }
        assertTrue(engine.getTypeStats().isEmpty());
        engine.insert(keys[0], 7, Actionable.MODULATE);
        assertEquals(HugeAmount.of(7), engine.getTypeStats().iterator().next().storedAmount());
    }

    @Test void hugeStatisticsDeltaFlushesInBothDirectionsAndDowngrades() {
        var total = new MutableInfiniteStorageTotal();
        BigInteger expected = MAX.multiply(BigInteger.valueOf(4));
        total.add(expected);
        for (int i = 0; i < 4; i++) { total.subtract(Long.MAX_VALUE); expected = expected.subtract(MAX); }
        assertEquals(HugeAmount.ZERO, total.snapshot());
        for (int i = 0; i < 4; i++) { total.add(Long.MAX_VALUE); expected = expected.add(MAX); }
        assertEquals(expected, total.snapshot().toBigInteger());
        total.subtract(expected.subtract(BigInteger.ONE));
        assertEquals(HugeAmount.of(1), total.snapshot());
    }

    @Test void simulationsDoNotChangeInventoryStatisticsDirtyFlagOrRevision() {
        var data = ECOInfiniteStorageData.createNew();
        var engine = new SavedDataInfiniteStorageEngine(data);
        AEKey key = AEItemKey.of(Items.STONE);
        engine.insert(key, 7, Actionable.MODULATE);
        var stats = engine.getTypeStats();
        long revision = engine.revision();
        data.setDirty(false);
        for (int i = 0; i < 100; i++) {
            assertEquals(Long.MAX_VALUE, engine.insert(key, Long.MAX_VALUE, Actionable.SIMULATE));
            assertEquals(7, engine.extract(key, Long.MAX_VALUE, Actionable.SIMULATE));
        }
        assertSame(stats, engine.getTypeStats());
        assertFalse(data.isDirty());
        assertEquals(revision, engine.revision());
        assertEquals(HugeAmount.of(7), engine.getAmount(key));
    }

    @Test void exactListingAndCombinedCollectionKeepAllKeysAndSaturateVisibleTotals() {
        var engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        var storage = new ECOInfiniteStorage(engine, Component.empty(), () -> true);
        AEKey stone = AEItemKey.of(Items.STONE), dirt = AEItemKey.of(Items.DIRT);
        engine.insert(stone, MAX.add(BigInteger.TEN), Actionable.MODULATE);
        engine.insert(dirt, 7, Actionable.MODULATE);
        Map<AEKey, ExactAmount> exact = new HashMap<>();
        storage.neoecoae$visitExactAmounts(exact::put);
        assertEquals(Map.of(stone, ExactAmount.finite(MAX.add(BigInteger.TEN)), dirt, ExactAmount.finite(BigInteger.valueOf(7))), exact);
        var out = new KeyCounter();
        out.set(stone, 64);
        storage.neoecoae$listWithExactAmounts(out, (key, amount) -> {});
        assertEquals(Long.MAX_VALUE, out.get(stone));
        assertEquals(7, out.get(dirt));
        ExactAmountCollector.begin();
        try {
            out = new KeyCounter();
            assertTrue(ExactAmountCollector.collectCombined(storage, out));
            assertEquals(Map.of(stone, exact.get(stone)), ExactAmountCollector.finish(),
                    "faithful ordinary long stock does not require an exact side-channel value");
            assertEquals(7, out.get(dirt));
        } finally { ExactAmountCollector.abort(); }
        assertFalse(ExactAmountCollector.collectCombined(storage, new KeyCounter()));
    }

    @Test void restoreAvailabilityUpdatesImmediatelyAndStatisticsFollowRestoration() {
        var engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        AEKey stone = AEItemKey.of(Items.STONE), dirt = AEItemKey.of(Items.DIRT);
        engine.insert(stone, 10, Actionable.MODULATE);
        engine.insert(dirt, 3, Actionable.MODULATE);
        UUID transaction = UUID.randomUUID(), target = UUID.randomUUID();
        assertTrue(engine.canUseMountedStorage());
        assertTrue(engine.reserveRestores(Map.of(stone, transaction), Set.of(target), Map.of(stone,
                Map.of(target, new ECOInfiniteStorageEngine.RestoreTargetAmounts(0, 10, 10)))));
        assertFalse(engine.canUseMountedStorage());
        assertFalse(engine.contains(stone));
        Map<AEKey, BigInteger> exact = new HashMap<>();
        engine.visitExactAmounts(exact::put);
        assertEquals(Map.of(dirt, BigInteger.valueOf(3)), exact);
        assertEquals(0, engine.insert(stone, 1, Actionable.MODULATE));
        assertTrue(engine.finishRestore(stone, transaction));
        assertTrue(engine.canUseMountedStorage());
        assertEquals(HugeAmount.of(3), engine.getTypeStats().iterator().next().storedAmount());
    }

    @Test void recoveryReadOnlyKeepsReadsButRejectsRealWrites() {
        var data = ECOInfiniteStorageData.createNew();
        var engine = new SavedDataInfiniteStorageEngine(data);
        AEKey key = AEItemKey.of(Items.STONE);
        engine.insert(key, 10, Actionable.MODULATE);
        data.markIncompleteLegacy(java.util.List.of("fixture"));
        assertFalse(engine.canUseMountedStorage());
        assertEquals(10, engine.extract(key, 20, Actionable.SIMULATE));
        assertEquals(0, engine.extract(key, 20, Actionable.MODULATE));
        assertEquals(0, engine.insert(key, 1, Actionable.MODULATE));
        assertTrue(engine.contains(key));
        Map<AEKey, BigInteger> exact = new HashMap<>();
        engine.visitExactAmounts(exact::put);
        assertEquals(Map.of(key, BigInteger.TEN), exact);
    }

    @Test void combinedLongSourcesAggregateExactlyWithOrdinaryStorageInEitherOrder() {
        AEKey key = AEItemKey.of(Items.STONE);
        var engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        engine.insert(key, Long.MAX_VALUE, Actionable.MODULATE);
        var storage = new ECOInfiniteStorage(engine, Component.empty(), () -> true);
        var ordinary = mock(appeng.api.storage.MEStorage.class);
        for (boolean reverse : new boolean[] {false, true}) {
            ExactAmountCollector.begin();
            try {
                var contribution = new KeyCounter();
                contribution.set(key, 64);
                if (reverse) ExactAmountCollector.observe(ordinary, contribution);
                assertTrue(ExactAmountCollector.collectCombined(storage, new KeyCounter()));
                if (!reverse) ExactAmountCollector.observe(ordinary, contribution);
                assertEquals(Map.of(key, ExactAmount.finite(MAX.add(BigInteger.valueOf(64)))), ExactAmountCollector.finish());
            } finally { ExactAmountCollector.abort(); }
        }
    }

    @Test void worldOwnedDomainDoesNotOpenExternalCellInventoriesForSimulationOrInsertion() {
        AEItemKey key = AEItemKey.of(Items.STONE);
        var source = IActionSource.empty();
        var engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        var storage = new ECOInfiniteStorage(engine, Component.empty(), () -> true);
        var dynamic = mock(StorageCell.class);
        when(dynamic.canFitInsideCell()).thenReturn(false);
        try (var cells = mockStatic(StorageCells.class)) {
            cells.when(() -> StorageCells.getCellInventory(key.getReadOnlyStack(), null)).thenReturn(dynamic);
            assertEquals(1, storage.insert(key, 1, Actionable.SIMULATE, source));
            assertTrue(engine.isEmpty());
            for (int i = 0; i < 2; i++) assertEquals(1, storage.insert(key, 1, Actionable.MODULATE, source));
            BigInteger huge = MAX.add(BigInteger.TEN);
            assertEquals(huge, storage.insertBigInteger(key, huge, Actionable.SIMULATE, source));
            assertEquals(HugeAmount.of(2), engine.getAmount(key));
            assertEquals(huge, storage.insertBigInteger(key, huge, Actionable.MODULATE, source));
            assertEquals(huge.add(BigInteger.TWO), engine.getAmount(key).toBigInteger());
            cells.verifyNoInteractions();
            verifyNoInteractions(dynamic);
        }
    }

    @Test void opaqueCellDataAndExactQuantitiesSurviveReloadAndExtraction() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var codec = new ECOInfiniteStorageData.KeyCodec() {
            @Override public CompoundTag encode(AEKey key) {
                return (CompoundTag) cn.dancingsnow.neoecoae.util.LegacyNbt.saveItem(registries, ((AEItemKey) key).getReadOnlyStack());
            }
            @Override public AEKey decode(CompoundTag tag) {
                return AEItemKey.of(cn.dancingsnow.neoecoae.util.LegacyNbt.parseItem(registries, tag));
            }
        };
        var state = ECOInfiniteStorageData.load(ECOInfiniteStorageData.createNew().save(new CompoundTag(), registries), registries, codec);
        var engine = new SavedDataInfiniteStorageEngine(state);
        var storage = new ECOInfiniteStorage(engine, Component.empty(), () -> true);
        var payload = new CompoundTag();
        payload.store("external_cell_id", net.minecraft.core.UUIDUtil.CODEC, UUID.randomUUID());
        payload.putLong("stored_amount", 123456789L);
        var stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(payload));
        AEItemKey key = AEItemKey.of(stack);
        var original = key.getReadOnlyStack().copy();
        BigInteger amount = MAX.add(BigInteger.TEN);
        try (var cells = mockStatic(StorageCells.class)) {
            assertEquals(amount, storage.insertBigInteger(key, amount, Actionable.MODULATE, IActionSource.empty()));
            var reloaded = ECOInfiniteStorageData.load(state.save(new CompoundTag(), registries), registries, codec);
            var again = new SavedDataInfiniteStorageEngine(reloaded);
            var view = new ECOInfiniteStorage(again, Component.empty(), () -> true);
            assertEquals(amount, again.getAmount(key).toBigInteger());
            var listing = new KeyCounter();
            view.getAvailableStacks(listing);
            var restoredKey = (AEItemKey) listing.iterator().next().getKey();
            assertTrue(ItemStack.isSameItemSameComponents(original, restoredKey.getReadOnlyStack()));
            assertEquals(Long.MAX_VALUE, view.extract(restoredKey, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty()));
            assertEquals(10, view.extract(restoredKey, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty()));
            assertTrue(again.isEmpty());
            assertTrue(ItemStack.isSameItemSameComponents(original, key.getReadOnlyStack()));
            cells.verifyNoInteractions();
        }
    }

    @Test void removingCellProbesKeepsAccessAndRecoveryWriteGuards() {
        var state = ECOInfiniteStorageData.createNew();
        var engine = new SavedDataInfiniteStorageEngine(state);
        var disabled = new ECOInfiniteStorage(engine, Component.empty(), () -> false);
        var enabled = new ECOInfiniteStorage(engine, Component.empty(), () -> true);
        var key = AEItemKey.of(Items.STONE);
        var source = IActionSource.empty();
        assertEquals(0, disabled.insert(key, 1, Actionable.MODULATE, source));
        assertEquals(BigInteger.ZERO, disabled.insertBigInteger(key, MAX, Actionable.MODULATE, source));
        assertEquals(0, enabled.insert(null, 1, Actionable.MODULATE, source));
        assertEquals(0, enabled.insert(key, -1, Actionable.MODULATE, source));
        assertEquals(BigInteger.ZERO, enabled.insertBigInteger(key, null, Actionable.MODULATE, source));
        assertEquals(3, enabled.insert(key, 3, Actionable.MODULATE, source));
        state.markIncompleteLegacy(java.util.List.of("fixture"));
        assertEquals(0, enabled.insert(key, 1, Actionable.MODULATE, source));
        assertEquals(BigInteger.ZERO, enabled.insertBigInteger(key, MAX, Actionable.MODULATE, source));
        assertEquals(HugeAmount.of(3), engine.getAmount(key));
    }
}
