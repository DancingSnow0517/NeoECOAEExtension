package cn.dancingsnow.neoecoae.api.storage;

import appeng.api.storage.cells.StorageCell;
import cn.dancingsnow.neoecoae.api.IECOTier;

public interface IECOStorageCell extends StorageCell {
    IECOTier getTier();
    /**
     * @return cellType for display in gui
     */
    ECOCellType getCellType();

    long getStoredItemTypes();

    long getTotalItemTypes();

    default boolean hasInfiniteTypeCapacity() {
        return false;
    }

    long getUsedBytes();

    long getTotalBytes();
}
