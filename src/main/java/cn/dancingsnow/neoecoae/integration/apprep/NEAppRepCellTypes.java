package cn.dancingsnow.neoecoae.integration.apprep;

import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import net.minecraft.network.chat.Component;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public final class NEAppRepCellTypes {
    public static final NECellTypeEntry MATTER = REGISTRATE.cellType("matter")
        .desc(Component.translatable("cell_type.neoecoae.matter").withColor(0x72E567))
        .typeCount(3)
        .register();

    private NEAppRepCellTypes() {}

    static void register() {}
}
