package cn.dancingsnow.neoecoae.util;

import appeng.api.inventories.InternalInventory;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

class PatternInventoryTransferTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void regionRemovalNotifiesEveryPhysicalSlotWithoutMutatingSourceStacks() {
        InternalInventory inventory = mock(InternalInventory.class);
        when(inventory.size()).thenReturn(3);
        ItemStack first = source(1);
        ItemStack last = source(1);
        when(inventory.getStackInSlot(0)).thenReturn(first);
        when(inventory.getStackInSlot(1)).thenReturn(ItemStack.EMPTY);
        when(inventory.getStackInSlot(2)).thenReturn(last);

        PatternInventoryTransfer.moveRegion(inventory, copy -> {
            when(copy.getCount()).thenReturn(0);
            when(copy.isEmpty()).thenReturn(true);
        });

        verify(inventory).setItemDirect(0, ItemStack.EMPTY);
        verify(inventory).setItemDirect(2, ItemStack.EMPTY);
        verify(inventory, never()).setItemDirect(eq(1), any());
        verify(first, never()).shrink(anyInt());
        verify(last, never()).shrink(anyInt());
    }

    @Test
    void partialAndRejectedTransfersKeepRemaindersInTheirOriginalSlots() {
        InternalInventory inventory = mock(InternalInventory.class);
        when(inventory.size()).thenReturn(2);
        ItemStack first = source(4);
        ItemStack last = source(1);
        when(inventory.getStackInSlot(0)).thenReturn(first);
        when(inventory.getStackInSlot(1)).thenReturn(last);
        ItemStack partial = first.copy();

        PatternInventoryTransfer.moveRegion(inventory, copy -> {
            if (copy == partial) when(copy.getCount()).thenReturn(2);
        });

        verify(inventory).setItemDirect(0, partial);
        verify(inventory, never()).setItemDirect(eq(1), any());
        assertSame(last, inventory.getStackInSlot(1));
    }

    private static ItemStack source(int count) {
        ItemStack source = mock(ItemStack.class);
        ItemStack copy = mock(ItemStack.class);
        when(source.getCount()).thenReturn(count);
        when(copy.getCount()).thenReturn(count);
        when(source.copy()).thenReturn(copy);
        return source;
    }
}
