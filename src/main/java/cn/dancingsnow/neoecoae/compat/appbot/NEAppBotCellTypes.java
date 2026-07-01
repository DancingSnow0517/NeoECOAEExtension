package cn.dancingsnow.neoecoae.compat.appbot;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;

public class NEAppBotCellTypes {
    public static final NECellTypeEntry MANA = REGISTRATE
            .cellType("mana")
            .desc(AppBotCompat.getManaKeyType().getDescription().copy().withStyle(style -> style.withColor(0x38b7fb)))
            .typeCount(1)
            .register();

    public static void register() {}
}
