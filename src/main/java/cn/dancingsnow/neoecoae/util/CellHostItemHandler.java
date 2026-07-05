package cn.dancingsnow.neoecoae.util;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

public class CellHostItemHandler implements IItemHandler {
    private final ICellHost host;
    @Nullable private String lastReadFingerprint;

    public CellHostItemHandler(ICellHost host) {
        this.host = host;
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (!isValidSlot(slot)) {
            return ItemStack.EMPTY;
        }
        // AE2 writes running crafting task state into the computation cell's ItemStack NBT.
        // Must return the real stack reference; returning a copy causes task state to be lost on reload.
        var stack = host.getCellStack();
        if (stack != null) {
            notifyIfReadReferenceChanged(stack);
            return stack;
        }
        lastReadFingerprint = null;
        return ItemStack.EMPTY;
    }

    private void notifyIfReadReferenceChanged(ItemStack stack) {
        if (!host.shouldNotifyPersistenceOnRead()) {
            return;
        }
        String fingerprint = stack.save(new net.minecraft.nbt.CompoundTag()).toString();
        if (lastReadFingerprint != null && !lastReadFingerprint.equals(fingerprint)) {
            host.notifyPersistence();
        }
        lastReadFingerprint = fingerprint;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!isValidSlot(slot)) {
            return stack;
        }
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!host.isItemValid(stack) || host.getCellStack() != null) {
            return stack;
        }

        if (!simulate) {
            host.setCellStack(stack.copyWithCount(1));
        }
        ItemStack copy = stack.copy();
        copy.shrink(1);
        return copy;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!isValidSlot(slot)) {
            return ItemStack.EMPTY;
        }
        if (host.getCellStack() == null || !host.canExtractCell()) {
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
        return isValidSlot(slot) ? 1 : 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return isValidSlot(slot) && host.isItemValid(stack);
    }

    private boolean isValidSlot(int slot) {
        return slot >= 0 && slot < getSlots();
    }
}
