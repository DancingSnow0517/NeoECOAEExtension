package cn.dancingsnow.neoecoae.integration.appliedsoul;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import net.minecraft.network.chat.Component;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

final class NEAppliedSoulCellTypes {
    static {
        REGISTRATE.addLang("cell_type", NeoECOAE.id("soul"), "Soul");
    }

    static final NECellTypeEntry SOUL = REGISTRATE.cellType("soul")
        .desc(Component.translatable("cell_type.neoecoae.soul").withColor(0xa56acb))
        .typeCount(1)
        .register();

    private NEAppliedSoulCellTypes() {
    }

    static void register() {
    }
}
