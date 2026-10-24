package cn.dancingsnow.neoecoae.integration.arsenergistique;

import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import cn.dancingsnow.neoecoae.integration.ExternalCellKeyTypes;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEArsEnergistiqueCellTypes {

    public static final NECellTypeEntry SOURCE = REGISTRATE.cellType("source")
        .desc(net.minecraft.network.chat.Component.translatable("key_type.arseng.source").withStyle(style -> style.withColor(0xc038fb)))
        .typeCount(1)
        .register();

    public static void register() {
    }
}
