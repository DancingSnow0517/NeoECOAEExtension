package cn.dancingsnow.neoecoae.gui.nativeui.slot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class NEPagedItemHandlerTest {
    @Test
    void mapsEachVisiblePageToDistinctPhysicalSlots() {
        var page = new MutableInt();
        var pages = new MutableInt();
        pages.value = 8;
        var handler = new NEPagedItemHandler(new SlotCountHandler(504), () -> page.value, () -> pages.value, 63);

        page.value = 0;
        assertEquals(0, handler.mapSlot(0));
        assertEquals(62, handler.mapSlot(62));

        page.value = 1;
        assertEquals(63, handler.mapSlot(0));
        assertEquals(125, handler.mapSlot(62));

        page.value = 7;
        assertEquals(441, handler.mapSlot(0));
        assertEquals(503, handler.mapSlot(62));
    }

    @Test
    void rejectsSlotsOutsideTheVisibleOrEnabledPages() {
        var handler = new NEPagedItemHandler(new SlotCountHandler(126), () -> 2, () -> 2, 63);

        assertEquals(-1, handler.mapSlot(0));
        assertEquals(-1, handler.mapSlot(-1));
        assertEquals(-1, handler.mapSlot(63));
    }

    private static final class MutableInt {
        private int value;
    }

    private record SlotCountHandler(int slots) implements IItemHandlerModifiable {
        @Override
        public int getSlots() {
            return slots;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack stack) {}

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return true;
        }
    }
}
