package cn.dancingsnow.neoecoae.util;

import appeng.api.inventories.InternalInventory;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/** Transfers copies so every real source mutation goes through the inventory's change notification. */
public final class PatternInventoryTransfer {
    private PatternInventoryTransfer() {}

    public static void moveRegion(InternalInventory inventory, Consumer<ItemStack> destination) {
        int slots = inventory.size();
        if (inventory instanceof WritablePrefix prefix) {
            // A view may show rows it cannot act on - the terminal appends a disk's recipes past the real slots.
            // Clearing those through the slot view does nothing, so sampling one would hand the player a copy
            // the disk keeps.
            slots = Math.min(slots, Math.max(0, prefix.writableSlotCount()));
        }
        for (int slot = 0; slot < slots; slot++) {
            ItemStack source = inventory.getStackInSlot(slot);
            if (source.isEmpty()) continue;
            ItemStack remaining = source.copy();
            destination.accept(remaining);
            if (remaining.getCount() != source.getCount()) {
                inventory.setItemDirect(slot, remaining.isEmpty() ? ItemStack.EMPTY : remaining);
            }
        }
    }
}
