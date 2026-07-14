package cn.dancingsnow.neoecoae.integration.arsenergistique;

import cn.dancingsnow.neoecoae.integration.ECOKeyTypeCellHandler;

public final class ECOSourceCellHandler extends ECOKeyTypeCellHandler {
    public static final ECOSourceCellHandler INSTANCE = new ECOSourceCellHandler();

    private ECOSourceCellHandler() {
        super(NEArsEnergistiqueCellTypes.SOURCE::get, ArsEnergistiqueCompat::getSourceKeyType);
    }
}
