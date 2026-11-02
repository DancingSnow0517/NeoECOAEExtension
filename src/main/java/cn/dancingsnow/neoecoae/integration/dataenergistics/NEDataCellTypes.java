package cn.dancingsnow.neoecoae.integration.dataenergistics;

import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import net.minecraft.network.chat.Component;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public final class NEDataCellTypes {
    public static final NECellTypeEntry DATA = REGISTRATE.cellType("data")
        .desc(Component.translatable("cell_type.neoecoae.data").withColor(0x339fba))
        .typeCount(4)
        .register();

    public static void register() {}
}
