package cn.dancingsnow.neoecoae.api.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.ICellWorkbenchItem;
import cn.dancingsnow.neoecoae.api.IECOTier;
import java.util.Set;
import net.minecraft.world.item.ItemStack;

public interface IBasicECOCellItem extends ICellWorkbenchItem, IECOStorageCellItem {
    IECOTier getTier();

    AEKeyType getKeyType();

    long getBytes();

    default long getIdleDrainBytes() {
        return getBytes();
    }

    int getBytesPerType();

    int getTotalTypes();

    @Override
    ECOCellType getCellType();

    @Override
    default Set<AEKeyType> getKeyTypes() {
        return Set.of(getKeyType());
    }

    default boolean isBlackListed(ItemStack cellStack, AEKey what) {
        return false;
    }
}
