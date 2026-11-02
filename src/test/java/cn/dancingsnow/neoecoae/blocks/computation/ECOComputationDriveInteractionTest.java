package cn.dancingsnow.neoecoae.blocks.computation;

import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.computation.ECOComputationCells;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOComputationDriveInteractionTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void rightClickRecognizesTheEmptyBackingStackAndInstallsOneCell() throws Exception {
        var block = mock(ECOComputationDrive.class, CALLS_REAL_METHODS);
        var drive = mock(ECOComputationDriveBlockEntity.class, CALLS_REAL_METHODS);
        doNothing().when(drive).setChanged();
        var field = ECOComputationDriveBlockEntity.class.getDeclaredField("cellStack");
        field.setAccessible(true);
        field.set(drive, ItemStack.EMPTY);
        var level = mock(Level.class);
        when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(drive);
        var player = mock(Player.class);
        ItemStack held = new ItemStack(Items.DIAMOND, 2);
        var memory = new ECOComputationCells.Memory(mock(IECOTier.class), 1024);
        try (var cells = mockStatic(ECOComputationCells.class)) {
            cells.when(() -> ECOComputationCells.describe(any())).thenReturn(memory);
            assertEquals(InteractionResult.SUCCESS,
                block.useItemOn(held, null, level, BlockPos.ZERO, player, InteractionHand.MAIN_HAND, null));
            assertNotNull(drive.getCellStack());
            assertEquals(1, drive.getCellStack().getCount());
            assertEquals(1, held.getCount());
        }
    }

    @Test
    void shiftEmptyHandRemovesTheCellAndLeavesANonNullBackingStack() throws Exception {
        var block = mock(ECOComputationDrive.class, CALLS_REAL_METHODS);
        var drive = mock(ECOComputationDriveBlockEntity.class, CALLS_REAL_METHODS);
        doNothing().when(drive).setChanged();
        ItemStack installed = new ItemStack(Items.DIAMOND);
        var field = ECOComputationDriveBlockEntity.class.getDeclaredField("cellStack");
        field.setAccessible(true);
        field.set(drive, installed);
        var level = mock(Level.class);
        when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(drive);
        var player = mock(Player.class);
        when(player.getMainHandItem()).thenReturn(ItemStack.EMPTY);
        when(player.isShiftKeyDown()).thenReturn(true);

        assertEquals(InteractionResult.SUCCESS_SERVER,
            block.useWithoutItem(null, level, BlockPos.ZERO, player, null));
        assertNull(drive.getCellStack());
        assertSame(ItemStack.EMPTY, field.get(drive));
        verify(player).setItemInHand(InteractionHand.MAIN_HAND, installed);
        assertEquals(InteractionResult.PASS,
            block.useWithoutItem(null, level, BlockPos.ZERO, player, null));
    }
}
