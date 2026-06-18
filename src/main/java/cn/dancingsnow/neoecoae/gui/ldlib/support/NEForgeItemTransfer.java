package cn.dancingsnow.neoecoae.gui.ldlib.support;

import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;

public class NEForgeItemTransfer implements IItemTransfer {
    private final IItemHandler delegate;
    private final IItemHandlerModifiable modifiableDelegate;
    private final Runnable changeListener;

    public NEForgeItemTransfer(IItemHandler delegate, Runnable changeListener) {
        this.delegate = delegate;
        this.modifiableDelegate = delegate instanceof IItemHandlerModifiable modifiable ? modifiable : null;
        this.changeListener = changeListener == null ? () -> {} : changeListener;
    }

    public NEForgeItemTransfer(IItemHandlerModifiable delegate, Runnable changeListener) {
        this.delegate = delegate;
        this.modifiableDelegate = delegate;
        this.changeListener = changeListener == null ? () -> {} : changeListener;
    }

    @Override
    public int getSlots() {
        return delegate.getSlots();
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        return delegate.getStackInSlot(slot);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (modifiableDelegate == null) {
            return;
        }
        modifiableDelegate.setStackInSlot(slot, stack);
        changeListener.run();
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, ItemStack stack, boolean simulate, boolean notifyChanges) {
        ItemStack result = delegate.insertItem(slot, stack, simulate);
        if (!simulate && notifyChanges && result.getCount() != stack.getCount()) {
            changeListener.run();
        }
        return result;
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate, boolean notifyChanges) {
        ItemStack result = delegate.extractItem(slot, amount, simulate);
        if (!simulate && notifyChanges && !result.isEmpty()) {
            changeListener.run();
        }
        return result;
    }

    @Override
    public int getSlotLimit(int slot) {
        return delegate.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return delegate.isItemValid(slot, stack);
    }

    @Override
    public Object createSnapshot() {
        ItemStack[] snapshot = new ItemStack[delegate.getSlots()];
        for (int i = 0; i < snapshot.length; i++) {
            snapshot[i] = delegate.getStackInSlot(i).copy();
        }
        return snapshot;
    }

    @Override
    public void restoreFromSnapshot(Object snapshot) {
        if (!(snapshot instanceof ItemStack[] stacks)) {
            return;
        }
        if (modifiableDelegate == null) {
            return;
        }
        for (int i = 0; i < stacks.length && i < delegate.getSlots(); i++) {
            modifiableDelegate.setStackInSlot(i, stacks[i].copy());
        }
        changeListener.run();
    }
}
