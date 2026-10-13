package cn.dancingsnow.neoecoae.compat.storage;

import appeng.api.stacks.AEKey;
import appeng.api.storage.cells.StorageCell;
import cn.dancingsnow.neoecoae.compat.ae2lt.ECOAe2LtUnboundedCells;
import cn.dancingsnow.neoecoae.compat.extendedae.ECOExtendedAEUnboundedCells;
import cn.dancingsnow.neoecoae.integration.ModIds;

import java.util.Set;

public final class ECOOptionalUnboundedCells {
    private ECOOptionalUnboundedCells() {}

    public static void collect(StorageCell cell, Set<AEKey> target) {
        if (cell == null) return;
        if (ModIds.isLoaded(ModIds.AE2LT)) ECOAe2LtUnboundedCells.collect(cell, target);
        if (ModIds.isLoaded(ModIds.EXTENDEDAE)) ECOExtendedAEUnboundedCells.collect(cell, target);
    }
}
