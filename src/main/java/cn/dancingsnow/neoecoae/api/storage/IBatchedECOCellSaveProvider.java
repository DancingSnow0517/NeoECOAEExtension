package cn.dancingsnow.neoecoae.api.storage;

import appeng.api.storage.cells.ISaveProvider;

/**
 * Marker for ECO cell hosts that batch content persistence themselves.
 */
public interface IBatchedECOCellSaveProvider extends ISaveProvider {
    default void onStorageRecovered() {
        saveChanges();
    }

    /** Whether the block-entity host that owns this callback has been removed. */
    default boolean isHostRemoved() {
        return false;
    }
}
