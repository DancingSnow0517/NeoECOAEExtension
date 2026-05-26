package cn.dancingsnow.neoecoae.registration.provider;

import com.tterrag.registrate.providers.ProviderType;

public class NEProviderTypes {
    public static final ProviderType<NECellModelProvider> CELL_MODEL = ProviderType.register(
        "cell_model",
        (parent, event) -> new NECellModelProvider(
            parent,
            event.getGenerator().getPackOutput(),
            event.getExistingFileHelper()
        )
    );
}
