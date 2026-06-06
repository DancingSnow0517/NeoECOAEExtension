package cn.dancingsnow.neoecoae.gui.nativeui.slot;

import java.util.function.IntSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;

public final class NEPagedItemHandler implements IItemHandlerModifiable {
    private final IItemHandlerModifiable delegate;
    private final IntSupplier currentPage;
    private final IntSupplier pageCount;
    private final int pageSize;

    public NEPagedItemHandler(
            IItemHandlerModifiable delegate, IntSupplier currentPage, IntSupplier pageCount, int pageSize) {
        this.delegate = delegate;
        this.currentPage = currentPage;
        this.pageCount = pageCount;
        this.pageSize = pageSize;
    }

    @Override
    public int getSlots() {
        return pageSize;
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        int actualSlot = mapSlot(slot);
        return actualSlot < 0 ? ItemStack.EMPTY : delegate.getStackInSlot(actualSlot);
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        int actualSlot = mapSlot(slot);
        if (actualSlot >= 0) {
            delegate.setStackInSlot(actualSlot, stack);
        }
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        int actualSlot = mapSlot(slot);
        return actualSlot < 0 ? stack : delegate.insertItem(actualSlot, stack, simulate);
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        int actualSlot = mapSlot(slot);
        return actualSlot < 0 ? ItemStack.EMPTY : delegate.extractItem(actualSlot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        int actualSlot = mapSlot(slot);
        return actualSlot < 0 ? 0 : delegate.getSlotLimit(actualSlot);
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        int actualSlot = mapSlot(slot);
        return actualSlot >= 0 && delegate.isItemValid(actualSlot, stack);
    }

    public int mapSlot(int visibleSlot) {
        int page = currentPage.getAsInt();
        int pages = pageCount.getAsInt();
        if (visibleSlot < 0 || visibleSlot >= pageSize || page < 0 || page >= pages) {
            return -1;
        }
        int actualSlot = page * pageSize + visibleSlot;
        return actualSlot < delegate.getSlots() ? actualSlot : -1;
    }
}
