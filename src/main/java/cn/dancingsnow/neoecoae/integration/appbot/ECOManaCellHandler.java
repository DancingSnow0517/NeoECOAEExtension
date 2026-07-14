package cn.dancingsnow.neoecoae.integration.appbot;

import cn.dancingsnow.neoecoae.integration.ECOKeyTypeCellHandler;

public final class ECOManaCellHandler extends ECOKeyTypeCellHandler {
    public static final ECOManaCellHandler INSTANCE = new ECOManaCellHandler();

    private ECOManaCellHandler() {
        super(NEAppBotCellTypes.MANA::get, AppBotCompat::getManaKeyType);
    }
}
