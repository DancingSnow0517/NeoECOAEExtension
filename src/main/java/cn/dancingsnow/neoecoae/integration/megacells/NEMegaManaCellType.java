package cn.dancingsnow.neoecoae.integration.megacells;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import net.minecraft.network.chat.Component;

final class NEMegaManaCellType {
    static final NECellTypeEntry MEGA_MANA = REGISTRATE.cellType("mega_mana")
            .desc(Component.translatable("cell_type.neoecoae.mega_mana")
                    .withStyle(style -> style.withColor(0x50c878)))
            .typeCount(1)
            .register();

    static void register() {
        // Class initialization registers the optional cell type.
    }
}
