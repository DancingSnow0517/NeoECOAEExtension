package cn.dancingsnow.neoecoae.integration.appmek;

import cn.dancingsnow.neoecoae.integration.ECOKeyTypeCellHandler;

/**
 * Applied Mekanistics-only storage cell handler.
 */
public final class ECOChemicalCellHandler extends ECOKeyTypeCellHandler {
    public static final ECOChemicalCellHandler INSTANCE = new ECOChemicalCellHandler();

    private ECOChemicalCellHandler() {
        super(NEAppMekCellTypes.CHEMICAL::get, AppMekCompat::getChemicalKeyType);
    }
}
