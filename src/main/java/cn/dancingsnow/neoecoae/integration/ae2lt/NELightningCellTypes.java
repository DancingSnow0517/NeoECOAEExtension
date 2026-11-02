package cn.dancingsnow.neoecoae.integration.ae2lt;

import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import net.minecraft.network.chat.Component;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public final class NELightningCellTypes {
    public static final NECellTypeEntry LIGHTNING = REGISTRATE.cellType("lightning")
        .desc(Component.translatable("key_type.ae2lt.lightning").withColor(0xB66CFF))
        .typeCount(2)
        .register();

    private NELightningCellTypes() {
    }

    public static void register() {
    }
}
