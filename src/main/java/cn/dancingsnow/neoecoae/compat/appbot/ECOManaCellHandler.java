package cn.dancingsnow.neoecoae.compat.appbot;

import cn.dancingsnow.neoecoae.compat.ECOKeyTypeCellHandler;

public final class ECOManaCellHandler extends ECOKeyTypeCellHandler {
    public static final ECOManaCellHandler INSTANCE = new ECOManaCellHandler();

    private ECOManaCellHandler() {
        super(NEAppBotCellTypes.MANA::get, AppBotCompat::getManaKeyType);
    }
}
