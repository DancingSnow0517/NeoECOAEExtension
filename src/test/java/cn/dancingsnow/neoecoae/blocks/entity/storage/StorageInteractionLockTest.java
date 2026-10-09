package cn.dancingsnow.neoecoae.blocks.entity.storage;

import cn.dancingsnow.neoecoae.api.storage.ICellHost;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageInteractionLockTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void migrationMarkerBlocksEvenWithoutAnInfiniteHostAndClearingItUnlocks() throws Exception {
        ECODriveBlockEntity drive = mock(ECODriveBlockEntity.class, CALLS_REAL_METHODS);
        ItemStack stack = new ItemStack(Items.STONE);
        var field = ECODriveBlockEntity.class.getDeclaredField("cellStack");
        field.setAccessible(true);
        field.set(drive, stack);
        ICellHost cellHost = drive;
        assertTrue(drive.canExtractCell());
        assertNull(cellHost.getCellExtractionBlockReasonText());

        cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.beginMigration(
            stack, java.util.UUID.randomUUID());
        assertEquals(ECODriveBlockEntity.CellExtractionBlockReason.INFINITE_MIGRATION,
            drive.getCellExtractionBlockReason());
        assertFalse(drive.canExtractCell());
        assertEquals(Component.translatable("tooltip.neoecoae.storage.infinite_migration_locked"),
            cellHost.getCellExtractionBlockReasonText());
        cellHost.setCellStack(null);
        assertSame(stack, drive.getCellStack());
        stack.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        assertTrue(drive.canExtractCell());
        assertNull(cellHost.getCellExtractionBlockReasonText());
    }

    @Test
    void infiniteMemberKeepsItsOwnReasonAndCannotBeClearedThroughSetter() {
        ECODriveBlockEntity drive = mock(ECODriveBlockEntity.class, CALLS_REAL_METHODS);
        doReturn(true).when(drive).isLockedByInfiniteMode();
        assertEquals(ECODriveBlockEntity.CellExtractionBlockReason.INFINITE_MEMBER,
            drive.getCellExtractionBlockReason());
        assertFalse(drive.canExtractCell());
        ICellHost cellHost = drive;
        assertEquals(Component.translatable("tooltip.neoecoae.storage.infinite_member_locked"),
            cellHost.getCellExtractionBlockReasonText());
        drive.setCellStack(null);
        verify(drive, never()).setChanged();
    }

    @Test
    void completedInfiniteModeAllowsMemberExtraction() {
        ECODriveBlockEntity drive = mock(ECODriveBlockEntity.class, CALLS_REAL_METHODS);
        ECOStorageSystemBlockEntity host = mock(ECOStorageSystemBlockEntity.class);
        doReturn(true).when(drive).isLockedByInfiniteMode();
        doReturn(host).when(drive).getStorageController();
        when(host.isFormedInfiniteMode()).thenReturn(true);
        assertTrue(drive.canExtractCell());
        assertNull(((ICellHost) drive).getCellExtractionBlockReasonText());
        when(host.isFormedInfiniteMode()).thenReturn(false);
        assertFalse(drive.canExtractCell());
    }

}
