package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.EnhancedPoseStack;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CraftingInterfaceRenderingTest {
    @BeforeAll static void bootstrap() throws ClassNotFoundException {
        InventoryTestBootstrap.initialize();
        // LDLib discovers widget types from NeoForge's scan data, which the unit runner does not install.
        try (var modList = mockStatic(ModList.class);
             var registrate = mockStatic(NERegistrate.class)) {
            var mods = mock(ModList.class);
            when(mods.getAllScanData()).thenReturn(List.of());
            modList.when(ModList::get).thenReturn(mods);
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            Class.forName("com.lowdragmc.lowdraglib2.gui.ui.UIElement");
            Class.forName("cn.dancingsnow.neoecoae.gui.theme.NETextures");
            AccessorRegistries.init();
        }
    }

    @Test void previewDrawsItsItemChildrenAndRestoresTheClip() {
        var state = mock(CraftingPatternPreviewState.class);
        var slots = new ArrayList<PatternItemSlot>();
        when(state.createSlot(anyInt())).thenAnswer(call -> {
            var slot = spy(new PatternItemSlot());
            // Verify the real parent's traversal without needing a running graphics context for item rendering.
            doNothing().when(slot).drawInBackground(any(GUIContext.class));
            slots.add(slot);
            return slot;
        });
        var preview = CraftingInterfaceUI.patternPreview(state);
        // A running ModularUI computes inline styles before rendering each element.
        preview.getStyleBag().compute(0);
        var context = mock(GUIContext.class);
        context.graphics = mock(GuiGraphics.class);
        context.elementColor = -1;
        preview.drawContents(context);
        assertEquals(54, slots.size());
        for (var slot : slots) {
            verify(slot).drawInBackground(context);
            slot.getStyleBag().compute(0);
            assertSame(IGuiTexture.EMPTY, slot.getStyle().backgroundTexture());
            assertSame(NETextures.PATTERN_MANAGER_SLOT, slot.getSlotStyle().slotOverlay());
            assertFalse(slot.getSlotStyle().showSlotOverlayOnlyEmpty());
        }
        verify(context).enableScissor(anyFloat(), anyFloat(), anyFloat(), anyFloat());
        verify(context).disableScissor();
    }

    @Test void statusTooltipUsesSeparateLinesAndNoNewlineGlyph() {
        var network = Component.literal("网络：已连接");
        var primary = Component.literal("整理完成：回收无效样板 0 个，重复样板 0 个");
        var secondary = Component.literal("背包空间不足，仍有 1 个样板待回收");
        var lines = CraftingInterfaceUI.statusTooltipLines(network, primary, secondary);
        var tooltip = HoverTooltips.empty().append(lines.toArray(Component[]::new));
        assertEquals(List.of(network, primary, secondary), tooltip.tooltipTexts());
        assertTrue(tooltip.tooltipTexts().stream().noneMatch(line -> line.getString().contains("\n")));
        assertEquals(List.of(network, primary), CraftingInterfaceUI.statusTooltipLines(network, primary, Component.empty()));
    }

    @Test void wideNativeScrollbarStaysVisibleWithoutOverflowAndReachesTheTrackBottom() {
        var state = mock(CraftingPatternPreviewState.class);
        var scrollbar = CraftingInterfaceUI.patternPreviewScrollbar(state);
        var context = mock(GUIContext.class);
        context.graphics = mock(GuiGraphics.class);

        // Empty and single-page lists still draw AE2's disabled handle.
        scrollbar.drawBackgroundAdditional(context);
        verify(context.graphics).blitSprite(ResourceLocation.fromNamespaceAndPath("ae2", "big_scroller_disabled"),
                1, 1, 12, 15);

        when(state.getMaxScrollRow()).thenReturn(3);
        when(state.getScrollRow()).thenReturn(3);
        scrollbar.drawBackgroundAdditional(context);
        verify(context.graphics).blitSprite(ResourceLocation.fromNamespaceAndPath("ae2", "big_scroller"),
                1, 92, 12, 15);
    }

    @Test void operationIndicatorDistinguishesServerOperationsAndRendersTheHoverRowWithoutStretching() throws Exception {
        var host = mock(ECOMachineInterfaceBlockEntity.class, CALLS_REAL_METHODS);
        var indicator = spy(new CraftingInterfaceUI.PatternOperationIndicator(host));
        assertEquals(0, indicator.getValue());
        var transfer = ECOMachineInterfaceBlockEntity.class.getDeclaredField("patternTransferInProgress");
        transfer.setAccessible(true);
        transfer.setBoolean(host, true);
        assertEquals(1, new CraftingInterfaceUI.PatternOperationIndicator(host).getValue());
        transfer.setBoolean(host, false);
        var organize = ECOMachineInterfaceBlockEntity.class.getDeclaredField("patternOrganizeInProgress");
        organize.setAccessible(true);
        organize.setBoolean(host, true);
        assertTrue(host.isPatternTransferInProgress(), "the old combined progress flag also includes organizing");
        assertEquals(2, new CraftingInterfaceUI.PatternOperationIndicator(host).getValue());
        organize.setBoolean(host, false);
        assertEquals(0, new CraftingInterfaceUI.PatternOperationIndicator(host).getValue());

        doReturn(301F).when(indicator).getPositionX();
        doReturn(84F).when(indicator).getPositionY();
        var context = mock(GUIContext.class);
        for (int state = 0; state < 3; state++) {
            final int spriteX = 8 + state * 10;
            indicator.setValue(state);
            doReturn(false).when(indicator).isHover();
            indicator.drawBackgroundAdditional(context);
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.x == spriteX && sprite.spritePosition.y == 129),
                eq(301F), eq(84F), eq(10F), eq(12F));
            doReturn(true).when(indicator).isHover();
            indicator.drawBackgroundAdditional(context);
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.x == spriteX && sprite.spritePosition.y == 142),
                eq(301F), eq(85F), eq(10F), eq(11F));
        }
        indicator.setValue(0);
        assertEquals(0, indicator.getValue(), "completion returns the synchronized lamp to green");
    }

    @Test void occupiedPreviewDrawsTheNativeSizeBackdropBeforeItsItem() {
        var state = mock(CraftingPatternPreviewState.class);
        when(state.createSlot(anyInt())).thenAnswer(call -> new PatternItemSlot());
        var preview = CraftingInterfaceUI.patternPreview(state);
        var slot = spy((PatternItemSlot) preview.getChildren().getFirst());
        slot.getStyleBag().compute(0);
        var item = new ItemStack(Items.DIAMOND);
        slot.setValue(item, false);
        doReturn(16F).when(slot).getContentWidth();
        doReturn(16F).when(slot).getContentHeight();
        var context = mock(GUIContext.class);
        context.graphics = mock(GuiGraphics.class);
        context.pose = mock(EnhancedPoseStack.class);
        context.modularUI = mock(ModularUI.class);
        context.elementColor = -1;
        var order = new ArrayList<String>();
        doAnswer(call -> { order.add("backdrop"); return null; }).when(context)
            .drawTexture(NETextures.PATTERN_MANAGER_SLOT, 0F, 0F, 16F, 16F);
        try (var drawer = mockStatic(DrawerHelper.class)) {
            drawer.when(() -> DrawerHelper.drawItemStack(context.graphics, item, 0, 0, -1, null))
                .thenAnswer(call -> { order.add("item"); return null; });
            slot.drawBackgroundAdditional(context);
            assertEquals(List.of("backdrop", "item"), order);
        }
    }
}
