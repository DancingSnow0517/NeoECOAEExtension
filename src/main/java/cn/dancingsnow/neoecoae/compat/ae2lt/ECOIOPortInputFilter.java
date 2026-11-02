package cn.dancingsnow.neoecoae.compat.ae2lt;

import appeng.api.inventories.InternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import net.minecraft.world.item.ItemStack;

/** Extends only the cell queue's filter, preserving the port's extraction rules. */
public record ECOIOPortInputFilter(IAEItemFilter original) implements IAEItemFilter {
    @Override
    public boolean allowInsert(InternalInventory inventory, int slot, ItemStack stack) {
        return original.allowInsert(inventory, slot, stack) || ECOStorageCells.isCellHandled(stack);
    }

    @Override
    public boolean allowExtract(InternalInventory inventory, int slot, int amount) {
        return original.allowExtract(inventory, slot, amount);
    }
}
