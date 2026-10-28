package cn.dancingsnow.neoecoae.storage.bulk;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.util.ConfigInventory;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.items.ECOBulkStorageCellItem;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOBulkMarkingServiceTest {
    @BeforeAll
    static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void selectedMatrixCannotRemarkOrMigrateAnotherMatrixsChain() {
        var ingot = AEItemKey.of(Items.IRON_INGOT);
        var block = AEItemKey.of(Items.IRON_BLOCK);
        ECOCompressionService.install(List.of(new BulkUnits(List.of(ingot, block),
            List.of(BigInteger.ONE, BigInteger.valueOf(9)))));
        var host = mock(ECOStorageSystemBlockEntity.class, RETURNS_DEEP_STUBS);
        var level = mock(ServerLevel.class, RETURNS_DEEP_STUBS);
        when(host.getLevel()).thenReturn(level);
        when(level.getServer().isSameThread()).thenReturn(true);
        when(host.getTier()).thenReturn(ECOTier.L9);
        when(host.getMainNode().getGrid()).thenReturn(null);
        var first = drive(BlockPos.ZERO, ingot);
        var second = drive(new BlockPos(1, 0, 0), null);
        when(host.getStorageDrivesForIntegration()).thenReturn(List.of(first, second));
        var available = new KeyCounter();
        available.add(block, 100);
        when(host.collectLocalStorageStacksForIntegration()).thenReturn(available);

        assertTrue(ECOBulkMarkingService.previewMarkAndMigrateCell(host, second.getBlockPos(), 0).isEmpty());
        var result = ECOBulkMarkingService.markAndMigrateCell(host, second.getBlockPos(), 0);
        assertEquals(0, result.added());
        assertEquals(1, result.alreadyMarked());
        assertEquals(0, result.transferred());
        var config = ((ECOBulkStorageCellItem) second.getCellStack().getItem()).getConfigInventory(second.getCellStack());
        assertNull(config.getKey(0));
        verify(second, never()).onCellConfigurationChanged();
        verify(first.getCellInventory(), never()).extract(any(), anyLong(), any(), any());
    }

    private static ECODriveBlockEntity drive(BlockPos position, AEItemKey marker) {
        var item = mock(ECOBulkStorageCellItem.class);
        var stack = mock(ItemStack.class);
        when(stack.getItem()).thenReturn(item);
        ConfigInventory config = ConfigInventory.configTypes(key -> AEKeyType.items().contains(key), 25, () -> {});
        if (marker != null) config.setStack(0, new GenericStack(marker, 0));
        when(item.getConfigInventory(stack)).thenReturn(config);
        var cell = mock(ECOBulkStorageCell.class);
        when(cell.getTier()).thenReturn(ECOTier.L9);
        when(cell.getEffectiveConfiguredFilters()).thenReturn(marker == null ? List.of() : List.of(marker));
        var drive = mock(ECODriveBlockEntity.class);
        when(drive.getBlockPos()).thenReturn(position);
        when(drive.getCellStack()).thenReturn(stack);
        when(drive.getCellInventory()).thenReturn(cell);
        return drive;
    }
}
