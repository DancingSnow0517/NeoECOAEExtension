package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.inventories.InternalInventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * 26.1 replacement for AE2 1.21.1 {@code InternalInventory#toItemHandler()} where callers (LDLib2
 * {@code ItemHandlerSlot}) still take the deprecated {@link IItemHandlerModifiable}. Capabilities use
 * {@link InternalInventory#toResourceHandler()} instead.
 */
@SuppressWarnings({"deprecation", "removal"})
public record InternalInventoryItemHandler(InternalInventory inventory) implements IItemHandlerModifiable {

    public static IItemHandlerModifiable of(InternalInventory inventory) {
        return new InternalInventoryItemHandler(inventory);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        inventory.setItemDirect(slot, stack);
    }

    @Override
    public int getSlots() {
        return inventory.size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inventory.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return inventory.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return inventory.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return inventory.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return inventory.isItemValid(slot, stack);
    }
}
