package cn.dancingsnow.neoecoae.integration.megacells;

import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import net.minecraft.network.chat.Component;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public final class NEMegaCellTypes {
    public static final NECellTypeEntry MEGA_ITEM = REGISTRATE.cellType("mega_item")
        .desc(Component.translatable("cell_type.neoecoae.mega_item").withColor(0xf89737))
        .typeCount(63)
        .register();
    public static final NECellTypeEntry MEGA_FLUID = REGISTRATE.cellType("mega_fluid")
        .desc(Component.translatable("cell_type.neoecoae.mega_fluid").withColor(0x9bc9fe))
        .typeCount(18)
        .register();

    private NEMegaCellTypes() {
    }

    public static void register() {
    }
}
