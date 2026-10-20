package cn.dancingsnow.neoecoae.integration.ae2omnicells.item;

import appeng.api.stacks.AEKeyType;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import me.ramidzkh.mekae2.ae2.MekanismKeyType;

import java.util.Set;
import java.util.function.Supplier;

/** Uses OmniCells' waste policy and SavedData storage, including in ordinary ME drives. */
public final class ECOSpentNuclearWasteCellItem extends ECOUniversalStorageCellItem {
    public ECOSpentNuclearWasteCellItem(Properties properties, IECOTier tier,
                                       Supplier<ECOCellType> cellType, double idleDrain, long totalBytes) {
        super(properties, tier, cellType, idleDrain, 1, totalBytes);
    }

    @Override
    public Set<AEKeyType> getKeyTypes() {
        return Set.of(MekanismKeyType.TYPE);
    }

    @Override
    public boolean isSpentNuclearWasteCell() {
        return true;
    }
}
