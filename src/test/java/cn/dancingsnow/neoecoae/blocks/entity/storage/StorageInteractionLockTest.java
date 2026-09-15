package cn.dancingsnow.neoecoae.blocks.entity.storage;

import cn.dancingsnow.neoecoae.util.CellHostItemHandler;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.nbt.CompoundTag;
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
        assertTrue(drive.canExtractCell());

        cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.beginMigration(
            stack, java.util.UUID.randomUUID());
        assertEquals(ECODriveBlockEntity.CellExtractionBlockReason.INFINITE_MIGRATION,
            drive.getCellExtractionBlockReason());
        assertFalse(drive.canExtractCell());
        drive.setCellStack(null);
        assertSame(stack, drive.getCellStack());
        stack.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        assertTrue(drive.canExtractCell());
    }

    @Test
    void finiteTransferReportsItsOwnReasonAndRejectsAutomation() {
        ECODriveBlockEntity drive = mock(ECODriveBlockEntity.class, CALLS_REAL_METHODS);
        doReturn(true).when(drive).isLockedByFiniteTransferDomain();
        doReturn(false).when(drive).isLockedByInfiniteMode();
        doReturn(new ItemStack(Items.STONE)).when(drive).getCellStack();

        assertEquals(ECODriveBlockEntity.CellExtractionBlockReason.FINITE_TRANSFER,
            drive.getCellExtractionBlockReason());
        assertFalse(drive.canExtractCell());
        CellHostItemHandler handler = new CellHostItemHandler(drive);
        assertTrue(handler.extractItem(0, 1, true).isEmpty());
        assertTrue(handler.extractItem(0, 1, false).isEmpty());
        verify(drive, never()).setCellStack(any());
    }

    @Test
    void emptyDriveInTransferDomainDoesNotConsumeInsertedItems() {
        ECODriveBlockEntity drive = mock(ECODriveBlockEntity.class, CALLS_REAL_METHODS);
        doReturn(true).when(drive).isLockedByFiniteTransferDomain();
        ItemStack input = new ItemStack(Items.STONE, 3);
        CellHostItemHandler handler = new CellHostItemHandler(drive);

        assertSame(input, handler.insertItem(0, input, true));
        assertSame(input, handler.insertItem(0, input, false));
        assertEquals(3, input.getCount());
        verify(drive, never()).setCellStack(any());
    }

    @Test
    void infiniteMemberKeepsItsOwnReasonAndCannotBeClearedThroughSetter() {
        ECODriveBlockEntity drive = mock(ECODriveBlockEntity.class, CALLS_REAL_METHODS);
        doReturn(true).when(drive).isLockedByInfiniteMode();
        assertEquals(ECODriveBlockEntity.CellExtractionBlockReason.INFINITE_MEMBER,
            drive.getCellExtractionBlockReason());
        assertFalse(drive.canExtractCell());
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
        when(host.isFormedInfiniteMode()).thenReturn(false);
        assertFalse(drive.canExtractCell());
    }

    @Test
    void pendingRecoveryLocksBeforeDomainConstructionAndCannotClaimSuccessfulHandoff() {
        ECOStorageInterfaceTransfer transfer = new ECOStorageInterfaceTransfer(null);
        assertFalse(transfer.isFiniteTransferDomainLocked());
        assertTrue(transfer.materializeFiniteTransferDomain());

        CompoundTag saved = new CompoundTag();
        saved.put("finiteTransferDomain", new CompoundTag());
        transfer.loadDomain(saved);
        assertTrue(transfer.isFiniteTransferDomainLocked());
        assertTrue(transfer.blocksInfiniteMigration());
        assertFalse(transfer.materializeFiniteTransferDomain());
    }
}
