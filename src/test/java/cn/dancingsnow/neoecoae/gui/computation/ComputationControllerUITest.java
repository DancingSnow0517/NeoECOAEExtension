package cn.dancingsnow.neoecoae.gui.computation;

import appeng.api.config.CpuSelectionMode;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ComputationControllerUITest {
    @BeforeAll
    static void bootstrap() throws ClassNotFoundException {
        InventoryTestBootstrap.initialize();
        try (var mods = mockStatic(ModList.class)) {
            ModList modList = mock(ModList.class);
            when(modList.getAllScanData()).thenReturn(List.of());
            mods.when(ModList::get).thenReturn(modList);
            // NeoECOAE's static initialiser (used by theme icons) needs a ModList while the class loads.
            Class.forName(NeoECOAE.class.getName());
            AccessorRegistries.init();
        }
    }

    @Test
    void loadsLayoutWithArtworkSwitchesInventoryAndToolbar() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            UIElement root = ComputationControllerUI.create(
                config(mock(Runnable.class), mock(Runnable.class), mock(Runnable.class)),
                () -> ComputationControllerPanelState.UNFORMED, new UIElement(), new UIElement(), new UIElement());

            assertNotNull(find(root, "computation_artwork"));
            assertNotNull(find(root, "computation_switch_substitutions"));
            assertNotNull(find(root, "computation_switch_cycle_planning"));
            assertNotNull(find(root, "computation_switch_fast_planner"));
            assertInstanceOf(InventorySlots.class, find(root, "computation_player_inventory"));
            assertNotNull(find(root, "computation_toolbar"));
            assertNotNull(find(root, "computation_drive_grid"));
            assertNotNull(find(root, "computation_main"));
            assertNotNull(find(root, "computation_cpu_selector"));
            assertNotNull(find(root, "computation-info"));
            assertNotNull(find(root, "computation-cpu-list-toggle"));
            assertNotNull(find(root, "computation_status"));
        }
    }

    @Test
    void driveGridHasOneSlotPerDriveWithoutDrives() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            UIElement root = ComputationControllerUI.create(
                config(mock(Runnable.class), mock(Runnable.class), mock(Runnable.class)),
                () -> ComputationControllerPanelState.UNFORMED, new UIElement(), new UIElement(), new UIElement());

            UIElement grid = find(root, "computation_drive_grid");
            assertNotNull(grid);
            // One BindableValue child carries the synced snapshot and is not a slot.
            assertEquals(22, grid.getChildren().stream().filter(ItemSlot.class::isInstance).count());
        }
    }

    @Test
    void switchesRunTheirServerActions() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            Runnable substitutions = mock(Runnable.class);
            Runnable cyclePlanning = mock(Runnable.class);
            Runnable fastPlanner = mock(Runnable.class);
            UIElement root = ComputationControllerUI.create(config(substitutions, cyclePlanning, fastPlanner),
                () -> ComputationControllerPanelState.UNFORMED, new UIElement(), new UIElement(), new UIElement());

            serverClick(find(root, "computation_switch_substitutions"), 0);
            verify(substitutions).run();
            verifyNoMoreInteractions(cyclePlanning, fastPlanner);

            serverClick(find(root, "computation_switch_cycle_planning"), 0);
            verify(cyclePlanning).run();
            verifyNoMoreInteractions(substitutions, fastPlanner);

            serverClick(find(root, "computation_switch_fast_planner"), 0);
            verify(fastPlanner).run();
            verifyNoMoreInteractions(substitutions, cyclePlanning);
        }
    }

    @Test
    void switchTooltipListsNameStateDescriptionAndCount() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            UIElement root = ComputationControllerUI.create(
                config(mock(Runnable.class), mock(Runnable.class), mock(Runnable.class)),
                () -> ComputationControllerPanelState.UNFORMED, new UIElement(), new UIElement(), new UIElement());

            UIElement substitutions = find(root, "computation_switch_substitutions");
            assertNotNull(substitutions);
            UIEvent tooltip = UIEvent.create(UIEvents.HOVER_TOOLTIPS);
            tooltip.target = substitutions;
            UIEventDispatcher.dispatchEvent(tooltip, false, false, false);
            assertNotNull(tooltip.hoverTooltips);
            assertEquals(4, tooltip.hoverTooltips.tooltipTexts().size());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void allThreeGaugesOpenToTheirActualRatiosAndKeepFollowingSyncedValues() throws ReflectiveOperationException {
        AtomicLong now = new AtomicLong();
        try (var ldlib = mockStatic(LDLib2.class); var minecraft = mockStatic(Minecraft.class);
             var clock = mockStatic(Util.class, CALLS_REAL_METHODS)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            clock.when(Util::getMillis).thenAnswer(call -> now.get());
            Minecraft client = mock(Minecraft.class);
            var fontField = Minecraft.class.getDeclaredField("font");
            fontField.setAccessible(true);
            fontField.set(client, mock(Font.class));
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            UIElement root = ComputationControllerUI.create(config(() -> {}, () -> {}, () -> {}),
                () -> new ComputationControllerPanelState(true, 1, 0, 0, 0, 0),
                new UIElement(), new UIElement(), new UIElement());
            UIElement artwork = find(root, "computation_artwork");
            BindableValue<Tag> synced = (BindableValue<Tag>) artwork.getChildren().getLast();
            CompoundTag update = ((CompoundTag) synced.getValue()).copy();
            update.putLong("usedBytes", 512);
            update.putInt("usedThreads", 2);
            synced.setValue(update);
            GUIContext context = mock(GUIContext.class);
            context.graphics = mock(GuiGraphics.class);
            PoseStack pose = new PoseStack();
            when(context.graphics.pose()).thenReturn(pose);

            artwork.drawBackgroundAdditional(context);
            verify(context, never()).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.y >= 154 && sprite.spritePosition.y <= 168),
                anyFloat(), anyFloat(), anyFloat(), eq(7F));
            now.set(250);
            artwork.drawBackgroundAdditional(context);
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.y == 154), eq(83F), eq(131F), eq(11F), eq(7F));
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.y == 161), eq(140F), eq(131F), eq(6F), eq(7F));
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.y == 168), eq(197F), eq(131F), eq(6F), eq(7F));

            clearInvocations(context);
            now.set(500);
            artwork.drawBackgroundAdditional(context);
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.y == 154), eq(83F), eq(131F), eq(22F), eq(7F));
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.y == 161), eq(140F), eq(131F), eq(11F), eq(7F));
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.y == 168), eq(197F), eq(131F), eq(11F), eq(7F));

            clearInvocations(context);
            update = update.copy();
            update.putLong("usedBytes", 256);
            synced.setValue(update);
            artwork.drawBackgroundAdditional(context);
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.y == 154), eq(83F), eq(131F), eq(11F), eq(7F));
        }
    }

    private static ComputationControllerUI.Config config(Runnable toggleSubstitutions, Runnable toggleCycle,
                                                         Runnable toggleFast) {
        return new ComputationControllerUI.Config(
            () -> true, () -> 1, () -> 0, () -> 0,
            () -> 0L, () -> 1024L, () -> 1024L,
            () -> 0, () -> 8, () -> 4, () -> 16,
            () -> CpuSelectionMode.ANY, mock(IntConsumer.class),
            () -> false, () -> 3, toggleSubstitutions,
            () -> true, toggleCycle,
            () -> true, toggleFast,
            () -> 1, mock(IntConsumer.class),
            () -> ItemStack.EMPTY, List::of, List::of, null,
            (position, expected, toInventory) -> false, (position, expected) -> false,
            () -> 0L, () -> true, List::of, cpu -> {}, cpu -> {},
            () -> Component.empty());
    }

    @Test
    void placedHostArtworkOverridesBadgesMarkersStatusAndSwitchesAndRefreshesWhileOpen() throws Exception {
        try (var ldlib = mockStatic(LDLib2.class); var minecraft = mockStatic(Minecraft.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            Minecraft client = mock(Minecraft.class);
            var fontField = Minecraft.class.getDeclaredField("font");
            fontField.setAccessible(true);
            fontField.set(client, mock(Font.class));
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            var block = mock(net.minecraft.world.level.block.Block.class,
                withSettings().extraInterfaces(cn.dancingsnow.neoecoae.api.IHostPanelTextureProvider.class));
            var placed = mock(net.minecraft.world.level.block.state.BlockState.class);
            when(placed.getBlock()).thenReturn(block);
            var provider = (cn.dancingsnow.neoecoae.api.IHostPanelTextureProvider) block;
            when(provider.getPanelTexture(same(placed), any())).thenReturn(net.minecraft.resources.ResourceLocation.parse("addon:badge.png"));
            when(provider.getPanelTexture(same(placed), any(), anyInt(), anyBoolean(), anyBoolean()))
                .thenAnswer(call -> net.minecraft.resources.ResourceLocation.parse("addon:state_" + call.getArgument(2) + "_"
                    + call.getArgument(3) + "_" + call.getArgument(4) + ".png"));
            Runnable toggle = mock(Runnable.class);
            var tier = new java.util.concurrent.atomic.AtomicReference<cn.dancingsnow.neoecoae.api.IECOTier>(cn.dancingsnow.neoecoae.api.ECOTier.L4);
            UIElement root = ComputationControllerUI.create(config(toggle, () -> {}, () -> {}),
                () -> new ComputationControllerPanelState(true, 1, 0, 0, 0, 0),
                new UIElement(), new UIElement(), new UIElement(), tier::get, () -> placed);
            UIElement artwork = find(root, "computation_artwork");
            GUIContext context = mock(GUIContext.class);
            context.graphics = mock(GuiGraphics.class);
            when(context.graphics.pose()).thenReturn(new PoseStack());
            artwork.drawBackgroundAdditional(context);
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.getImageLocation().toString().equals("addon:badge.png")), eq(23F), eq(5F), eq(13F), eq(18F));
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.getImageLocation().toString().equals("addon:badge.png") && sprite.spritePosition.y == 8),
                eq(10F), eq(64F), eq(14F), eq(8F));
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.getImageLocation().toString().equals("addon:state_0_false_false.png")),
                eq(180F), eq(153F), eq(12F), eq(12F));
            for (int index = 0; index < 3; index++) {
                var button = (com.lowdragmc.lowdraglib2.gui.ui.elements.Button) find(root, "computation_switch_"
                    + List.of("substitutions", "cycle_planning", "fast_planner").get(index));
                button.getStyleBag().compute(0);
                assertEquals("addon:state_" + index + "_true_false.png", ((SpriteTexture) button.getButtonStyle().baseTexture()).getImageLocation().toString());
                assertEquals("addon:state_" + index + "_true_true.png", ((SpriteTexture) button.getButtonStyle().hoverTexture()).getImageLocation().toString());
                assertEquals(15, ((SpriteTexture) button.getButtonStyle().baseTexture()).spriteSize.width);
                assertEquals(26, ((SpriteTexture) button.getButtonStyle().baseTexture()).spriteSize.height);
            }
            serverClick(find(root, "computation_switch_substitutions"), 0);
            verify(toggle).run();
            var textures = (cn.dancingsnow.neoecoae.gui.common.HostPanelTextureSync) root.getChildren().getLast();
            textures.setValue(new CompoundTag());
            var button = (com.lowdragmc.lowdraglib2.gui.ui.elements.Button) find(root, "computation_switch_substitutions");
            button.getStyleBag().compute(1);
            assertEquals(ComputationControllerTextures.WIDGETS_ATLAS, ((SpriteTexture) button.getButtonStyle().baseTexture()).getImageLocation());
            clearInvocations(context);
            artwork.drawBackgroundAdditional(context);
            verify(context, never()).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.getImageLocation().getNamespace().equals("addon")), anyFloat(), anyFloat(), anyFloat(), anyFloat());
            var addonTier = mock(cn.dancingsnow.neoecoae.api.IECOTier.class);
            when(addonTier.getPanelTierName()).thenReturn(Component.literal("L1"));
            when(addonTier.getPanelAtlas(anyString())).thenAnswer(call -> net.minecraft.resources.ResourceLocation.parse(
                "addon:" + ((String) call.getArgument(0)).toLowerCase(java.util.Locale.ROOT) + ".png"));
            tier.set(addonTier);
            clearInvocations(context);
            artwork.drawBackgroundAdditional(context);
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.x == 36 && sprite.spritePosition.y == 5 && sprite.spriteSize.width == 1
                && net.minecraft.resources.ResourceLocation.parse("addon:computation-background_atlas.png")
                .equals(cn.dancingsnow.neoecoae.gui.theme.PanelAtlasTestSupport.image(texture))), eq(23F), eq(5F), eq(13F), eq(18F));
            verify(context).drawTexture(argThat(texture -> net.minecraft.resources.ResourceLocation.parse("addon:computation-widgets_atlas.png")
                .equals(cn.dancingsnow.neoecoae.gui.theme.PanelAtlasTestSupport.image(texture))), eq(180F), eq(153F), eq(12F), eq(12F));
            assertEquals(net.minecraft.resources.ResourceLocation.parse("addon:computation-widgets_atlas.png"),
                cn.dancingsnow.neoecoae.gui.theme.PanelAtlasTestSupport.image(button.getButtonStyle().baseTexture()));
            verify(block, never()).defaultBlockState();
        }
    }

    private static UIElement find(UIElement element, String id) {
        return element.selectId(id).findFirst().orElse(null);
    }

    private static void serverClick(UIElement element, int button) {
        assertNotNull(element);
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.button = button;
        element.getBaubleServerEvent(UIEvents.MOUSE_DOWN).executor().apply(new Object[] {event});
    }
}
