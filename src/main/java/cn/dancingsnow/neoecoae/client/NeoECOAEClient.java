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
import cn.dancingsnow.neoecoae.gui.ldlib.NEComputationControllerLDLibUI;
import cn.dancingsnow.neoecoae.gui.ldlib.NECraftingControllerLDLibUI;
import cn.dancingsnow.neoecoae.gui.ldlib.NECraftingPatternBusLDLibUI;
import cn.dancingsnow.neoecoae.gui.ldlib.NEFluidHatchLDLibUI;
import cn.dancingsnow.neoecoae.gui.ldlib.NEIntegratedWorkingStationLDLibUI;
import cn.dancingsnow.neoecoae.gui.ldlib.NEStorageControllerLDLibUI;
import cn.dancingsnow.neoecoae.gui.ldlib.NEStructureTerminalLDLibUI;
import cn.dancingsnow.neoecoae.gui.nativeui.NENativeMenus;
import net.minecraft.client.gui.screens.MenuScreens;
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

        MenuScreens.register(NENativeMenus.STORAGE_CONTROLLER.get(), NEStorageControllerLDLibUI::new);
        MenuScreens.register(NENativeMenus.COMPUTATION_CONTROLLER.get(), NEComputationControllerLDLibUI::new);
        MenuScreens.register(NENativeMenus.CRAFTING_CONTROLLER.get(), NECraftingControllerLDLibUI::new);
        MenuScreens.register(NENativeMenus.INTEGRATED_WORKING_STATION.get(), NEIntegratedWorkingStationLDLibUI::new);
        MenuScreens.register(NENativeMenus.CRAFTING_PATTERN_BUS.get(), NECraftingPatternBusLDLibUI::new);
        MenuScreens.register(NENativeMenus.FLUID_HATCH.get(), NEFluidHatchLDLibUI::new);
        MenuScreens.register(NENativeMenus.STRUCTURE_TERMINAL.get(), NEStructureTerminalLDLibUI::new);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(NEBlockEntities.COMPUTATION_DRIVE.get(), ECOComputationDriveRenderer::new);
        event.registerBlockEntityRenderer(NEBlockEntities.ECO_DRIVE.get(), ECODriveRenderer::new);
    }
}
