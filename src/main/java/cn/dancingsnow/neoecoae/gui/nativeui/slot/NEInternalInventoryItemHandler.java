package cn.dancingsnow.neoecoae.gui.nativeui.slot;

import appeng.api.inventories.InternalInventory;
import cn.dancingsnow.neoecoae.blocks.entity.ECOIntegratedWorkingStationBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;

/**
 * Forge item-handler bridge for AE2 InternalInventory used by native menus.
 *
 * This keeps vanilla/Forge SlotItemHandler semantics for client rendering and
 * menu synchronisation, while still mutating AE2's InternalInventory directly
 * and notifying the owning block entity after real changes.
 */
public class NEInternalInventoryItemHandler implements IItemHandlerModifiable {

    private final InternalInventory inv;
    private final ECOIntegratedWorkingStationBlockEntity owner;
    private final boolean allowInsert;
    private final boolean allowExtract;

    public NEInternalInventoryItemHandler(InternalInventory inv,
                                          ECOIntegratedWorkingStationBlockEntity owner,
                                          boolean allowInsert,
                                          boolean allowExtract) {
        this.inv = inv;
        this.owner = owner;
        this.allowInsert = allowInsert;
        this.allowExtract = allowExtract;
    }

    @Override
    public int getSlots() {
        return inv.size();
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        return inv.getStackInSlot(slot);
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        inv.setItemDirect(slot, stack.copy());
        notifyOwner();
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (!allowInsert || stack.isEmpty()) {
            return stack;
        }
        ItemStack remainder = inv.insertItem(slot, stack, simulate);
        if (!simulate && remainder.getCount() != stack.getCount()) {
            notifyOwner();
        }
        return remainder;
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!allowExtract || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack extracted = inv.extractItem(slot, amount, simulate);
        if (!simulate && !extracted.isEmpty()) {
            notifyOwner();
        }
        return extracted;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return allowInsert;
    }

    private void notifyOwner() {
        if (owner != null) {
            owner.onGuiInventoryChanged();
        }
    }
}
