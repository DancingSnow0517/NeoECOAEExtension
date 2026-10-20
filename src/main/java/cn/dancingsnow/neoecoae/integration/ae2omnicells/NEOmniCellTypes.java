package cn.dancingsnow.neoecoae.integration.ae2omnicells;

import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import net.minecraft.network.chat.Component;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public final class NEOmniCellTypes {
    public static final NECellTypeEntry OMNI = REGISTRATE.cellType("omni")
        .desc(Component.translatable("cell_type.neoecoae.omni").withColor(0x54E6F2))
        .typeCount(63)
        .register();

    public static final NECellTypeEntry COMPLEX_OMNI = REGISTRATE.cellType("complex_omni")
        .desc(Component.translatable("cell_type.neoecoae.complex_omni").withColor(0xB86BFF))
        .typeCount(6400)
        .register();

    public static final NECellTypeEntry QUANTUM_OMNI = REGISTRATE.cellType("quantum_omni")
        .desc(Component.translatable("cell_type.neoecoae.quantum_omni").withColor(0xE15CFF))
        .typeCount(0)
        .register();

    private NEOmniCellTypes() {
    }

    public static void register() {
    }
}
