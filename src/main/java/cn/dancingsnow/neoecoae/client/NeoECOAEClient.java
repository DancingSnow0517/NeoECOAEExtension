package cn.dancingsnow.neoecoae.client;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlockEntities;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.ECOComputationModels;
import cn.dancingsnow.neoecoae.client.all.NEExtraModels;
import cn.dancingsnow.neoecoae.client.renderer.blockentity.ECOComputationDriveRenderer;
import cn.dancingsnow.neoecoae.client.renderer.blockentity.ECODriveRenderer;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class NeoECOAEClient {
    public static void init(IEventBus modBus) {
        NEExtraModels.register();
        modBus.addListener(NeoECOAEClient::onClientSetup);
        modBus.addListener(NEExtraModels::onRegisterExtraModels);
        modBus.addListener(NeoECOAEClient::onRegisterRenderers);
        modBus.addListener((FMLClientSetupEvent event) -> ECOCellModels.on(event));
        modBus.addListener((ModelEvent.RegisterAdditional event) -> ECOCellModels.on(event));
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        NeoECOAE.getIntegrationManager().loadAllClientIntegrations();
        ECOComputationModels.runDeferredRegistration();
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
            NEBlockEntities.COMPUTATION_DRIVE.get(),
            ECOComputationDriveRenderer::new
        );
        event.registerBlockEntityRenderer(
            NEBlockEntities.ECO_DRIVE.get(),
            ECODriveRenderer::new
        );
    }
}
