package cn.dancingsnow.neoecoae.client;

import cn.dancingsnow.neoecoae.NeoECOAE;
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
import cn.dancingsnow.neoecoae.platform.ui.runtime.editor.resource.ResourceInstance;
import cn.dancingsnow.neoecoae.platform.ui.runtime.editor.resource.TexturesResource;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.ScreenEvent;
import appeng.menu.implementations.CellWorkbenchMenu;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import cn.dancingsnow.neoecoae.integration.jei.JeiBookmarkAccess;
import cn.dancingsnow.neoecoae.network.ECOImportJeiBookmarksC2SPacket;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import cn.dancingsnow.neoecoae.network.ECONetwork;

@EventBusSubscriber(modid =  NeoECOAE.MOD_ID, value = Dist.CLIENT)
public class NeoECOAEClient {
    public static void initialize(IEventBus modBus) {
        if (!net.minecraftforge.fml.loading.FMLEnvironment.production && Boolean.getBoolean("neoecoae.clientSmoke")) {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                cn.dancingsnow.neoecoae.client.diagnostics.ClientStartupProbe::tick);
        }
        NEExtraModels.register();
        modBus.addListener(NeoECOAEClient::onClientSetup);
        modBus.addListener((net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) ->
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> {
                cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.SpriteTexture.clearDimensionCache();
                cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.style.StylesheetManager.INSTANCE.setResourceOpener(manager::open);
            }));
        modBus.addListener(NeoECOAEClient::onRegisterItemColors);
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        NETextures.init(TexturesResource.INSTANCE);
        event.enqueueWork(() -> InitScreens.register(
            LargeIntegratedWorkingStationPatternProviderMenu.TYPE,
            LargeIntegratedWorkingStationPatternProviderScreen::new,
            "/screens/large_integrated_working_station_interface.json"));
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
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
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
                ECONetwork.sendToServer(new ECOImportJeiBookmarksC2SPacket(menu.containerId, keys));
            });
        importButton.active = JeiBookmarkAccess.isAvailable();
        importButton.visible = importButton.active;
        importButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.translatable("gui.neoecoae.import_jei_bookmarks.tooltip")));
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

    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        // Covers every ECO storage matrix icon that carries a status-light layer: the ECO item/fluid/
        // chemical/FE/mana/source families, plus Omni, MEGA and Lightning matrices. Standard cells
        // use status-light layer 2; the infinite resource cell uses layer 1. Models without a status-light
        // layer do not use its tint, so registering an extra item is harmless.
        Item[] cells = BuiltInRegistries.ITEM.stream()
            .filter(item -> item instanceof IECOStorageCellItem)
            .toArray(Item[]::new);
        event.register(NEItemColors::getCellColor, cells);
    }

}
