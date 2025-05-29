package cn.dancingsnow.neoecoae.registration.provider;

import com.tterrag.registrate.providers.ProviderType;

public class NEProviderTypes {
    public static final ProviderType<NECellModelProvider> CELL_MODEL = ProviderType.registerProvider(
        "cell_model",
        c -> new NECellModelProvider(c.parent(), c.output(), c.fileHelper())
    );
}
