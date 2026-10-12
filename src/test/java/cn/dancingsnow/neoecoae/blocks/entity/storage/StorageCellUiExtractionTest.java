package cn.dancingsnow.neoecoae.blocks.entity.storage;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageCellUiExtractionTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void clickReturnsTheExactDiskAndLatestPersistedComponentsOnlyOnce() {
        Fixture f = new Fixture();
        ItemStack expected = f.cell.get().copy();
        // Unmount can persist new components; the returned item must retain them.
        doAnswer(call -> {
            f.cell.get().set(DataComponents.CUSTOM_NAME, Component.literal("Persisted disk"));
            f.cell.set(null);
            return null;
        }).when(f.drive).setCellStack(null);
        assertTrue(f.take(expected, false));
        assertEquals(Component.literal("Persisted disk"), f.carried.get().get(DataComponents.CUSTOM_NAME));
        assertTrue(f.carried.get().get(DataComponents.CUSTOM_DATA).copyTag().contains("neoecoae_infinite_member"));
        assertNull(f.cell.get());
        assertFalse(f.take(expected, false));
        verify(f.drive, times(1)).setCellStack(null);
        verify(f.menu, times(1)).broadcastChanges();
    }

    @Test
    void shiftClickMovesOneDiskIntoAnEmptyInventorySlot() {
        Fixture f = new Fixture();
        ItemStack original = f.cell.get();
        assertTrue(f.take(original.copy(), true));
        assertSame(original, f.inventory.getItem(0));
        assertNull(f.cell.get());
        assertTrue(f.carried.get().isEmpty());
    }

    @Test
    void fullInventoryAndOccupiedCursorLeaveTheDiskMounted() {
        Fixture f = new Fixture();
        for (int i = 0; i < f.inventory.items.size(); i++) f.inventory.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        assertFalse(f.take(f.cell.get().copy(), true));
        f.carried.set(new ItemStack(Items.STONE));
        assertFalse(f.take(f.cell.get().copy(), false));
        verify(f.drive, never()).setCellStack(null);
        assertNotNull(f.cell.get());
    }

    @Test
    void migrationAndMemberLocksApplyToBothClickModes() {
        Fixture f = new Fixture();
        for (var reason : List.of(ECODriveBlockEntity.CellExtractionBlockReason.INFINITE_MIGRATION,
            ECODriveBlockEntity.CellExtractionBlockReason.INFINITE_MEMBER)) {
            when(f.drive.getCellExtractionBlockReasonText()).thenReturn(Component.translatable(reason.translationKey()));
            when(f.drive.canExtractCell()).thenReturn(false);
            assertFalse(f.take(f.cell.get().copy(), false));
            assertFalse(f.take(f.cell.get().copy(), true));
        }
        verify(f.drive, never()).setCellStack(null);
    }

    @Test
    void staleDiskRemovedDriveWrongPositionAndBrokenStructureCannotExtract() {
        Fixture f = new Fixture();
        ItemStack stale = f.cell.get().copy();
        f.cell.get().set(DataComponents.CUSTOM_NAME, Component.literal("Different disk"));
        assertFalse(f.take(stale, false));
        assertFalse(StorageCellUiExtraction.extract(f.host, f.player, f.position.above(), f.cell.get().copy(), false));
        when(f.drive.isRemoved()).thenReturn(true);
        assertFalse(f.take(f.cell.get().copy(), false));
        when(f.drive.isRemoved()).thenReturn(false);
        when(f.cluster.getDrives()).thenReturn(List.of());
        assertFalse(f.take(f.cell.get().copy(), false));
        when(f.cluster.getDrives()).thenReturn(List.of(f.drive));
        when(f.host.isFormed()).thenReturn(false);
        assertFalse(f.take(f.cell.get().copy(), false));
        verify(f.drive, never()).setCellStack(null);
    }

    @Test
    void setterRefusingExtractionDoesNotCreateAnotherDisk() {
        Fixture f = new Fixture();
        doNothing().when(f.drive).setCellStack(null);
        assertFalse(f.take(f.cell.get().copy(), false));
        assertFalse(f.take(f.cell.get().copy(), true));
        assertTrue(f.inventory.getItem(0).isEmpty());
        assertTrue(f.carried.get().isEmpty());
    }

    private static class Fixture {
        final ECOStorageSystemBlockEntity host = mock(ECOStorageSystemBlockEntity.class);
        final NEStorageCluster cluster = mock(NEStorageCluster.class);
        final ECODriveBlockEntity drive = mock(ECODriveBlockEntity.class);
        final Player player = mock(Player.class);
        final AbstractContainerMenu menu = mock(AbstractContainerMenu.class);
        final Inventory inventory = new Inventory(player);
        final BlockPos position = new BlockPos(10, 70, 3);
        final AtomicReference<ItemStack> cell = new AtomicReference<>(new ItemStack(Items.DIAMOND));
        final AtomicReference<ItemStack> carried = new AtomicReference<>(ItemStack.EMPTY);

        Fixture() {
            ECOInfiniteStorageMember.markMember(cell.get(), UUID.randomUUID());
            when(host.isFormed()).thenReturn(true);
            when(host.getCluster()).thenReturn(cluster);
            when(cluster.getDrives()).thenReturn(List.of(drive));
            when(drive.getBlockPos()).thenReturn(position);
            when(drive.getCellStack()).thenAnswer(call -> cell.get());
            when(drive.canExtractCell()).thenReturn(true);
            doAnswer(call -> { cell.set(null); return null; }).when(drive).setCellStack(null);
            when(player.level()).thenReturn(mock(Level.class));
            when(player.getInventory()).thenReturn(inventory);
            player.containerMenu = menu;
            when(menu.getCarried()).thenAnswer(call -> carried.get());
            doAnswer(call -> { carried.set(call.getArgument(0)); return null; }).when(menu).setCarried(any());
        }

        boolean take(ItemStack expected, boolean shift) {
            return StorageCellUiExtraction.extract(host, player, position, expected, shift);
        }
    }
}
