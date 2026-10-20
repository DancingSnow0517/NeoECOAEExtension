package cn.dancingsnow.neoecoae.integration.appbot;

import appbot.ae2.ManaKeyType;
import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEAppBotCellTypes {
    public static final NECellTypeEntry MANA = REGISTRATE.cellType("mana")
        .desc(ManaKeyType.TYPE.getDescription().copy().withColor(0x38b7fb))
        .typeCount(1)
        .register();

    public static void register() {
    }
}
