package cn.dancingsnow.neoecoae.menu;

import appeng.api.inventories.InternalInventory;
import appeng.menu.slot.RestrictedInputSlot;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import net.minecraft.world.item.ItemStack;

public class WithECORestrictedInputSlot extends RestrictedInputSlot {
    private final PlacableItemType which;

    public WithECORestrictedInputSlot(PlacableItemType valid, InternalInventory inv, int invSlot) {
        super(valid, inv, invSlot);
        this.which = valid;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        boolean b = super.mayPlace(stack);
        if (!b && this.which == PlacableItemType.STORAGE_CELLS) {
            return ECOStorageCells.isCellHandled(stack);
        }
        return b;
    }
}
