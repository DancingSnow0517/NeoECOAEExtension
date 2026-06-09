package cn.dancingsnow.neoecoae.client;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlockEntities;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.ECOComputationModels;
import cn.dancingsnow.neoecoae.client.all.NEExtraModels;
import cn.dancingsnow.neoecoae.client.multiblock.preview.MultiblockPreviewScrollHandler;
import cn.dancingsnow.neoecoae.client.renderer.blockentity.ECOComputationDriveRenderer;
import cn.dancingsnow.neoecoae.client.renderer.blockentity.ECODriveRenderer;
import cn.dancingsnow.neoecoae.client.screen.NEConfigScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class NeoECOAEClient {
    public static void init(IEventBus modBus) {
        NEExtraModels.register();
        ModLoadingContext.get()
                .registerExtensionPoint(
                        ConfigScreenHandler.ConfigScreenFactory.class,
                        () -> new ConfigScreenHandler.ConfigScreenFactory(NEConfigScreen::new));
        modBus.addListener(NeoECOAEClient::onClientSetup);
        modBus.addListener(NEExtraModels::onRegisterExtraModels);
        modBus.addListener(NeoECOAEClient::onRegisterRenderers);
        modBus.addListener((FMLClientSetupEvent event) -> ECOCellModels.on(event));
        modBus.addListener((ModelEvent.RegisterAdditional event) -> ECOCellModels.on(event));
        MinecraftForge.EVENT_BUS.addListener(MultiblockPreviewScrollHandler::onMouseScrolled);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        NeoECOAE.getIntegrationManager().loadAllClientIntegrations();
        ECOComputationModels.runDeferredRegistration();
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(NEBlockEntities.COMPUTATION_DRIVE.get(), ECOComputationDriveRenderer::new);
        event.registerBlockEntityRenderer(NEBlockEntities.ECO_DRIVE.get(), ECODriveRenderer::new);
    }
}
