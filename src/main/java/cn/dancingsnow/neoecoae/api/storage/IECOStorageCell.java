package cn.dancingsnow.neoecoae.api.storage;

import appeng.api.storage.cells.StorageCell;
import cn.dancingsnow.neoecoae.api.IECOTier;

public interface IECOStorageCell extends StorageCell {
    IECOTier getTier();

    /** Whether this cell should receive marked inserts before ordinary cells. */
    default boolean prioritizesMarkedInserts() {
        return false;
    }

    /** Priority offset used when this cell is mounted inside an ECO storage host. */
    default int getStoragePriority() {
        return prioritizesMarkedInserts() ? 1 : 0;
    }
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
     * Whether an empty disk of this type may qualify its host for infinite mode.
     * Specialized finite cells can opt out while remaining usable in normal drives.
     */
    default boolean isInfiniteStorageEligible() {
        return true;
    }

    long getUsedBytes();

    long getTotalBytes();
}
