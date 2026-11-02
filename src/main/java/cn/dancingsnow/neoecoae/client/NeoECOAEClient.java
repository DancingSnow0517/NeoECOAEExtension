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
import cn.dancingsnow.neoecoae.all.NEBlockEntities;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.ECOComputationModels;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCellItem;
import cn.dancingsnow.neoecoae.client.all.NEExtraModels;
import cn.dancingsnow.neoecoae.client.gui.crafting.ECOCraftConfirmScreenIntegration;
import cn.dancingsnow.neoecoae.client.gui.workstation.LargeIntegratedWorkingStationPatternProviderScreen;
import cn.dancingsnow.neoecoae.client.rendering.FixedBlockEntityRenderers;
import cn.dancingsnow.neoecoae.client.renderer.blockentity.ECOComputationDriveRenderer;
import cn.dancingsnow.neoecoae.client.renderer.blockentity.ECOComputationCoolingControllerRenderer;
import cn.dancingsnow.neoecoae.client.renderer.blockentity.ECODriveRenderer;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.menu.LargeIntegratedWorkingStationPatternProviderMenu;
import appeng.init.client.InitScreens;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import com.lowdragmc.lowdraglib2.editor.resource.EditorResourceEvent;
import com.lowdragmc.lowdraglib2.editor.resource.ResourceInstance;
import com.lowdragmc.lowdraglib2.editor.resource.TexturesResource;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
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
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import appeng.menu.implementations.CellWorkbenchMenu;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import cn.dancingsnow.neoecoae.integration.jei.JeiBookmarkAccess;
import cn.dancingsnow.neoecoae.network.ECOImportJeiBookmarksC2SPacket;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

@Mod(value = NeoECOAE.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid =  NeoECOAE.MOD_ID, value = Dist.CLIENT)
public class NeoECOAEClient {
    public NeoECOAEClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        NeoForge.EVENT_BUS.addListener(NEClientRecipe::receivedRecipe);
        NEExtraModels.register();
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager ->
            cn.dancingsnow.neoecoae.client.gui.graph.GraphRenderer.resourcesReloaded());
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        NeoECOAE.getIntegrationManager().loadAllClientIntegrations();
        NEItemColors.clearCache();
        ECOCellModels.runDeferredRegistration();
        ECOComputationModels.runDeferredRegistration();
        FixedBlockEntityRenderers.register(
            NEBlockEntities.COMPUTATION_DRIVE.get(),
            new ECOComputationDriveRenderer()
        );
        FixedBlockEntityRenderers.register(
            NEBlockEntities.ECO_DRIVE.get(),
            new ECODriveRenderer()
        );
        var coolingRenderer = new ECOComputationCoolingControllerRenderer();
        FixedBlockEntityRenderers.register(NEBlockEntities.COMPUTATION_COOLING_CONTROLLER_L4.get(), coolingRenderer);
        FixedBlockEntityRenderers.register(NEBlockEntities.COMPUTATION_COOLING_CONTROLLER_L6.get(), coolingRenderer);
        FixedBlockEntityRenderers.register(NEBlockEntities.COMPUTATION_COOLING_CONTROLLER_L9.get(), coolingRenderer);
    }

    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        cn.dancingsnow.neoecoae.network.MenuDataTransport.retainClientMenu(player == null ? null : player.containerMenu);
    }

    @SubscribeEvent
    public static void onScreenInitPost(ScreenEvent.Init.Post event) {
        ECOCraftConfirmScreenIntegration.onScreenInitPost(event);
    }

    public static Button createJeiBookmarkButton(CellWorkbenchMenu menu) {
        Button importButton = new JeiBookmarkButton(
            Component.translatable("gui.neoecoae.import_jei_bookmarks"), ignored -> {
                if (!JeiBookmarkAccess.isAvailable()) return;
                var keys = JeiBookmarkAccess.bookmarkedKeys();
                PacketDistributor.sendToServer(new ECOImportJeiBookmarksC2SPacket(menu.containerId, keys));
            });
        importButton.active = JeiBookmarkAccess.isAvailable();
        importButton.visible = importButton.active;
        importButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.translatable("gui.neoecoae.import_jei_bookmarks.tooltip")
                .append("\n")
                .append(Component.literal("Neo ECO AE Extension")
                    .withStyle(net.minecraft.ChatFormatting.BLUE, net.minecraft.ChatFormatting.ITALIC))));
        return importButton;
    }

    /** Vanilla-screen counterpart of the AE2 toolbar buttons used by the side rails. */
    private static final class JeiBookmarkButton extends Button {
        private static final net.minecraft.resources.ResourceLocation ICON_TEXTURE =
            NeoECOAE.id("textures/gui/widget/upload.png");

        private JeiBookmarkButton(Component message, OnPress onPress) {
            super(0, 0, 16, 16, message, onPress, Button.DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int yOffset = isHovered() ? 1 : 0;
            ECOIcon background = isHovered()
                ? ECOIcon.TOOLBAR_BUTTON_BACKGROUND_HOVER
                : isFocused() ? ECOIcon.TOOLBAR_BUTTON_BACKGROUND_FOCUS
                : ECOIcon.TOOLBAR_BUTTON_BACKGROUND;
            background.blit(graphics, getX() - 1, getY() + yOffset, 100);

            graphics.blit(ICON_TEXTURE, getX(), getY() + 2 + yOffset,
                200, 0, 0, 16, 16, 16, 16);
        }
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        ECOCraftConfirmScreenIntegration.onScreenRenderPost(event);
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        InitScreens.register(
            event,
            LargeIntegratedWorkingStationPatternProviderMenu.TYPE,
            LargeIntegratedWorkingStationPatternProviderScreen::new,
            "/screens/large_integrated_working_station_interface.json"
        );
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
