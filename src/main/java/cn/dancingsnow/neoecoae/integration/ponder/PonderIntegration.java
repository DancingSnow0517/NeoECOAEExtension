package cn.dancingsnow.neoecoae.integration.ponder;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.providers.RegistrateLangProvider;
import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

@Integration("ponder")
public class PonderIntegration {
    public void apply() {
        NeoECOAE.MOD_BUS.addListener(this::onFMLCommonSetup);
        REGISTRATE.addDataGenerator(ProviderType.LANG, this::addPonderLang);
    }

    private void onFMLCommonSetup(FMLClientSetupEvent event) {
        PonderIndex.addPlugin(new NEPonderPlugin());
    }

    private void addPonderLang(RegistrateLangProvider provider) {
        PonderIndex.addPlugin(new NEPonderPlugin());
        PonderIndex.getLangAccess().provideLang(NeoECOAE.MOD_ID, provider::add);
    }
}
    