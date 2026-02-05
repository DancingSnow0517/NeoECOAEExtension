package cn.dancingsnow.neoecoae.client;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlockEntities;
import cn.dancingsnow.neoecoae.api.ECOComputationModels;
import cn.dancingsnow.neoecoae.api.rendering.FixedBlockEntityRenderers;
import cn.dancingsnow.neoecoae.client.all.NEExtraModels;
import cn.dancingsnow.neoecoae.client.renderer.blockentity.ECOComputationDriveRenderer;
import cn.dancingsnow.neoecoae.gui.NETextures;
import com.lowdragmc.lowdraglib2.editor.resource.EditorResourceEvent;
import com.lowdragmc.lowdraglib2.editor.resource.ResourceInstance;
import com.lowdragmc.lowdraglib2.editor.resource.TexturesResource;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = NeoECOAE.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid =  NeoECOAE.MOD_ID, value = Dist.CLIENT)
public class NeoECOAEClient {
    public NeoECOAEClient(IEventBus modBus, ModContainer container) {
        NEExtraModels.register();
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        NeoECOAE.getIntegrationManager().loadAllClientIntegrations();
        ECOComputationModels.runDeferredRegistration();
        FixedBlockEntityRenderers.register(
            NEBlockEntities.COMPUTATION_DRIVE.get(),
            new ECOComputationDriveRenderer()
        );
//        FixedBlockEntityRenderers.register(
//            NEBlockEntities.COMPUTATION_COOLING_CONTROLLER_L4.get(),
//            new ECOComputationCoolingControllerRenderer()
//        );
//
//        FixedBlockEntityRenderers.register(
//            NEBlockEntities.COMPUTATION_COOLING_CONTROLLER_L6.get(),
//            new ECOComputationCoolingControllerRenderer()
//        );
//
//        FixedBlockEntityRenderers.register(
//            NEBlockEntities.COMPUTATION_COOLING_CONTROLLER_L9.get(),
//            new ECOComputationCoolingControllerRenderer()
//        );
    }

    @SubscribeEvent
    public static void onAddChunkGeometry(AddSectionGeometryEvent event) {
        event.addRenderer(c -> FixedBlockEntityRenderers.render(c, event.getSectionOrigin()));
    }

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public static void onLoadBuiltinEditorResource(EditorResourceEvent.LoadBuiltin event) {
        if (event.resourceInstance.resource == TexturesResource.INSTANCE) {
            NETextures.init((ResourceInstance<IGuiTexture>) event.resourceInstance);
        }
    }
}
