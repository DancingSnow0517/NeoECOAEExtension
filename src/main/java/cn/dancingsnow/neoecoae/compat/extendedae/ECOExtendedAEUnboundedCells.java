package cn.dancingsnow.neoecoae.compat.extendedae;

import appeng.api.stacks.AEKey;
import appeng.api.storage.cells.StorageCell;
import com.glodblock.github.extendedae.common.inventory.InfinityCellInventory;

import java.util.Set;

public final class ECOExtendedAEUnboundedCells {
    private ECOExtendedAEUnboundedCells() {}

    public static void collect(StorageCell cell, Set<AEKey> target) {
        if (cell instanceof InfinityCellInventory infinite) {
            target.addAll(infinite.getAvailableStacks().keySet());
        }
    }
}
