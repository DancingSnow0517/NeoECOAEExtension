package cn.dancingsnow.neoecoae.blocks.entity.storage;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageCellUiInsertionTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void aTakenDiskCanBePutBackInTheSameDriveWithAllItsComponents() {
        Fixture f = new Fixture();
        ItemStack disk = f.carried.get();
        f.cell.set(disk);
        f.carried.set(ItemStack.EMPTY);
        assertTrue(StorageCellUiExtraction.extract(f.host, f.player, f.position, f.drive.getCellInteractionId(), false));
        assertSame(disk, f.carried.get());
        assertNull(f.cell.get());
        ItemStack expected = disk.copy();
        assertTrue(f.insert());
        assertTrue(ItemStack.matches(expected, f.cell.get()));
        assertEquals("stored-disk-123", f.cell.get().get(DataComponents.CUSTOM_DATA).copyTag().getString("disk-id"));
        assertEquals(Component.literal("Disk with contents"), f.cell.get().get(DataComponents.CUSTOM_NAME));
        assertTrue(f.carried.get().isEmpty());
        verify(f.menu, times(2)).broadcastChanges();
    }

    @Test
    void insertsExactlyOneDiskAndRejectsARepeatedStaleRequest() {
        Fixture f = new Fixture();
        f.carried.get().setCount(3);
        ItemStack request = f.carried.get().copy();
        assertTrue(StorageCellUiInsertion.insert(f.host, f.player, f.position, request));
        assertEquals(1, f.cell.get().getCount());
        assertEquals(2, f.carried.get().getCount());
        assertFalse(StorageCellUiInsertion.insert(f.host, f.player, f.position, request));
        verify(f.drive, times(1)).setCellStack(any());
    }

    @Test
    void invalidCellLockedDriveAndRejectedSetterDoNotConsumeTheCursorDisk() {
        Fixture f = new Fixture();
        ItemStack original = f.carried.get().copy();
        when(f.drive.isItemValid(any())).thenReturn(false);
        assertFalse(f.insert());
        when(f.drive.isItemValid(any())).thenReturn(true);
        when(f.drive.canExtractCell()).thenReturn(false);
        assertFalse(f.insert());
        when(f.drive.canExtractCell()).thenReturn(true);
        doNothing().when(f.drive).setCellStack(any());
        assertFalse(f.insert());
        assertTrue(ItemStack.matches(original, f.carried.get()));
        assertNull(f.cell.get());
    }

    @Test
    void occupiedOrRemovedDriveWrongPositionAndBrokenStructureCannotAcceptADisk() {
        Fixture f = new Fixture();
        ItemStack original = f.carried.get().copy();
        ItemStack occupied = new ItemStack(Items.EMERALD);
        f.cell.set(occupied);
        assertFalse(f.insert());
        assertSame(occupied, f.cell.get());
        f.cell.set(null);
        when(f.drive.isRemoved()).thenReturn(true);
        assertFalse(f.insert());
        when(f.drive.isRemoved()).thenReturn(false);
        assertFalse(StorageCellUiInsertion.insert(f.host, f.player, f.position.above(), original));
        when(f.cluster.getDrives()).thenReturn(List.of());
        assertFalse(f.insert());
        when(f.cluster.getDrives()).thenReturn(List.of(f.drive));
        when(f.host.isFormed()).thenReturn(false);
        assertFalse(f.insert());
        assertTrue(ItemStack.matches(original, f.carried.get()));
        verify(f.drive, never()).setCellStack(any());
    }

    @Test
    void changedCursorAndEmptyRequestsCannotInventADisk() {
        Fixture f = new Fixture();
        ItemStack oldRequest = f.carried.get().copy();
        f.carried.get().set(DataComponents.CUSTOM_NAME, Component.literal("Different disk"));
        assertFalse(StorageCellUiInsertion.insert(f.host, f.player, f.position, oldRequest));
        f.carried.set(ItemStack.EMPTY);
        assertFalse(StorageCellUiInsertion.insert(f.host, f.player, f.position, oldRequest));
        assertFalse(f.insert());
        assertNull(f.cell.get());
        verify(f.drive, never()).setCellStack(any());
    }

    private static class Fixture {
        final ECOStorageSystemBlockEntity host = mock(ECOStorageSystemBlockEntity.class);
        final NEStorageCluster cluster = mock(NEStorageCluster.class);
        final ECODriveBlockEntity drive = mock(ECODriveBlockEntity.class);
        final Player player = mock(Player.class);
        final AbstractContainerMenu menu = mock(AbstractContainerMenu.class);
        final BlockPos position = new BlockPos(10, 70, 3);
        final AtomicReference<ItemStack> cell = new AtomicReference<>();
        final AtomicReference<ItemStack> carried = new AtomicReference<>(new ItemStack(Items.DIAMOND));

        Fixture() {
            CompoundTag data = new CompoundTag();
            data.putString("disk-id", "stored-disk-123");
            carried.get().set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            carried.get().set(DataComponents.CUSTOM_NAME, Component.literal("Disk with contents"));
            when(host.isFormed()).thenReturn(true);
            when(host.getCluster()).thenReturn(cluster);
            when(cluster.getDrives()).thenReturn(List.of(drive));
            when(drive.getBlockPos()).thenReturn(position);
            when(drive.getCellStack()).thenAnswer(call -> cell.get());
            when(drive.getCellInteractionId()).thenReturn(UUID.randomUUID());
            when(drive.canExtractCell()).thenReturn(true);
            when(drive.isItemValid(any())).thenReturn(true);
            doAnswer(call -> { cell.set(call.getArgument(0)); return null; }).when(drive).setCellStack(any());
            when(player.level()).thenReturn(mock(Level.class));
            player.containerMenu = menu;
            when(menu.getCarried()).thenAnswer(call -> carried.get());
            doAnswer(call -> { carried.set(call.getArgument(0)); return null; }).when(menu).setCarried(any());
        }

        boolean insert() {
            return StorageCellUiInsertion.insert(host, player, position, carried.get().copy());
        }
    }
}
