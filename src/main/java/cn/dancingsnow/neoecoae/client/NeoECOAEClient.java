package cn.dancingsnow.neoecoae.client;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlockEntities;
import cn.dancingsnow.neoecoae.api.ECOComputationModels;
import cn.dancingsnow.neoecoae.api.rendering.FixedBlockEntityRenderers;
import cn.dancingsnow.neoecoae.client.all.NEExtraModels;
import cn.dancingsnow.neoecoae.client.renderer.blockentity.ECOComputationDriveRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = NeoECOAE.MOD_ID, dist = Dist.CLIENT)
public class NeoECOAEClient {
    public NeoECOAEClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(NeoECOAEClient::onClientSetup);
        NeoForge.EVENT_BUS.addListener(NeoECOAEClient::onAddChunkGeometry);
        NEExtraModels.register();
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        NeoECOAE.getIntegrationManager().loadAllClientIntegrations();
        ECOComputationModels.runDeferredRegistration();
        FixedBlockEntityRenderers.register(
            NEBlockEntities.COMPUTATION_DRIVE.get(),
            new ECOComputationDriveRenderer()
        );
    }

    public static void onAddChunkGeometry(AddSectionGeometryEvent event) {
        event.addRenderer(c -> FixedBlockEntityRenderers.render(c, event.getSectionOrigin()));
    }
}
