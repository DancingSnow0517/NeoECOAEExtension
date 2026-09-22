package cn.dancingsnow.neoecoae.client;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlockEntities;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.ECOComputationModels;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCellItem;
import cn.dancingsnow.neoecoae.client.all.NEExtraModels;
import cn.dancingsnow.neoecoae.client.rendering.FixedBlockEntityRenderers;
import cn.dancingsnow.neoecoae.client.renderer.blockentity.ECOComputationDriveRenderer;
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
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import appeng.menu.implementations.CellWorkbenchMenu;
import appeng.client.gui.Icon;
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
        NEExtraModels.register();
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
                var keys = JeiBookmarkAccess.itemBookmarks().stream()
                    .map(stack -> appeng.api.stacks.AEItemKey.of(stack))
                    .filter(java.util.Objects::nonNull).map(key -> (appeng.api.stacks.AEKey) key).toList();
                PacketDistributor.sendToServer(new ECOImportJeiBookmarksC2SPacket(menu.containerId, keys));
            });
        importButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.translatable("gui.neoecoae.import_jei_bookmarks.tooltip")));
        return importButton;
    }

    /** Vanilla-screen counterpart of the AE2 toolbar buttons used by the side rails. */
    private static final class JeiBookmarkButton extends Button {
        private static final net.minecraft.resources.ResourceLocation ICON_TEXTURE =
            NeoECOAE.id("textures/gui/upload.png");

        private JeiBookmarkButton(Component message, OnPress onPress) {
            super(0, 0, 16, 16, message, onPress, Button.DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int yOffset = isHovered() ? 1 : 0;
            Icon background = isHovered()
                ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER
                : isFocused() ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS
                : Icon.TOOLBAR_BUTTON_BACKGROUND;
            background.getBlitter()
                .dest(getX() - 1, getY(), 18, 20)
                .zOffset(100)
                .blit(graphics);

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
        // RenderSection reuses a mutable origin; snapshot it before the async rebuild runs.
        BlockPos sectionOrigin = event.getSectionOrigin().immutable();
        event.addRenderer(context -> FixedBlockEntityRenderers.render(context, sectionOrigin));
    }

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        // Covers every ECO storage matrix icon that carries a status-light layer: the ECO item/fluid/
        // chemical/FE/mana/source families, and the Omni, MEGA and Lightning matrices built on the
        // eco_cell_compat housing set. The tint index is ignored by models that have no status-light
        // layer, so registering an extra item is harmless.
        Item[] cells = BuiltInRegistries.ITEM.stream()
            .filter(item -> item instanceof IECOStorageCellItem)
            .toArray(Item[]::new);
        event.register(NEItemColors::getCellColor, cells);
    }

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public static void onLoadBuiltinEditorResource(EditorResourceEvent.LoadBuiltin event) {
        if (event.resourceInstance.resource == TexturesResource.INSTANCE) {
            NETextures.init((ResourceInstance<IGuiTexture>) event.resourceInstance);
        }
    }
}
