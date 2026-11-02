package cn.dancingsnow.neoecoae.blocks.storage;

import appeng.api.networking.IManagedGridNode;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECODriveInteractionTest {
    private ECODriveBlock block;
    private ECODriveBlockEntity drive;
    private Level level;
    private Player player;

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @BeforeEach
    void setup() throws Exception {
        block = mock(ECODriveBlock.class, CALLS_REAL_METHODS);
        drive = mock(ECODriveBlockEntity.class, CALLS_REAL_METHODS);
        // Mockito skips constructors: explicitly reproduce a new or deserialized empty drive.
        var field = ECODriveBlockEntity.class.getDeclaredField("cellStack");
        field.setAccessible(true);
        field.set(drive, ItemStack.EMPTY);
        doReturn(mock(IManagedGridNode.class)).when(drive).getMainNode();
        doNothing().when(drive).setChanged();
        doAnswer(call -> !((ItemStack) call.getArgument(0)).isEmpty()).when(drive).isItemValid(any());
        level = mock(Level.class);
        when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(drive);
        player = mock(Player.class);
        when(player.getMainHandItem()).thenReturn(ItemStack.EMPTY);
        when(player.isShiftKeyDown()).thenReturn(true);
    }

    @Test
    void rightClickInstallsOneCellAndShiftEmptyHandReturnsTheSameCell() {
        ItemStack held = new ItemStack(Items.DIAMOND, 2);
        assertEquals(InteractionResult.SUCCESS, insert(held));
        ItemStack installed = drive.getCellStack();
        assertNotNull(installed);
        assertEquals(1, installed.getCount());
        assertEquals(1, held.getCount());
        assertEquals(InteractionResult.SUCCESS_SERVER, extract());
        assertNull(drive.getCellStack());
        verify(player).setItemInHand(InteractionHand.MAIN_HAND, installed);
        assertEquals(InteractionResult.SUCCESS, insert(held));
        assertEquals(1, drive.getCellStack().getCount());
        assertTrue(held.isEmpty());
    }

    @Test
    void occupiedDriveDoesNotConsumeAnotherCell() {
        ItemStack held = new ItemStack(Items.DIAMOND, 2);
        insert(held);
        ItemStack installed = drive.getCellStack();
        assertEquals(InteractionResult.TRY_WITH_EMPTY_HAND, insert(held));
        assertSame(installed, drive.getCellStack());
        assertEquals(1, held.getCount());
    }

    @Test
    void creativeInsertionKeepsTheHeldCell() {
        when(player.isCreative()).thenReturn(true);
        ItemStack held = new ItemStack(Items.DIAMOND);
        assertEquals(InteractionResult.SUCCESS, insert(held));
        assertEquals(1, held.getCount());
        assertNotSame(held, drive.getCellStack());
    }

    @Test
    void clientPredictsSuccessWithoutMovingItems() {
        when(level.isClientSide()).thenReturn(true);
        ItemStack held = new ItemStack(Items.DIAMOND);
        assertEquals(InteractionResult.SUCCESS, insert(held));
        assertNull(drive.getCellStack());
        assertEquals(1, held.getCount());
    }

    @Test
    void rejectedSetterDoesNotConsumeTheHeldCell() {
        doNothing().when(drive).setCellStack(any());
        ItemStack held = new ItemStack(Items.DIAMOND);
        assertEquals(InteractionResult.PASS, insert(held));
        assertEquals(1, held.getCount());
        assertNull(drive.getCellStack());
    }

    @Test
    void extractionLockKeepsTheInstalledCell() {
        insert(new ItemStack(Items.DIAMOND));
        ItemStack installed = drive.getCellStack();
        doReturn(ECODriveBlockEntity.CellExtractionBlockReason.INFINITE_MIGRATION)
            .when(drive).getCellExtractionBlockReason();
        assertEquals(InteractionResult.SUCCESS, extract());
        assertSame(installed, drive.getCellStack());
        verify(player, never()).setItemInHand(eq(InteractionHand.MAIN_HAND), same(installed));
        verify(player).sendOverlayMessage(any());
    }

    @Test
    void emptyDriveAndOccupiedHandCannotProduceOrOverwriteACell() {
        assertEquals(InteractionResult.PASS, extract());
        insert(new ItemStack(Items.DIAMOND));
        clearInvocations(player);
        when(player.getMainHandItem()).thenReturn(new ItemStack(Items.STONE));
        assertEquals(InteractionResult.PASS, extract());
        assertNotNull(drive.getCellStack());
        verify(player, never()).setItemInHand(eq(InteractionHand.MAIN_HAND), any());
    }

    private InteractionResult insert(ItemStack stack) {
        return block.useItemOn(stack, null, level, BlockPos.ZERO, player, InteractionHand.MAIN_HAND, null);
    }

    private InteractionResult extract() {
        return block.useWithoutItem(null, level, BlockPos.ZERO, player, null);
    }
}
