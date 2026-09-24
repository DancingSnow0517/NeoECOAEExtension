package cn.dancingsnow.neoecoae.integration.ae2lt;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import net.minecraft.network.chat.Component;

final class NELightningCellTypes {
    static final NECellTypeEntry LIGHTNING = REGISTRATE
            .cellType("lightning")
            .desc(Component.translatable("key_type.ae2lt.lightning").withStyle(style -> style.withColor(0xB66CFF)))
            .typeCount(2)
            .register();

    private NELightningCellTypes() {}

    static void register() {}
}
