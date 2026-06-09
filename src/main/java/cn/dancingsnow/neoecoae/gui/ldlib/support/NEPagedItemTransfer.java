package cn.dancingsnow.neoecoae.gui.ldlib.support;

import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import java.util.function.IntSupplier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class NEPagedItemTransfer implements IItemTransfer {
    private final IItemTransfer delegate;
    private final IntSupplier currentPage;
    private final IntSupplier pageCount;
    private final int pageSize;

    public NEPagedItemTransfer(IItemTransfer delegate, IntSupplier currentPage, IntSupplier pageCount, int pageSize) {
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
    public void setStackInSlot(int slot, ItemStack stack) {
        int actualSlot = mapSlot(slot);
        if (actualSlot >= 0) {
            delegate.setStackInSlot(actualSlot, stack);
        }
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, ItemStack stack, boolean simulate, boolean notifyChanges) {
        int actualSlot = mapSlot(slot);
        return actualSlot < 0 ? stack : delegate.insertItem(actualSlot, stack, simulate, notifyChanges);
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate, boolean notifyChanges) {
        int actualSlot = mapSlot(slot);
        return actualSlot < 0 ? ItemStack.EMPTY : delegate.extractItem(actualSlot, amount, simulate, notifyChanges);
    }

    @Override
    public int getSlotLimit(int slot) {
        int actualSlot = mapSlot(slot);
        return actualSlot < 0 ? 0 : delegate.getSlotLimit(actualSlot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        int actualSlot = mapSlot(slot);
        return actualSlot >= 0 && delegate.isItemValid(actualSlot, stack);
    }

    @Override
    public Object createSnapshot() {
        return delegate.createSnapshot();
    }

    @Override
    public void restoreFromSnapshot(Object snapshot) {
        delegate.restoreFromSnapshot(snapshot);
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
