package cn.dancingsnow.neoecoae.integration.megacells;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import net.minecraft.network.chat.Component;

final class NEMegaEnergyCellType {
    static final NECellTypeEntry MEGA_ENERGY = REGISTRATE
            .cellType("mega_energy")
            .desc(Component.translatable("cell_type.neoecoae.mega_energy")
                    .withStyle(style -> style.withColor(0xdd504c)))
            .typeCount(1)
            .register();

    static void register() {
        // Intentional class-initialization barrier: registration order must remain explicit.
    }
}
