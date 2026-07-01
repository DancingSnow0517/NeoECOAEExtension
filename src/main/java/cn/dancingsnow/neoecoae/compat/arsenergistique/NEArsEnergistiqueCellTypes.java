package cn.dancingsnow.neoecoae.compat.arsenergistique;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;

public class NEArsEnergistiqueCellTypes {
    public static final NECellTypeEntry SOURCE = REGISTRATE
            .cellType("source")
            .desc(ArsEnergistiqueCompat.getSourceKeyType()
                    .getDescription()
                    .copy()
                    .withStyle(style -> style.withColor(0xc038fb)))
            .typeCount(1)
            .register();

    public static void register() {}
}
