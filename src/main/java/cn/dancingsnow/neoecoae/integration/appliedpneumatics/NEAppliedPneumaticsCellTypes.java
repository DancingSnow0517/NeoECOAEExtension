package cn.dancingsnow.neoecoae.integration.appliedpneumatics;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import net.minecraft.network.chat.Component;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

final class NEAppliedPneumaticsCellTypes {
    static {
        REGISTRATE.addLang("cell_type", NeoECOAE.id("air"), "Air");
    }

    static final NECellTypeEntry AIR = REGISTRATE.cellType("air")
        .desc(Component.translatable("cell_type.neoecoae.air").withColor(0x61c8e8))
        .typeCount(1)
        .register();

    private NEAppliedPneumaticsCellTypes() {
    }

    static void register() {
    }
}
