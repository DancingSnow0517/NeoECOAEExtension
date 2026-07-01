package cn.dancingsnow.neoecoae.compat.appmek;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;

public class NEAppMekCellTypes {
    public static final NECellTypeEntry CHEMICAL = REGISTRATE
            .cellType("chemicals")
            .desc(AppMekCompat.getChemicalKeyType()
                    .getDescription()
                    .copy()
                    .withStyle(style -> style.withColor(0x37f89e)))
            .typeCount(25)
            .register();

    public static void register() {}
}
