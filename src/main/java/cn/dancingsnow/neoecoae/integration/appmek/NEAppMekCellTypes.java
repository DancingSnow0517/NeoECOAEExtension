package cn.dancingsnow.neoecoae.integration.appmek;

import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import me.ramidzkh.mekae2.ae2.MekanismKeyType;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEAppMekCellTypes {

    public static final NECellTypeEntry MEKANISM = REGISTRATE
        .cellType("mekanism")
        .desc(MekanismKeyType.TYPE.getDescription().copy().withColor(0x37f89e))
        .typeCount(25)
        .register();

    public static void register() {}
}
