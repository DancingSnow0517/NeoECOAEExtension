package cn.dancingsnow.neoecoae.items.cell;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.ICellWorkbenchItem;
import cn.dancingsnow.neoecoae.api.IECOTier;
import net.minecraft.world.item.ItemStack;

public interface IBasicECOCellItem extends ICellWorkbenchItem {
    IECOTier getTier();
    AEKeyType getKeyType();
    long getBytes();
    int getBytesPerType();
    int getTotalTypes();
    default boolean isBlackListed(ItemStack cellStack, AEKey what) {
        return false;
    }
}
