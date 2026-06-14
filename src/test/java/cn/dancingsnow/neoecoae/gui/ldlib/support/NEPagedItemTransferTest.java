package cn.dancingsnow.neoecoae.gui.ldlib.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class NEPagedItemTransferTest {
    @Test
    void mapsEachVisiblePageToDistinctPhysicalSlots() {
        var page = new MutableInt();
        var pages = new MutableInt();
        pages.value = 8;
        var transfer = new NEPagedItemTransfer(new SlotCountTransfer(504), () -> page.value, () -> pages.value, 63);

        page.value = 0;
        assertEquals(0, transfer.mapSlot(0));
        assertEquals(62, transfer.mapSlot(62));

        page.value = 1;
        assertEquals(63, transfer.mapSlot(0));
        assertEquals(125, transfer.mapSlot(62));

        page.value = 7;
        assertEquals(441, transfer.mapSlot(0));
        assertEquals(503, transfer.mapSlot(62));
    }

    @Test
    void rejectsSlotsOutsideTheVisibleOrEnabledPages() {
        var transfer = new NEPagedItemTransfer(new SlotCountTransfer(126), () -> 2, () -> 2, 63);

        assertEquals(-1, transfer.mapSlot(0));
        assertEquals(-1, transfer.mapSlot(-1));
        assertEquals(-1, transfer.mapSlot(63));
    }

    private static final class MutableInt {
        private int value;
    }

    private record SlotCountTransfer(int slots) implements IItemTransfer {
        @Override
        public int getSlots() {
            return slots;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {}

        @Override
        public @NotNull ItemStack insertItem(int slot, ItemStack stack, boolean simulate, boolean notifyChanges) {
            return stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate, boolean notifyChanges) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }

        @Override
        public Object createSnapshot() {
            return null;
        }

        @Override
        public void restoreFromSnapshot(Object snapshot) {}
    }
}
