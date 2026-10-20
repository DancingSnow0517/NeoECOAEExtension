package cn.dancingsnow.neoecoae.integration.arsenergistique;

import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import gripe._90.arseng.me.key.SourceKeyType;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEArsEnergistiqueCellTypes {

    public static final NECellTypeEntry SOURCE = REGISTRATE.cellType("source")
        .desc(SourceKeyType.TYPE.getDescription().copy().withColor(0xc038fb))
        .typeCount(1)
        .register();

    public static void register() {
    }
}
