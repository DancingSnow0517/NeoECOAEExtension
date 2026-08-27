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

    /**
     * Whether this storage cell may be migrated into the ECO infinite storage domain.
     * Specialized finite cells can opt out while remaining usable in normal drives.
     */
    default boolean isInfiniteStorageEligible() {
        return true;
    }

    long getUsedBytes();

    long getTotalBytes();
}
