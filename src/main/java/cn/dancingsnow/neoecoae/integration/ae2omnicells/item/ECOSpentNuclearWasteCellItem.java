package cn.dancingsnow.neoecoae.integration.ae2omnicells.item;

import appeng.api.stacks.AEKeyType;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.integration.ExternalCellKeyTypes;

import java.util.Set;
import java.util.function.Supplier;

/**
 * Uses OmniCells' waste policy and SavedData storage, including in ordinary ME drives.
 *
 * <p>TODO(26.1 port): Applied Mekanistics has no 26.1 build, so this cell is never registered
 * ({@code NEOmniWasteCells} is only touched behind {@code NeoECOAE.isIntegrationActive("appmek")}, which is
 * false while "appmek" is in {@code UNPORTED_INTEGRATIONS}). The Mekanism key type is resolved by id instead of
 * {@code me.ramidzkh.mekae2.ae2.MekanismKeyType.TYPE} so this class compiles without the appmek jar; switch back
 * to the direct reference once a 26.1 Applied Mekanistics exists.
 */
public final class ECOSpentNuclearWasteCellItem extends ECOUniversalStorageCellItem {
    private static final Supplier<AEKeyType> MEKANISM_KEY_TYPE = ExternalCellKeyTypes.byId("appmek", "chemical");

    public ECOSpentNuclearWasteCellItem(Properties properties, IECOTier tier,
                                       Supplier<ECOCellType> cellType, double idleDrain, long totalBytes) {
        super(properties, tier, cellType, idleDrain, 1, totalBytes);
    }

    @Override
    public Set<AEKeyType> getKeyTypes() {
        return Set.of(MEKANISM_KEY_TYPE.get());
    }

    @Override
    public boolean isSpentNuclearWasteCell() {
        return true;
    }
}
