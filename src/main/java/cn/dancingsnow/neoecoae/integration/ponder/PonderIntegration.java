package cn.dancingsnow.neoecoae.integration.ponder;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Integration("ponder")
public class PonderIntegration {
    public void apply() {
        NeoECOAE.MOD_BUS.addListener(this::onFMLCommonSetup);
    }

    private void onFMLCommonSetup(FMLClientSetupEvent event) {
        PonderIndex.addPlugin(new NEPonderPlugin());
    }
}
