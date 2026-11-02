package cn.dancingsnow.neoecoae.util;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CellHostItemHandlerTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void automationInstallsOnlyOneCellAndNullRepresentsRemoval() {
        Host host = new Host();
        var handler = new CellHostItemHandler(host);
        var resource = ItemResource.of(Items.DIAMOND);
        assertEquals(0, handler.getAmountAsLong(0));
        try (var transaction = Transaction.openRoot()) {
            assertEquals(1, handler.insert(0, resource, 64, transaction));
            transaction.commit();
        }
        assertEquals(1, host.stack.getCount());
        try (var transaction = Transaction.openRoot()) {
            assertEquals(1, handler.extract(0, resource, 64, transaction));
            transaction.commit();
        }
        assertNull(host.stack);
        assertEquals(0, handler.getAmountAsLong(0));
    }

    @Test
    void abortedTransfersRestoreTheOriginalCellAndCount() {
        Host host = new Host();
        var handler = new CellHostItemHandler(host);
        var resource = ItemResource.of(Items.DIAMOND);
        try (var transaction = Transaction.openRoot()) {
            assertEquals(1, handler.insert(0, resource, 1, transaction));
        }
        assertNull(host.stack);
        host.stack = new ItemStack(Items.DIAMOND);
        ItemStack original = host.stack;
        try (var transaction = Transaction.openRoot()) {
            assertEquals(1, handler.extract(0, resource, 1, transaction));
        }
        assertEquals(1, host.stack.getCount());
        assertEquals(1, original.getCount());
    }

    @Test
    void lockedCellCannotBeMutatedThroughTheReturnedStack() {
        Host host = new Host();
        host.stack = new ItemStack(Items.DIAMOND);
        host.locked = true;
        var handler = new CellHostItemHandler(host);
        var resource = ItemResource.of(Items.DIAMOND);
        try (var transaction = Transaction.openRoot()) {
            assertEquals(0, handler.extract(0, resource, 1, transaction));
            assertEquals(0, handler.insert(0, resource, 1, transaction));
            transaction.commit();
        }
        assertEquals(1, host.stack.getCount());
    }

    private static class Host implements ICellHost {
        private ItemStack stack;
        private boolean locked;
        @Override public ItemStack getCellStack() { return stack; }
        @Override public void setCellStack(ItemStack stack) {
            if (!locked && (stack == null || isItemValid(stack))) this.stack = stack;
        }
        @Override public boolean isItemValid(ItemStack stack) { return stack.is(Items.DIAMOND); }
        @Override public boolean canExtractCell() { return !locked; }
    }
}
