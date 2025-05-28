package cn.dancingsnow.neoecoae.data;

import cn.dancingsnow.neoecoae.data.model.CellModelProvider;
import com.tterrag.registrate.providers.ProviderType;

public class NEProviderTypes {
    public static final ProviderType<CellModelProvider> CELL_MODEL = ProviderType.registerProvider(
        "cell_model",
        c -> new CellModelProvider(c.parent(), c.output(), c.fileHelper())
    );
}
