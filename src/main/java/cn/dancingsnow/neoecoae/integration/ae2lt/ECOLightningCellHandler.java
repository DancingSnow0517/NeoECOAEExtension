package cn.dancingsnow.neoecoae.integration.ae2lt;

import cn.dancingsnow.neoecoae.integration.ECOKeyTypeCellHandler;

final class ECOLightningCellHandler extends ECOKeyTypeCellHandler {
    static final ECOLightningCellHandler INSTANCE = new ECOLightningCellHandler();

    private ECOLightningCellHandler() {
        super(NELightningCellTypes.LIGHTNING::get, AE2LightningTechCompat::getLightningKeyType);
    }
}
