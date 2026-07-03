package cn.dancingsnow.neoecoae.compat.appmek;

import cn.dancingsnow.neoecoae.compat.ECOKeyTypeCellHandler;

/**
 * Applied Mekanistics-only storage cell handler.
 */
public final class ECOChemicalCellHandler extends ECOKeyTypeCellHandler {
    public static final ECOChemicalCellHandler INSTANCE = new ECOChemicalCellHandler();

    private ECOChemicalCellHandler() {
        super(NEAppMekCellTypes.CHEMICAL::get, AppMekCompat::getChemicalKeyType);
    }
}
