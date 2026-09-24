package cn.dancingsnow.neoecoae.integration.appliedpneumatics;

import cn.dancingsnow.neoecoae.integration.ECOKeyTypeCellHandler;

final class ECOAirCellHandler extends ECOKeyTypeCellHandler {
    static final ECOAirCellHandler INSTANCE = new ECOAirCellHandler();

    private ECOAirCellHandler() {
        super(NEAppliedPneumaticsCellTypes.AIR::get, AppliedPneumaticsCompat::getAirKeyType);
    }
}
