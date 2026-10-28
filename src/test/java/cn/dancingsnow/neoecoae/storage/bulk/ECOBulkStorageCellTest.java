package cn.dancingsnow.neoecoae.storage.bulk;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeInventory;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.items.ECOBulkStorageCellItem;
import cn.dancingsnow.neoecoae.platform.nbt.NEItemData;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ECOBulkStorageCellTest {
    static AEItemKey nugget, ingot, block;
    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
        nugget = AEItemKey.of(Items.IRON_NUGGET); ingot = AEItemKey.of(Items.IRON_INGOT); block = AEItemKey.of(Items.IRON_BLOCK);
    }
    @BeforeEach void recipes() { ECOCompressionService.install(List.of(iron(9))); }
    static BulkUnits iron(int ratio) {
        return new BulkUnits(List.of(nugget, ingot, block), List.of(BigInteger.ONE, BigInteger.valueOf(ratio), BigInteger.valueOf(ratio * 9L)));
    }
    static Fixture fixture(AEItemKey... markers) { return new Fixture(markers); }
    private static appeng.util.ConfigInventory markerConfig() {
        return appeng.util.ConfigInventory.configTypes(key -> AEKeyType.items().contains(key), 25, () -> {});
    }
    static final class Fixture {
        final ItemStack stack;
        final ECOBulkStorageCell cell;
        Fixture(AEItemKey... markers) {
            stack = new ItemStack(Items.PAPER);
            ECOBulkStorageCellItem.writeConfiguredStacks(stack, java.util.Arrays.stream(markers).map(key -> new GenericStack(key, 0)).toList());
            cell = restore(stack);
        }
        ECOBulkStorageCell restore(ItemStack saved) {
            // Vanilla's test bootstrap freezes item registration; run the real inventory methods
            // with an ordinary carrier stack, rather than inventing an unregistered mod item.
            try {
                var cell = mock(ECOBulkStorageCell.class, CALLS_REAL_METHODS);
                for (String name : List.of("accounts", "cutoffs", "pending")) {
                    var field = ECOBulkStorageCell.class.getDeclaredField(name); field.setAccessible(true); field.set(cell, new LinkedHashMap<>());
                }
                var data = ECOBulkStorageCell.class.getDeclaredField("unresolved"); data.setAccessible(true); data.set(cell, new ListTag());
                var stackField = ECOBulkStorageCell.class.getDeclaredField("stack"); stackField.setAccessible(true); stackField.set(cell, saved);
                doReturn(25L).when(cell).getTotalItemTypes();
                var load = ECOBulkStorageCell.class.getDeclaredMethod("load"); load.setAccessible(true); load.invoke(cell);
                return cell;
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
    }
    private static Map<AEItemKey, BigInteger> exact(ECOBulkStorageCell cell) {
        Map<AEItemKey, BigInteger> result = new LinkedHashMap<>();
        cell.neoecoae$visitExactAmounts((key, amount) -> { assertFalse(amount.infinite()); result.put((AEItemKey) key, amount.value()); });
        return result;
    }

    @Test void quantitiesBeyondLongRoundTripAndOnlyTheBoundarySaturates() {
        var f = fixture(ingot);
        assertEquals(Long.MAX_VALUE, f.cell.insert(ingot, Long.MAX_VALUE, Actionable.SIMULATE, null));
        assertTrue(f.cell.getAvailableStacks().isEmpty());
        for (int i = 0; i < 3; i++) assertEquals(Long.MAX_VALUE, f.cell.insert(ingot, Long.MAX_VALUE, Actionable.MODULATE, null));
        BigInteger count = BulkUnits.LONG_MAX.multiply(BigInteger.valueOf(3));
        assertEquals(count, exact(f.cell).get(ingot));
        assertEquals(Long.MAX_VALUE, f.cell.getAvailableStacks().get(ingot));
        var restored = f.restore(f.stack.copy());
        for (int i = 0; i < 3; i++) assertEquals(Long.MAX_VALUE, restored.extract(ingot, Long.MAX_VALUE, Actionable.MODULATE, null));
        assertTrue(restored.getAvailableStacks().isEmpty());
        assertTrue(restored.canFitInsideCell());
    }
    @Test void arbitraryFormsAndRemaindersConserveEveryAtom() {
        var f = fixture(block);
        assertEquals(1, f.cell.insert(block, 1, Actionable.MODULATE, null));
        assertEquals(3, f.cell.insert(ingot, 3, Actionable.MODULATE, null));
        assertEquals(1, f.cell.insert(nugget, 1, Actionable.MODULATE, null));
        assertEquals(Map.of(block, BigInteger.ONE, ingot, BigInteger.valueOf(3), nugget, BigInteger.ONE), exact(f.cell));
        assertEquals(1, f.cell.extract(block, 2, Actionable.MODULATE, null));
        assertEquals(3, f.cell.extract(ingot, 4, Actionable.MODULATE, null));
        assertEquals(1, f.cell.extract(nugget, 8, Actionable.MODULATE, null));
        assertTrue(f.cell.canFitInsideCell());
    }
    @Test void partialExtractionNeverSubtractsFractionalItems() {
        var f = fixture(ingot);
        f.cell.insert(nugget, 10, Actionable.MODULATE, null);
        assertEquals(1, f.cell.extract(ingot, 2, Actionable.MODULATE, null));
        assertEquals(Map.of(nugget, BigInteger.ONE), exact(f.cell));
        assertEquals(0, f.cell.extract(ingot, 1, Actionable.MODULATE, null));
    }
    @Test void cutoffChangesOnlyProjectionAndSimulationsNeverMutate() {
        var f = fixture(ingot);
        f.cell.insert(block, 1, Actionable.MODULATE, null);
        var saved = f.cell.getStoredEntries();
        assertEquals(block, f.cell.cycleCompressionCutoff(ingot, 1));
        assertEquals(Map.of(block, BigInteger.ONE), exact(f.cell));
        assertEquals(saved, f.cell.getStoredEntries());
        assertEquals(9, f.cell.extract(ingot, 20, Actionable.SIMULATE, null));
        assertEquals(saved, f.cell.getStoredEntries());
    }
    @Test void removedMarkersKeepOldStockAndCannotBypassTypeLimit() {
        var f = fixture(ingot);
        doReturn(1L).when(f.cell).getTotalItemTypes();
        f.cell.insert(ingot, 9, Actionable.MODULATE, null);
        AEItemKey paper = AEItemKey.of(Items.PAPER);
        ECOBulkStorageCellItem.writeConfiguredStacks(f.stack, List.of(new GenericStack(paper, 0)));
        assertEquals(0, f.cell.insert(ingot, 1, Actionable.MODULATE, null));
        assertEquals(0, f.cell.insert(paper, 1, Actionable.MODULATE, null));
        assertEquals(9, f.cell.extract(ingot, 9, Actionable.MODULATE, null));
        assertEquals(1, f.cell.insert(paper, 1, Actionable.MODULATE, null));
    }
    @Test void componentsArePartOfTheStorageIdentity() {
        var named = new ItemStack(Items.STICK);
        named.setHoverName(Component.literal("Named"));
        var key = AEItemKey.of(named); var f = fixture(key);
        assertEquals(0, f.cell.insert(AEItemKey.of(Items.STICK), 1, Actionable.MODULATE, null));
        assertEquals(4, f.cell.insert(key, 4, Actionable.MODULATE, null));
        assertEquals(4, f.restore(f.stack.copy()).extract(key, 4, Actionable.MODULATE, null));
    }
    @Test void recipeReloadRecoversHistoricalBaseWithoutReinterpretingStock() {
        var f = fixture(ingot); f.cell.insert(ingot, 10, Actionable.MODULATE, null);
        ECOCompressionService.install(List.of(iron(4)));
        assertEquals(Map.of(nugget, BigInteger.valueOf(90)), exact(f.cell));
        assertEquals(0, f.cell.extract(ingot, 10, Actionable.MODULATE, null));
        assertEquals(0, f.cell.insert(ingot, 1, Actionable.MODULATE, null));
        assertEquals(90, f.cell.extract(nugget, 100, Actionable.MODULATE, null));
        assertEquals(1, f.cell.insert(ingot, 1, Actionable.MODULATE, null));
    }
    @Test void oldLongFormatIsMigratedWithoutLosingUnitsOrUnknownEntries() {
        var f = fixture(ingot);
        var data = new CompoundTag(); data.putInt("version", 1);
        var entry = BulkItemKeys.write(ingot); entry.putLong("units", Long.MAX_VALUE);
        var table = new ListTag(); var chain = iron(9);
        for (int i = 0; i < chain.items().size(); i++) {
            var unit = BulkItemKeys.write(chain.items().get(i)); unit.putLong("factor", chain.factors().get(i).longValueExact()); table.add(unit);
        }
        entry.put("definition", table);
        var entries = new ListTag(); entries.add(entry);
        var missing = new CompoundTag(); missing.putString("item", "missing:unknown"); missing.putLong("units", 27); entries.add(missing);
        data.put("entries", entries); var custom = new CompoundTag(); custom.put("neoecoae_mega_long_bulk", data);
        NEItemData.update(f.stack, tag -> tag.merge(custom));
        var migrated = f.restore(f.stack); assertEquals(1, migrated.getUnresolvedEntryCount());
        assertEquals(BulkUnits.LONG_MAX, migrated.getStoredEntries().get(ingot));
        migrated.persist();
        var again = f.restore(f.stack.copy());
        assertEquals(migrated.getStoredEntries(), again.getStoredEntries()); assertEquals(1, again.getUnresolvedEntryCount());
        assertFalse(again.canFitInsideCell());
    }
    @Test void queuedOutputsSurviveSavePartialAcceptanceAndFurtherRetries() {
        var f = fixture(ingot);
        BigInteger output = BulkUnits.LONG_MAX.add(BigInteger.valueOf(5));
        f.cell.enqueueOutput(nugget, output);
        assertFalse(f.cell.canFitInsideCell());
        var cell = f.restore(f.stack.copy());
        MEStorage network = mock(MEStorage.class);
        when(network.insert(eq(nugget), anyLong(), eq(Actionable.MODULATE), isNull())).thenReturn(3L);
        cell.drainOutputs(network, null);
        assertEquals(output.subtract(BigInteger.valueOf(3)), cell.getPendingOutputs().get(nugget));
        when(network.insert(eq(nugget), anyLong(), eq(Actionable.MODULATE), isNull())).thenAnswer(call -> call.getArgument(1));
        cell.drainOutputs(network, null); cell.drainOutputs(network, null);
        assertTrue(cell.getPendingOutputs().isEmpty()); assertTrue(cell.canFitInsideCell());
    }
    @Test void ordinaryListingsNeverOverflowAnExistingCounter() {
        var f = fixture(ingot); f.cell.insert(ingot, Long.MAX_VALUE, Actionable.MODULATE, null);
        var counter = new KeyCounter(); counter.add(ingot, 2);
        f.cell.getAvailableStacks(counter); assertEquals(Long.MAX_VALUE, counter.get(ingot));
    }
    @Test void exactInsertionAndSavedFactorsCanExceedTheLongBoundary() {
        var f = fixture(ingot); var count = BigInteger.TEN.pow(60);
        assertEquals(count, f.cell.insertBigInteger(ingot, count, Actionable.SIMULATE, null));
        assertTrue(f.cell.getAvailableStacks().isEmpty());
        assertEquals(count, f.cell.insertBigInteger(ingot, count, Actionable.MODULATE, null));
        assertEquals(count, exact(f.restore(f.stack.copy())).get(ingot));
    }
    @Test void legacyEntriesWaitingForRecipeInitializationRecoverOnSnapshotChange() {
        var f = fixture(ingot); var entry = BulkItemKeys.write(ingot); entry.putLong("units", 90);
        var entries = new ListTag(); entries.add(entry); var data = new CompoundTag(); data.put("entries", entries);
        var custom = new CompoundTag(); custom.put("neoecoae_mega_long_bulk", data);
        NEItemData.update(f.stack, tag -> tag.merge(custom));
        ECOCompressionService.install(List.of());
        var restored = f.restore(f.stack); assertEquals(1, restored.getUnresolvedEntryCount());
        ECOCompressionService.install(List.of(iron(9)));
        assertEquals(Map.of(ingot, BigInteger.TEN), exact(restored)); assertEquals(0, restored.getUnresolvedEntryCount());
        assertEquals(10, f.restore(f.stack.copy()).extract(ingot, 10, Actionable.MODULATE, null));
    }
    @Test void restoringTheOriginalRecipeReenablesHistoricalStockWithoutChangingItsBalance() {
        var f = fixture(ingot); f.cell.insert(ingot, 9, Actionable.MODULATE, null);
        var before = f.cell.getStoredEntries();
        ECOCompressionService.install(List.of(iron(4))); assertEquals(1, f.cell.getRecoveringEntryCount());
        ECOCompressionService.install(List.of(iron(9))); assertEquals(0, f.cell.getRecoveringEntryCount());
        assertEquals(before, f.cell.getStoredEntries()); assertEquals(1, f.cell.extract(block, 1, Actionable.MODULATE, null));
    }
    @Test void cutoffDefinesDirectionalVirtualPatternsWithoutDuplicatingInventory() {
        var f = fixture(ingot);
        assertEquals(List.of(new ECOBulkConversionPattern(ingot, 1, nugget, 9),
            new ECOBulkConversionPattern(ingot, 9, block, 1)), f.cell.getConversionPatterns());
        f.cell.cycleCompressionCutoff(ingot, 1);
        assertEquals(List.of(new ECOBulkConversionPattern(ingot, 1, nugget, 9),
            new ECOBulkConversionPattern(block, 1, ingot, 9)), f.cell.getConversionPatterns());
    }

    @Test void markerRemainsLockedUntilEveryRemainderOfItsChainIsExtracted() {
        var f = fixture(block, AEItemKey.of(Items.PAPER));
        assertTrue(f.cell.canRemoveMarker(block));
        f.cell.insert(nugget, 82, Actionable.MODULATE, null);
        assertFalse(f.cell.canRemoveMarker(block));
        assertFalse(f.cell.canRemoveMarker(ingot));
        assertTrue(f.cell.canRemoveMarker(AEItemKey.of(Items.PAPER)));
        assertEquals(1, f.cell.extract(block, 1, Actionable.MODULATE, null));
        assertEquals(0, f.cell.getAvailableStacks().get(block));
        assertFalse(f.cell.canRemoveMarker(block));
        assertEquals(1, f.cell.extract(nugget, 1, Actionable.MODULATE, null));
        assertTrue(f.cell.canRemoveMarker(block));
    }

    @Test void clearAllAndReplacementRetainOccupiedMarkersButAllowTheirOtherForms() {
        var paper = AEItemKey.of(Items.PAPER);
        var f = fixture(ingot, paper);
        f.cell.insert(nugget, 1, Actionable.MODULATE, null);
        var previous = ECOBulkStorageCellItem.configuredStacks(f.stack);
        var proposed = markerConfig();
        for (int slot = 0; slot < proposed.size(); slot++) proposed.setStack(slot, previous.get(slot));
        proposed.clear();
        f.cell.protectOccupiedMarkers(previous, proposed);
        assertEquals(ingot, proposed.getKey(0));
        assertNull(proposed.getKey(1));
        proposed.setStack(0, new GenericStack(paper, 0));
        f.cell.protectOccupiedMarkers(previous, proposed);
        assertEquals(ingot, proposed.getKey(0));
        proposed.setStack(0, new GenericStack(block, 0));
        f.cell.protectOccupiedMarkers(previous, proposed);
        assertEquals(block, proposed.getKey(0));
        f.cell.extract(nugget, 1, Actionable.MODULATE, null);
        proposed.clear();
        f.cell.protectOccupiedMarkers(previous, proposed);
        assertTrue(proposed.isEmpty());
    }

    @Test void queuedOutputsAndHistoricalDefinitionsAlsoLockTheMarker() {
        var f = fixture(ingot);
        f.cell.enqueueOutput(nugget, BigInteger.ONE);
        assertFalse(f.cell.canRemoveMarker(block));
        var restored = f.restore(f.stack.copy());
        assertFalse(restored.canRemoveMarker(ingot));
        var history = fixture(ingot);
        history.cell.insert(ingot, 10, Actionable.MODULATE, null);
        ECOCompressionService.install(List.of());
        assertFalse(history.cell.canRemoveMarker(ingot));
        assertEquals(90, history.cell.extract(nugget, 90, Actionable.MODULATE, null));
        assertTrue(history.cell.canRemoveMarker(ingot));
    }

    @Test void bulkItemConfigRestoresRejectedEditsAndClearAllBeforePublishing() throws Exception {
        var f = fixture(ingot, AEItemKey.of(Items.PAPER));
        f.cell.insert(nugget, 1, Actionable.MODULATE, null);
        try (var factory = mockStatic(cn.dancingsnow.neoecoae.items.ECOStorageCellItem.class)) {
            factory.when(() -> cn.dancingsnow.neoecoae.items.ECOStorageCellItem.getCellInventory(f.stack))
                .thenReturn(f.cell);
            var create = ECOBulkStorageCellItem.class.getDeclaredMethod("createPreservingConfigInventory", ItemStack.class, int.class);
            create.setAccessible(true);
            var config = (appeng.util.ConfigInventory) create.invoke(null, f.stack, 25);
            config.setStack(0, null);
            assertEquals(ingot, config.getKey(0));
            assertEquals(ingot, ECOBulkStorageCellItem.configuredStacks(f.stack).get(0).what());
            config.clear();
            assertEquals(ingot, config.getKey(0));
            assertNull(config.getKey(1));
            config.setStack(0, new GenericStack(AEItemKey.of(Items.PAPER), 0));
            assertEquals(ingot, config.getKey(0));
            config.setStack(0, new GenericStack(block, 0));
            assertEquals(block, config.getKey(0));
            assertEquals(block, ECOBulkStorageCellItem.configuredStacks(f.stack).get(0).what());
            f.cell.extract(nugget, 1, Actionable.MODULATE, null);
            config.clear();
            assertTrue(config.isEmpty());
            assertTrue(ECOBulkStorageCellItem.configuredStacks(f.stack).stream().allMatch(java.util.Objects::isNull));
        }
    }

    @Test void compressionMenuMovesOneStepWithoutWrappingOrChangingBalances() {
        assertEquals(ingot, ECOBulkMarkerActions.shift(nugget, 1));
        assertEquals(block, ECOBulkMarkerActions.shift(ingot, 1));
        assertEquals(ingot, ECOBulkMarkerActions.shift(block, -1));
        assertEquals(nugget, ECOBulkMarkerActions.shift(ingot, -1));
        assertNull(ECOBulkMarkerActions.shift(block, 1));
        assertNull(ECOBulkMarkerActions.shift(nugget, -1));
        assertNull(ECOBulkMarkerActions.shift(ingot, 2));
        assertNull(ECOBulkMarkerActions.shift(AEItemKey.of(Items.PAPER), 1));
        var f = fixture(ingot);
        f.cell.insert(nugget, 82, Actionable.MODULATE, null);
        var balance = f.cell.getStoredEntries();
        f.cell.setCompressionCutoff(block);
        assertEquals(balance, f.cell.getStoredEntries());
        assertEquals(Map.of(block, BigInteger.ONE, nugget, BigInteger.ONE), exact(f.cell));
        assertEquals(exact(f.cell), exact(f.restore(f.stack.copy())));
    }
}
