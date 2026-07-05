package cn.dancingsnow.neoecoae.compat.arsenergistique;

import cn.dancingsnow.neoecoae.compat.ECOKeyTypeCellHandler;

public final class ECOSourceCellHandler extends ECOKeyTypeCellHandler {
    public static final ECOSourceCellHandler INSTANCE = new ECOSourceCellHandler();

    private ECOSourceCellHandler() {
        super(NEArsEnergistiqueCellTypes.SOURCE::get, ArsEnergistiqueCompat::getSourceKeyType);
    }
}
