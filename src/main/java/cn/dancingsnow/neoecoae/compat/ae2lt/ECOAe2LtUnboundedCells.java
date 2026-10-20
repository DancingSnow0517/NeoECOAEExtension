package cn.dancingsnow.neoecoae.compat.ae2lt;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.cells.StorageCell;
import com.moakiee.ae2lt.me.cell.FixedInfiniteCellInventory;

import java.util.Set;

public final class ECOAe2LtUnboundedCells {
    private ECOAe2LtUnboundedCells() {}

    public static void collect(StorageCell cell, Set<AEKey> target) {
        if (!(cell instanceof FixedInfiniteCellInventory fixed)) return;
        for (AEKey key : fixed.getAvailableStacks().keySet()) {
            if (fixed.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty()) == Long.MAX_VALUE) {
                target.add(key);
            }
        }
    }
}
