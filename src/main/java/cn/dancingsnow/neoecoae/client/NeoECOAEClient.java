package cn.dancingsnow.neoecoae.client;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlockEntities;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.ECOComputationModels;
import cn.dancingsnow.neoecoae.client.all.NEExtraModels;
import cn.dancingsnow.neoecoae.client.renderer.blockentity.ECOComputationDriveRenderer;
import cn.dancingsnow.neoecoae.client.renderer.blockentity.ECODriveRenderer;
import cn.dancingsnow.neoecoae.gui.nativeui.NENativeMenus;
import cn.dancingsnow.neoecoae.gui.nativeui.screen.NEComputationControllerScreen;
import cn.dancingsnow.neoecoae.gui.nativeui.screen.NECraftingControllerScreen;
import cn.dancingsnow.neoecoae.gui.nativeui.screen.NECraftingPatternBusScreen;
import cn.dancingsnow.neoecoae.gui.nativeui.screen.NEFluidHatchScreen;
import cn.dancingsnow.neoecoae.gui.nativeui.screen.NEIntegratedWorkingStationScreen;
import cn.dancingsnow.neoecoae.gui.nativeui.screen.NEStorageControllerScreen;
import cn.dancingsnow.neoecoae.gui.nativeui.screen.NEStructureTerminalScreen;
import net.minecraft.client.gui.screens.MenuScreens;
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

        // Register native UI screens
        MenuScreens.register(NENativeMenus.STORAGE_CONTROLLER.get(), NEStorageControllerScreen::new);
        MenuScreens.register(NENativeMenus.COMPUTATION_CONTROLLER.get(), NEComputationControllerScreen::new);
        MenuScreens.register(NENativeMenus.CRAFTING_CONTROLLER.get(), NECraftingControllerScreen::new);
        MenuScreens.register(NENativeMenus.INTEGRATED_WORKING_STATION.get(), NEIntegratedWorkingStationScreen::new);
        MenuScreens.register(NENativeMenus.CRAFTING_PATTERN_BUS.get(), NECraftingPatternBusScreen::new);
        MenuScreens.register(NENativeMenus.FLUID_HATCH.get(), NEFluidHatchScreen::new);
        MenuScreens.register(NENativeMenus.STRUCTURE_TERMINAL.get(), NEStructureTerminalScreen::new);
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
