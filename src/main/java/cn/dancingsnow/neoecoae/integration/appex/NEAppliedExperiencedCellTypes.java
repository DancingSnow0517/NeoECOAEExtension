package cn.dancingsnow.neoecoae.integration.appex;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import net.minecraft.network.chat.Component;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

final class NEAppliedExperiencedCellTypes {
    static {
        REGISTRATE.addLang("cell_type", NeoECOAE.id("experience"), "Experience");
    }

    static final NECellTypeEntry EXPERIENCE = REGISTRATE.cellType("experience")
        .desc(Component.translatable("cell_type.neoecoae.experience").withColor(0x74d56d))
        .typeCount(1)
        .register();

    private NEAppliedExperiencedCellTypes() {
    }

    static void register() {
    }
}
