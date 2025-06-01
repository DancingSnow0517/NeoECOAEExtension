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
        if (host.getCellStack() != null) {
            return stack;
        } else {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (!simulate) {
                host.setCellStack(stack.copyWithCount(1));
            }
            ItemStack copy = stack.copy();
            copy.shrink(1);
            return copy;
        }
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (host.getCellStack() == null) {
            return ItemStack.EMPTY;
        }
        if (amount <= 0) {
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
