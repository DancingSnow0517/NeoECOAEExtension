package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import com.tterrag.registrate.util.entry.RegistryEntry;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEEcoTiers {
    public static final RegistryEntry<IECOTier, ECOTier> L4 = REGISTRATE
        .ecoTier("l4", () -> ECOTier.L4)
        .register();

    public static final RegistryEntry<IECOTier, ECOTier> L6 = REGISTRATE
        .ecoTier("l6", () -> ECOTier.L6)
        .register();

    public static final RegistryEntry<IECOTier, ECOTier> L9 = REGISTRATE
        .ecoTier("l9", () -> ECOTier.L9)
        .register();

    public static void register() {}
}
