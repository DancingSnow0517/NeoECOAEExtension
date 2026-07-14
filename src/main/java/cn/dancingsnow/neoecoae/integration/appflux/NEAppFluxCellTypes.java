package cn.dancingsnow.neoecoae.integration.appflux;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;

public class NEAppFluxCellTypes {
    public static final NECellTypeEntry FE = REGISTRATE
            .cellType("flux")
            .desc(AppFluxCompat.getFluxKeyType().getDescription().copy().withStyle(style -> style.withColor(0xdd504c)))
            .typeCount(1)
            .register();

    public static void register() {}
}
