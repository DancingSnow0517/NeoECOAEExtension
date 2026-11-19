package cn.dancingsnow.neoecoae.util;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

public class CellHostItemHandler implements IItemHandler {
    private final ICellHost host;

    public CellHostItemHandler(ICellHost host) {
        this.host = host;
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return host.getCellStack() != null ? host.getCellStack() : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack installed = host.getCellStack();
        if ((installed != null && !installed.isEmpty()) || !host.canExtractCell() || !host.isItemValid(stack)) {
            return stack;
        }
        if (!simulate) {
            ItemStack cell = stack.copyWithCount(1);
            host.setCellStack(cell);
            if (host.getCellStack() != cell) return stack;
        }
        return stack.getCount() == 1 ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - 1);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (host.getCellStack() == null) {
            return ItemStack.EMPTY;
        }
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }
        if (!host.canExtractCell()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = host.getCellStack().copyWithCount(1);
        if (!simulate) {
            host.setCellStack(null);
        }
        return copy;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return host.isItemValid(stack);
    }
}
