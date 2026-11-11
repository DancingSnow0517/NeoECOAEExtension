package cn.dancingsnow.neoecoae.integration.repae2bridge;

import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import net.minecraft.network.chat.Component;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public final class NERepBridgeCellTypes {
    public static final NECellTypeEntry MATTER = REGISTRATE.cellType("rep_matter")
        .desc(Component.translatable("cell_type.neoecoae.rep_matter").withColor(0x72E567))
        .typeCount(8)
        .register();

    private NERepBridgeCellTypes() {}

    static void register() {}
}
