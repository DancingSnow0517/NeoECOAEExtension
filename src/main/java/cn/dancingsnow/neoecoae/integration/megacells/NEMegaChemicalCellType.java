package cn.dancingsnow.neoecoae.integration.megacells;

import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import net.minecraft.network.chat.Component;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

final class NEMegaChemicalCellType {
    static final NECellTypeEntry MEGA_CHEMICAL = REGISTRATE.cellType("mega_chemical")
        .desc(Component.translatable("cell_type.neoecoae.mega_chemical").withColor(0x37f89e))
        .typeCount(315)
        .register();

    static void register() {
        // Intentional class-initialization barrier: registration order must remain explicit.
    }
}
