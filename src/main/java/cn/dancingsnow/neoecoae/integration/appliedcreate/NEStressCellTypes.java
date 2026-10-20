package cn.dancingsnow.neoecoae.integration.appliedcreate;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import net.minecraft.network.chat.Component;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public final class NEStressCellTypes {
    static {
        REGISTRATE.addLang("cell_type", NeoECOAE.id("stress"), "Stress");
    }

    public static final NECellTypeEntry STRESS = REGISTRATE.cellType("stress")
        .desc(Component.translatable("cell_type.neoecoae.stress").withColor(0xEFC700))
        .typeCount(1)
        .register();

    private NEStressCellTypes() {}

    static void register() {}
}
