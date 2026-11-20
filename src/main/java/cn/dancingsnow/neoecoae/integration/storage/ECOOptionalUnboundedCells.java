package cn.dancingsnow.neoecoae.integration.storage;

import appeng.api.stacks.AEKey;
import appeng.api.storage.cells.StorageCell;
import cn.dancingsnow.neoecoae.integration.ae2lt.ECOAe2LtUnboundedCells;
import cn.dancingsnow.neoecoae.integration.extendedae.ECOExtendedAEUnboundedCells;
import net.minecraftforge.fml.ModList;

import java.util.Set;

public final class ECOOptionalUnboundedCells {
    private ECOOptionalUnboundedCells() {}

    public static void collect(StorageCell cell, Set<AEKey> target) {
        if (cell == null) return;
        var mods = ModList.get();
        if (mods == null) return;
        if (mods.isLoaded("ae2lt")) ECOAe2LtUnboundedCells.collect(cell, target);
        if (mods.isLoaded("expatternprovider")) ECOExtendedAEUnboundedCells.collect(cell, target);
    }
}
