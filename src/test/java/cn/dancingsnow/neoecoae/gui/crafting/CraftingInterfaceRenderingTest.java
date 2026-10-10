package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
        for (var slot : slots) verify(slot).drawInBackground(context);
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
}
