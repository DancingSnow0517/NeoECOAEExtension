package cn.dancingsnow.neoecoae.compat.appflux;

import cn.dancingsnow.neoecoae.compat.ECOKeyTypeCellHandler;

public final class ECOFeCellHandler extends ECOKeyTypeCellHandler {
    public static final ECOFeCellHandler INSTANCE = new ECOFeCellHandler();

    private ECOFeCellHandler() {
        super(NEAppFluxCellTypes.FE::get, AppFluxCompat::getFluxKeyType);
    }
}
