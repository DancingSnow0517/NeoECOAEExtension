package cn.dancingsnow.neoecoae.integration.megacells;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import net.minecraft.network.chat.Component;

public final class NEMegaCellTypes {
    public static final NECellTypeEntry MEGA_ITEM = REGISTRATE
            .cellType("mega_item")
            .desc(Component.translatable("cell_type.neoecoae.mega_item").withStyle(style -> style.withColor(0xf89737)))
            .typeCount(315)
            .register();
    public static final NECellTypeEntry MEGA_FLUID = REGISTRATE
            .cellType("mega_fluid")
            .desc(Component.translatable("cell_type.neoecoae.mega_fluid").withStyle(style -> style.withColor(0x9bc9fe)))
            .typeCount(315)
            .register();

    private NEMegaCellTypes() {}

    public static void register() {
        // Intentional class-initialization barrier: registration order must remain explicit.
    }
}
