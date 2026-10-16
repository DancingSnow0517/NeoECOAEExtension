package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.util.ConfigInventory;
import cn.dancingsnow.neoecoae.integration.megacells.backend.ECOMegaLongBulkStorageCell;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOStorageCellAccountingTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    private static ItemStack stack(ECOStorageCellItem item) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getItem()).thenReturn(item);
        when(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)).thenReturn(CustomData.EMPTY);
        when(item.getKeyType()).thenReturn(AEKeyType.fluids());
        when(item.getTotalTypes()).thenReturn(315);
        when(item.getBytes()).thenReturn(1000L);
        when(item.getBytesPerType()).thenReturn(8);
        return stack;
    }

    @Test void bulkConstructionSkipsOrdinaryContentsConfigAndUpgradeInitialization() {
        var item = mock(ECOStorageCellItem.class);
        var stack = stack(item);
        var bulk = new ECOMegaLongBulkStorageCell(stack, null);
        assertFalse(bulk.isInfiniteStorageEligible());
        verify(stack, never()).getOrDefault(eq(AEComponents.STORAGE_CELL_INV), anyList());
        verify(item, never()).getConfigInventory(any());
        verify(item, never()).getUpgrades(any());
    }

    @Test void finiteInsertExtractAndMigrationUseIdenticalCapacityAccounting() {
        try (var types = mockStatic(AEKeyTypes.class)) {
            types.when(AEKeyTypes::getAll).thenReturn(Set.of(AEKeyType.fluids()));
            var item = mock(ECOStorageCellItem.class);
            var stack = stack(item);
            when(stack.getOrDefault(eq(AEComponents.STORAGE_CELL_INV), anyList())).thenReturn(List.of());
            var configuration = ConfigInventory.configTypes(27).build();
            when(item.getConfigInventory(stack)).thenReturn(configuration);
            when(item.getUpgrades(stack)).thenReturn(mock(IUpgradeInventory.class));
            var cell = new ECOStorageCell(stack, null);
            var water = AEFluidKey.of(Fluids.WATER);
            long perByte = AEKeyType.fluids().getAmountPerByte();
            assertEquals(0, cell.getUsedBytes());
            assertEquals(perByte + 1, cell.insert(water, perByte + 1, Actionable.MODULATE, null));
            assertEquals(10, cell.getUsedBytes());
            assertEquals(1000L * perByte - 8L * perByte - (perByte + 1), cell.getRemainingItemCount());
            assertEquals(cell.insert(water, Long.MAX_VALUE, Actionable.SIMULATE, null),
                cell.simulateInsertForMigration(water, Long.MAX_VALUE, perByte + 1, 1, perByte + 1));
            assertEquals(perByte, cell.extract(water, perByte, Actionable.MODULATE, null));
            assertEquals(9, cell.getUsedBytes());
            assertEquals(1, cell.extract(water, Long.MAX_VALUE, Actionable.MODULATE, null));
            assertEquals(0, cell.getUsedBytes());
            assertEquals(0, cell.getStoredItemTypes());
        }
    }
}
