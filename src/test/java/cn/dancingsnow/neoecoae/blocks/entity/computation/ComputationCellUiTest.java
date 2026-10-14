package cn.dancingsnow.neoecoae.blocks.entity.computation;

import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
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

class ComputationCellUiTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void insertInstallsExactlyOneCellIntoTheUpperDriveAndConsumesOneFromTheCursor() {
        Fixture f = new Fixture();
        f.carried.get().setCount(3);
        ItemStack request = f.carried.get().copy();
        assertTrue(ComputationCellUi.insert(f.host, f.player, f.upperPosition, request));
        assertEquals(1, f.upperCell.get().getCount());
        assertEquals(2, f.carried.get().getCount());
        assertNull(f.lowerCell.get());
        assertFalse(ComputationCellUi.insert(f.host, f.player, f.upperPosition, request));
        verify(f.upper, times(1)).setCellStack(any());
        verify(f.lower, never()).setCellStack(any());
        verify(f.menu, times(1)).broadcastChanges();
    }

    @Test
    void insertInstallsExactlyOneCellIntoTheLowerDriveAtItsOwnPosition() {
        Fixture f = new Fixture();
        f.carried.get().setCount(2);
        assertTrue(f.insert(f.lowerPosition));
        assertEquals(1, f.lowerCell.get().getCount());
        assertEquals(1, f.carried.get().getCount());
        assertNull(f.upperCell.get());
        verify(f.lower, times(1)).setCellStack(any());
        verify(f.upper, never()).setCellStack(any());
    }

    @Test
    void staleOrChangedCursorAndEmptyRequestCannotInstallACell() {
        Fixture f = new Fixture();
        ItemStack oldRequest = f.carried.get().copy();
        f.carried.get().set(DataComponents.CUSTOM_NAME, Component.literal("Different cell"));
        assertFalse(ComputationCellUi.insert(f.host, f.player, f.upperPosition, oldRequest));
        f.carried.set(ItemStack.EMPTY);
        assertFalse(ComputationCellUi.insert(f.host, f.player, f.upperPosition, oldRequest));
        assertFalse(ComputationCellUi.insert(f.host, f.player, f.upperPosition, ItemStack.EMPTY));
        assertNull(f.upperCell.get());
        verify(f.upper, never()).setCellStack(any());
    }

    @Test
    void invalidItemAndRejectedInstallDoNotConsumeTheCursor() {
        Fixture f = new Fixture();
        ItemStack original = f.carried.get().copy();
        when(f.upper.isItemValid(any())).thenReturn(false);
        assertFalse(f.insert(f.upperPosition));
        when(f.upper.isItemValid(any())).thenReturn(true);
        doNothing().when(f.upper).setCellStack(any());
        assertFalse(f.insert(f.upperPosition));
        assertTrue(ItemStack.matches(original, f.carried.get()));
        assertNull(f.upperCell.get());
        verify(f.menu, never()).broadcastChanges();
    }

    @Test
    void occupiedOrRemovedUpperDriveCannotAcceptACell() {
        Fixture f = new Fixture();
        ItemStack original = f.carried.get().copy();
        ItemStack occupant = new ItemStack(Items.EMERALD);
        f.upperCell.set(occupant);
        assertFalse(f.insert(f.upperPosition));
        assertSame(occupant, f.upperCell.get());
        f.upperCell.set(null);
        when(f.upper.isRemoved()).thenReturn(true);
        assertFalse(f.insert(f.upperPosition));
        assertTrue(ItemStack.matches(original, f.carried.get()));
        verify(f.upper, never()).setCellStack(any());
    }

    @Test
    void wrongPositionUnformedHostOrMissingClusterCannotAcceptACell() {
        Fixture f = new Fixture();
        ItemStack original = f.carried.get().copy();
        assertFalse(ComputationCellUi.insert(f.host, f.player, f.upperPosition.above(), original));
        when(f.host.isFormed()).thenReturn(false);
        assertFalse(f.insert(f.upperPosition));
        when(f.host.isFormed()).thenReturn(true);
        when(f.host.getCluster()).thenReturn(null);
        assertFalse(f.insert(f.upperPosition));
        assertTrue(ItemStack.matches(original, f.carried.get()));
        verify(f.upper, never()).setCellStack(any());
        verify(f.lower, never()).setCellStack(any());
    }

    @Test
    void extractMovesTheCellToTheCursorAndClearsTheDrive() {
        Fixture f = new Fixture();
        f.carried.set(ItemStack.EMPTY);
        ItemStack cell = new ItemStack(Items.DIAMOND);
        cell.set(DataComponents.CUSTOM_NAME, Component.literal("Installed cell"));
        f.upperCell.set(cell);
        assertTrue(f.take(f.upperPosition, cell.copy(), false));
        assertSame(cell, f.carried.get());
        assertNull(f.upperCell.get());
        assertEquals(Component.literal("Installed cell"), f.carried.get().get(DataComponents.CUSTOM_NAME));
        verify(f.upper, times(1)).setCellStack(null);
        verify(f.menu, times(1)).broadcastChanges();
    }

    @Test
    void extractWithInventoryModePlacesTheCellInTheFirstFreeSlot() {
        Fixture f = new Fixture();
        f.carried.set(ItemStack.EMPTY);
        f.inventory.setItem(0, new ItemStack(Items.COBBLESTONE));
        ItemStack cell = new ItemStack(Items.DIAMOND);
        f.upperCell.set(cell);
        assertTrue(f.take(f.upperPosition, cell.copy(), true));
        assertSame(cell, f.inventory.getItem(1));
        assertNull(f.upperCell.get());
        assertTrue(f.carried.get().isEmpty());
        verify(f.menu, times(1)).broadcastChanges();
    }

    @Test
    void extractToCursorIsRefusedWhileTheCursorHoldsAnItem() {
        Fixture f = new Fixture();
        ItemStack cell = new ItemStack(Items.DIAMOND);
        f.upperCell.set(cell);
        f.carried.set(new ItemStack(Items.STONE));
        assertFalse(f.take(f.upperPosition, cell.copy(), false));
        assertSame(cell, f.upperCell.get());
        assertTrue(ItemStack.matches(new ItemStack(Items.STONE), f.carried.get()));
        verify(f.upper, never()).setCellStack(any());
    }

    @Test
    void staleCellWrongPositionRemovedDriveOrUnformedHostCannotBeExtracted() {
        Fixture f = new Fixture();
        f.carried.set(ItemStack.EMPTY);
        ItemStack cell = new ItemStack(Items.DIAMOND);
        f.upperCell.set(cell);
        ItemStack stale = cell.copy();
        cell.set(DataComponents.CUSTOM_NAME, Component.literal("Changed cell"));
        assertFalse(f.take(f.upperPosition, stale, false));
        assertFalse(f.take(f.upperPosition.above(), cell.copy(), false));
        when(f.upper.isRemoved()).thenReturn(true);
        assertFalse(f.take(f.upperPosition, cell.copy(), false));
        when(f.upper.isRemoved()).thenReturn(false);
        when(f.host.isFormed()).thenReturn(false);
        assertFalse(f.take(f.upperPosition, cell.copy(), false));
        assertSame(cell, f.upperCell.get());
        verify(f.upper, never()).setCellStack(any());
    }

    @Test
    void rejectedRemovalDoesNotDuplicateTheCell() {
        Fixture f = new Fixture();
        f.carried.set(ItemStack.EMPTY);
        ItemStack cell = new ItemStack(Items.DIAMOND);
        f.upperCell.set(cell);
        doNothing().when(f.upper).setCellStack(null);
        assertFalse(f.take(f.upperPosition, cell.copy(), false));
        assertFalse(f.take(f.upperPosition, cell.copy(), true));
        assertSame(cell, f.upperCell.get());
        assertTrue(f.carried.get().isEmpty());
        assertTrue(f.inventory.getItem(0).isEmpty());
    }

    private static class Fixture {
        final ECOComputationSystemBlockEntity host = mock(ECOComputationSystemBlockEntity.class);
        final NEComputationCluster cluster = mock(NEComputationCluster.class);
        final ECOComputationDriveBlockEntity upper = mock(ECOComputationDriveBlockEntity.class);
        final ECOComputationDriveBlockEntity lower = mock(ECOComputationDriveBlockEntity.class);
        final Player player = mock(Player.class);
        final AbstractContainerMenu menu = mock(AbstractContainerMenu.class);
        final Inventory inventory = new Inventory(player);
        final BlockPos upperPosition = new BlockPos(10, 70, 3);
        final BlockPos lowerPosition = new BlockPos(10, 66, 3);
        final AtomicReference<ItemStack> upperCell = new AtomicReference<>();
        final AtomicReference<ItemStack> lowerCell = new AtomicReference<>();
        final AtomicReference<ItemStack> carried = new AtomicReference<>(new ItemStack(Items.DIAMOND));

        Fixture() {
            when(host.isFormed()).thenReturn(true);
            when(host.getCluster()).thenReturn(cluster);
            when(cluster.getUpperDrives()).thenReturn(List.of(upper));
            when(cluster.getLowerDrives()).thenReturn(List.of(lower));
            wire(upper, upperPosition, upperCell);
            wire(lower, lowerPosition, lowerCell);
            when(player.level()).thenReturn(mock(Level.class));
            when(player.getInventory()).thenReturn(inventory);
            player.containerMenu = menu;
            when(menu.getCarried()).thenAnswer(call -> carried.get());
            doAnswer(call -> { carried.set(call.getArgument(0)); return null; }).when(menu).setCarried(any());
        }

        private static void wire(ECOComputationDriveBlockEntity drive, BlockPos position, AtomicReference<ItemStack> cell) {
            when(drive.getBlockPos()).thenReturn(position);
            when(drive.getCellStack()).thenAnswer(call -> cell.get());
            when(drive.canExtractCell()).thenReturn(true);
            when(drive.isItemValid(any())).thenReturn(true);
            doAnswer(call -> { cell.set(call.getArgument(0)); return null; }).when(drive).setCellStack(any());
        }

        boolean insert(BlockPos position) {
            return ComputationCellUi.insert(host, player, position, carried.get().copy());
        }

        boolean take(BlockPos position, ItemStack expected, boolean toInventory) {
            return ComputationCellUi.extract(host, player, position, expected, toInventory);
        }
    }
}
