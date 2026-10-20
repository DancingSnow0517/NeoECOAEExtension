package cn.dancingsnow.neoecoae.api.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.ICellWorkbenchItem;
import cn.dancingsnow.neoecoae.api.IECOTier;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

public interface IBasicECOCellItem extends ICellWorkbenchItem, IECOStorageCellItem {
    IECOTier getTier();
    AEKeyType getKeyType();
    long getBytes();
    int getBytesPerType();
    default double getIdleDrain() {
        return (double) getBytes() / (1 << 20);
    }
    int getTotalTypes();
    ECOCellType getCellType();
    @Override
    default Set<AEKeyType> getKeyTypes() {
        return Set.of(getKeyType());
    }
    default boolean isBlackListed(ItemStack cellStack, AEKey what) {
        return false;
    }
}
