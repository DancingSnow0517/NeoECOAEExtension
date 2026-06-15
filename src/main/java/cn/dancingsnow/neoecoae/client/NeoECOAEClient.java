package cn.dancingsnow.neoecoae.client;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.client.ECOCellModels;
import cn.dancingsnow.neoecoae.api.client.ECOComputationModels;
import cn.dancingsnow.neoecoae.client.item.ECOStorageCellStateTintSource;
import cn.dancingsnow.neoecoae.client.model.ECOComputationDriveModel;
import cn.dancingsnow.neoecoae.client.model.ECODriveModel;
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
import net.neoforged.neoforge.client.event.InitializeClientRegistriesEvent;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = NeoECOAE.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid =  NeoECOAE.MOD_ID, value = Dist.CLIENT)
public class NeoECOAEClient {
    public NeoECOAEClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        NeoForge.EVENT_BUS.addListener(NEClientRecipe::receivedRecipe);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        NeoECOAE.getIntegrationManager().loadAllClientIntegrations();
    }

    @SubscribeEvent
    public static void onAddChunkGeometry(AddSectionGeometryEvent event) {
        event.addRenderer(context -> {});
    }

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public static void onLoadBuiltinEditorResource(EditorResourceEvent.LoadBuiltin event) {
        if (event.resourceInstance.resource == TexturesResource.INSTANCE) {
            NETextures.init((ResourceInstance<IGuiTexture>) event.resourceInstance);
        }
    }

    @SubscribeEvent
    public static void registerItemTintSource(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(ECOStorageCellStateTintSource.ID, ECOStorageCellStateTintSource.MAP_CODEC);
    }

    @SubscribeEvent
    public static void registerBlockStateModels(RegisterBlockStateModels event) {
        event.registerModel(ECODriveModel.Unbaked.ID, ECODriveModel.Unbaked.MAP_CODEC);
        event.registerModel(ECOComputationDriveModel.Unbaked.ID, ECOComputationDriveModel.Unbaked.MAP_CODEC);
    }

    @SubscribeEvent
    public static void registerCustomClientRegistries(InitializeClientRegistriesEvent event) {
        ECOCellModels.register(NEItems.ECO_ITEM_CELL_16M, NeoECOAE.id("block/cell/storage_cell_l4_item"));
        ECOCellModels.register(NEItems.ECO_ITEM_CELL_64M, NeoECOAE.id("block/cell/storage_cell_l6_item"));
        ECOCellModels.register(NEItems.ECO_ITEM_CELL_256M, NeoECOAE.id("block/cell/storage_cell_l9_item"));

        ECOCellModels.register(NEItems.ECO_FLUID_CELL_16M, NeoECOAE.id("block/cell/storage_cell_l4_fluid"));
        ECOCellModels.register(NEItems.ECO_FLUID_CELL_64M, NeoECOAE.id("block/cell/storage_cell_l6_fluid"));
        ECOCellModels.register(NEItems.ECO_FLUID_CELL_256M, NeoECOAE.id("block/cell/storage_cell_l9_fluid"));

        ECOComputationModels.registerCellModel(NEItems.ECO_COMPUTATION_CELL_L4, NeoECOAE.id("block/compute/cell_l4"), NeoECOAE.id("block/compute/cell_l4_formed"));
        ECOComputationModels.registerCellModel(NEItems.ECO_COMPUTATION_CELL_L6, NeoECOAE.id("block/compute/cell_l6"), NeoECOAE.id("block/compute/cell_l6_formed"));
        ECOComputationModels.registerCellModel(NEItems.ECO_COMPUTATION_CELL_L9, NeoECOAE.id("block/compute/cell_l9"), NeoECOAE.id("block/compute/cell_l9_formed"));

        ECOComputationModels.registerCableModel(ECOTier.L4, NeoECOAE.id("block/compute/cable_l4_dis"), NeoECOAE.id("block/compute/cable_l4"));
        ECOComputationModels.registerCableModel(ECOTier.L6, NeoECOAE.id("block/compute/cable_l6_dis"), NeoECOAE.id("block/compute/cable_l6"));
        ECOComputationModels.registerCableModel(ECOTier.L9, NeoECOAE.id("block/compute/cable_l9_dis"), NeoECOAE.id("block/compute/cable_l9"));
    }
}
