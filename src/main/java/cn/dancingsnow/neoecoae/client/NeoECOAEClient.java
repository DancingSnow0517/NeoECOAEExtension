package cn.dancingsnow.neoecoae.client;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlockEntities;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.ECOComputationModels;
import cn.dancingsnow.neoecoae.api.rendering.FixedBlockEntityRenderers;
import cn.dancingsnow.neoecoae.client.all.NEExtraModels;
import cn.dancingsnow.neoecoae.client.renderer.blockentity.ECOComputationDriveRenderer;
import cn.dancingsnow.neoecoae.client.renderer.blockentity.ECODriveRenderer;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingInterfaceUI;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.lowdragmc.lowdraglib2.editor.resource.EditorResourceEvent;
import com.lowdragmc.lowdraglib2.editor.resource.ResourceInstance;
import com.lowdragmc.lowdraglib2.editor.resource.TexturesResource;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
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
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolder;

@Mod(value = NeoECOAE.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid =  NeoECOAE.MOD_ID, value = Dist.CLIENT)
public class NeoECOAEClient {
    public NeoECOAEClient(IEventBus modBus, ModContainer container) {
        NEExtraModels.register();
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        NeoECOAE.getIntegrationManager().loadAllClientIntegrations();
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

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        Item[] cells = BuiltInRegistries.ITEM.stream()
            .filter(item -> item instanceof ECOStorageCellItem)
            .toArray(Item[]::new);
        event.register(NEItemColors::getCellColor, cells);
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> screen
                && screen.getMenu() instanceof IModularUIHolder holder
                && holder.getModularUI() != null) {
            CraftingInterfaceUI.attachNativeSearchFields(event, holder.getModularUI());
        }
    }

    @SubscribeEvent
    public static void onScreenMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        CraftingInterfaceUI.prepareSearchFields(event.getScreen());
    }

    @SubscribeEvent
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (CraftingInterfaceUI.handleSearchKeyPressed(
                event.getScreen(), event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (CraftingInterfaceUI.handleSearchCharTyped(
                event.getScreen(), event.getCodePoint(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenMouseClickedPost(ScreenEvent.MouseButtonPressed.Post event) {
        CraftingInterfaceUI.reclaimSearchFieldFocus(event.getScreen());
    }

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public static void onLoadBuiltinEditorResource(EditorResourceEvent.LoadBuiltin event) {
        if (event.resourceInstance.resource == TexturesResource.INSTANCE) {
            NETextures.init((ResourceInstance<IGuiTexture>) event.resourceInstance);
        }
    }
}
