package cn.dancingsnow.neoecoae.data;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.data.lang.NELangGenerator;
import cn.dancingsnow.neoecoae.data.model.CellModelGenerator;
import cn.dancingsnow.neoecoae.data.recipe.NERecipeGenerator;
import cn.dancingsnow.neoecoae.integration.ponder.NEPonderPlugin;
import cn.dancingsnow.neoecoae.registration.provider.NEProviderTypes;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.providers.RegistrateLangProvider;
import net.createmod.ponder.foundation.PonderIndex;

import java.util.function.BiConsumer;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEDataGen {
    public static void configureDataGen() {
        REGISTRATE.addDataGenerator(NEProviderTypes.CELL_MODEL, CellModelGenerator::accept);
        REGISTRATE.addDataGenerator(ProviderType.LANG, NELangGenerator::accept);
        REGISTRATE.addDataGenerator(ProviderType.RECIPE, NERecipeGenerator::accept);
    }
}
