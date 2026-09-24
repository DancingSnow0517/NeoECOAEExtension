package cn.dancingsnow.neoecoae.integration.megacells;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import net.minecraft.network.chat.Component;

final class NEMegaSourceCellType {
    static final NECellTypeEntry MEGA_SOURCE = REGISTRATE
            .cellType("mega_source")
            .desc(Component.translatable("cell_type.neoecoae.mega_source")
                    .withStyle(style -> style.withColor(0x9d60d1)))
            .typeCount(1)
            .register();

    static void register() {
        // Class initialization registers the optional cell type.
    }
}
