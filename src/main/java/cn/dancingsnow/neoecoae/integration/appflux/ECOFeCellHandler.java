package cn.dancingsnow.neoecoae.integration.appflux;

import cn.dancingsnow.neoecoae.integration.ECOKeyTypeCellHandler;

public final class ECOFeCellHandler extends ECOKeyTypeCellHandler {
    public static final ECOFeCellHandler INSTANCE = new ECOFeCellHandler();

    private ECOFeCellHandler() {
        super(NEAppFluxCellTypes.FE::get, AppFluxCompat::getFluxKeyType);
    }
}
