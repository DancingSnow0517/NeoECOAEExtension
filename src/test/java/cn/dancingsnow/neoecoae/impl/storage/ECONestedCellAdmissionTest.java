package cn.dancingsnow.neoecoae.impl.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.StorageCell;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorage;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ECONestedCellAdmissionTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void ecoCellThatCannotFitIsRejectedByCellCheckWithoutConsultingAe2() {
        AEItemKey key = AEItemKey.of(Items.DIRT);
        var eco = mock(IECOStorageCell.class);
        when(eco.canFitInsideCell()).thenReturn(false);
        try (var ecoCells = mockStatic(ECOStorageCells.class); var ae2 = mockStatic(StorageCells.class)) {
            ecoCells.when(() -> ECOStorageCells.getCellInventory(key.getReadOnlyStack(), null)).thenReturn(eco);
            assertFalse(ECOStorageCell.canStoreKeyInsideStorageCell(key));
            ae2.verify(() -> StorageCells.getCellInventory(any(), any()), never());
        }
    }

    @Test void ecoCellThatCanFitIsAcceptedByCellCheck() {
        AEItemKey key = AEItemKey.of(Items.DIRT);
        var eco = mock(IECOStorageCell.class);
        when(eco.canFitInsideCell()).thenReturn(true);
        try (var ecoCells = mockStatic(ECOStorageCells.class)) {
            ecoCells.when(() -> ECOStorageCells.getCellInventory(key.getReadOnlyStack(), null)).thenReturn(eco);
            assertTrue(ECOStorageCell.canStoreKeyInsideStorageCell(key));
        }
    }

    @Test void ae2CellIsStillCheckedWhenNoEcoInventoryExists() {
        AEItemKey key = AEItemKey.of(Items.DIRT);
        var ae2Cell = mock(StorageCell.class);
        when(ae2Cell.canFitInsideCell()).thenReturn(false);
        try (var ecoCells = mockStatic(ECOStorageCells.class); var ae2 = mockStatic(StorageCells.class)) {
            ecoCells.when(() -> ECOStorageCells.getCellInventory(key.getReadOnlyStack(), null)).thenReturn(null);
            ae2.when(() -> StorageCells.getCellInventory(key.getReadOnlyStack(), null)).thenReturn(ae2Cell);
            assertFalse(ECOStorageCell.canStoreKeyInsideStorageCell(key));
        }
    }

    @Test void ecoCellThatCannotFitIsRejectedByInfiniteInsertWithoutEngineWrite() {
        AEItemKey key = AEItemKey.of(Items.DIRT);
        var engine = mock(ECOInfiniteStorageEngine.class);
        var storage = new ECOInfiniteStorage(engine, Component.empty(), () -> true);
        var eco = mock(IECOStorageCell.class);
        when(eco.canFitInsideCell()).thenReturn(false);
        try (var ecoCells = mockStatic(ECOStorageCells.class)) {
            ecoCells.when(() -> ECOStorageCells.getCellInventory(key.getReadOnlyStack(), null)).thenReturn(eco);
            assertEquals(0, storage.insert(key, 1, Actionable.MODULATE, IActionSource.empty()));
        }
        verify(engine, never()).insert(any(), anyLong(), any());
    }

    @Test void ecoCellThatCanFitIsAcceptedByInfiniteInsert() {
        AEItemKey key = AEItemKey.of(Items.DIRT);
        var engine = mock(ECOInfiniteStorageEngine.class);
        when(engine.insert(key, 1L, Actionable.MODULATE)).thenReturn(1L);
        var storage = new ECOInfiniteStorage(engine, Component.empty(), () -> true);
        var eco = mock(IECOStorageCell.class);
        when(eco.canFitInsideCell()).thenReturn(true);
        try (var ecoCells = mockStatic(ECOStorageCells.class)) {
            ecoCells.when(() -> ECOStorageCells.getCellInventory(key.getReadOnlyStack(), null)).thenReturn(eco);
            assertEquals(1, storage.insert(key, 1, Actionable.MODULATE, IActionSource.empty()));
        }
        verify(engine).insert(key, 1L, Actionable.MODULATE);
    }

    @Test void nonStableEcoInventoryIsProbedAgainOnNextInsert() {
        AEItemKey key = AEItemKey.of(Items.DIRT);
        var engine = mock(ECOInfiniteStorageEngine.class);
        when(engine.insert(key, 1L, Actionable.MODULATE)).thenReturn(1L);
        var storage = new ECOInfiniteStorage(engine, Component.empty(), () -> true);
        var eco = mock(IECOStorageCell.class);
        when(eco.canFitInsideCell()).thenReturn(true, false);
        try (var ecoCells = mockStatic(ECOStorageCells.class)) {
            ecoCells.when(() -> ECOStorageCells.getCellInventory(key.getReadOnlyStack(), null)).thenReturn(eco);
            assertEquals(1, storage.insert(key, 1, Actionable.MODULATE, IActionSource.empty()));
            assertEquals(0, storage.insert(key, 1, Actionable.MODULATE, IActionSource.empty()));
            ecoCells.verify(() -> ECOStorageCells.getCellInventory(key.getReadOnlyStack(), null), times(2));
        }
    }

    @Test void ecoClaimedKeyWithoutInventoryIsNotCached() {
        AEItemKey key = AEItemKey.of(Items.DIRT);
        var engine = mock(ECOInfiniteStorageEngine.class);
        when(engine.insert(key, 1L, Actionable.MODULATE)).thenReturn(1L);
        var storage = new ECOInfiniteStorage(engine, Component.empty(), () -> true);
        try (var ecoCells = mockStatic(ECOStorageCells.class); var ae2 = mockStatic(StorageCells.class)) {
            ecoCells.when(() -> ECOStorageCells.getCellInventory(key.getReadOnlyStack(), null)).thenReturn(null);
            ecoCells.when(() -> ECOStorageCells.isCellHandled(key.getReadOnlyStack())).thenReturn(true);
            assertEquals(1, storage.insert(key, 1, Actionable.MODULATE, IActionSource.empty()));
            assertEquals(1, storage.insert(key, 1, Actionable.MODULATE, IActionSource.empty()));
            ecoCells.verify(() -> ECOStorageCells.getCellInventory(key.getReadOnlyStack(), null), times(2));
        }
    }
}
