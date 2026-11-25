package cn.dancingsnow.neoecoae.blocks.entity.storage;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

final class ECOStorageComponentSlot {
    private final ECOStorageSystemBlockEntity host;
    ECOStorageComponentSlot(ECOStorageSystemBlockEntity host) { this.host = host; }

    IItemHandlerModifiable componentItemHandler(IItemHandlerModifiable delegate) {
        return new IItemHandlerModifiable() {
            @Override public int getSlots() { return delegate.getSlots(); }
            @Override public ItemStack getStackInSlot(int slot) {
                ItemStack stack = delegate.getStackInSlot(slot);
                return stack.isEmpty() ? ItemStack.EMPTY : new ItemStack(stack.getItem(), stack.getCount());
            }
            @Override public void setStackInSlot(int slot, ItemStack stack) {
                if (slot == 0 && (stack.isEmpty() || ECOStorageSystemBlockEntity.isInfiniteComponent(stack)))
                    delegate.setStackInSlot(slot, stack);
            }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return isItemValid(slot, stack) ? delegate.insertItem(slot, stack, simulate) : stack;
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return slot == 0 && host.canExtractInfiniteComponents() ? delegate.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
            }
            @Override public int getSlotLimit(int slot) { return delegate.getSlotLimit(slot); }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                return slot == 0 && ECOStorageSystemBlockEntity.isInfiniteComponent(stack) && delegate.isItemValid(slot, stack);
            }
        };
    }
}
