package cn.dancingsnow.neoecoae.gui.ldlib.support;

import appeng.api.inventories.InternalInventory;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class NEInternalInventoryItemTransfer implements IItemTransfer {
    private final InternalInventory inv;
    private final Runnable changeListener;
    private final boolean allowInsert;
    private final boolean allowExtract;

    public NEInternalInventoryItemTransfer(
            InternalInventory inv, Runnable changeListener, boolean allowInsert, boolean allowExtract) {
        this.inv = inv;
        this.changeListener = changeListener == null ? () -> {} : changeListener;
        this.allowInsert = allowInsert;
        this.allowExtract = allowExtract;
    }

    @Override
    public int getSlots() {
        return inv.size();
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        return inv.getStackInSlot(slot).copy();
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        inv.setItemDirect(slot, stack.copy());
        changeListener.run();
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, ItemStack stack, boolean simulate, boolean notifyChanges) {
        if (!allowInsert || stack.isEmpty()) {
            return stack;
        }
        ItemStack result = inv.insertItem(slot, stack, simulate);
        if (!simulate && notifyChanges && result.getCount() != stack.getCount()) {
            changeListener.run();
        }
        return result;
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate, boolean notifyChanges) {
        if (!allowExtract || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack result = inv.extractItem(slot, amount, simulate);
        if (!simulate && notifyChanges && !result.isEmpty()) {
            changeListener.run();
        }
        return result;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return allowInsert;
    }

    @Override
    public Object createSnapshot() {
        ItemStack[] snapshot = new ItemStack[inv.size()];
        for (int i = 0; i < snapshot.length; i++) {
            snapshot[i] = inv.getStackInSlot(i).copy();
        }
        return snapshot;
    }

    @Override
    public void restoreFromSnapshot(Object snapshot) {
        if (!(snapshot instanceof ItemStack[] stacks)) {
            return;
        }
        for (int i = 0; i < stacks.length && i < inv.size(); i++) {
            inv.setItemDirect(i, stacks[i].copy());
        }
        changeListener.run();
    }
}
