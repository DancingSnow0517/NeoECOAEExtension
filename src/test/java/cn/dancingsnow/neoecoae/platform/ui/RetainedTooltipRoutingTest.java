package cn.dancingsnow.neoecoae.platform.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.HoverTooltips;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.utils.ModularUITooltipComponent;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.util.UiTestSupport;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RetainedTooltipRoutingTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void imageOnlyTooltipsReachForgeWithoutAnEmptyHeaderAndDisappearOutsideTheRegion() {
        try (var mocked = mockStatic(Minecraft.class)) {
            var client = mock(Minecraft.class);
            var font = mock(Font.class);
            UiTestSupport.set(client, "font", font);
            mocked.when(Minecraft::getInstance).thenReturn(client);
            var root = new UIElement().layout(layout -> layout.width(20).height(20));
            root.arrange();
            var content = new ModularUITooltipComponent(new UIElement());
            root.addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
                event.hoverTooltips = new HoverTooltips(List.of(), content, null, null));
            var graphics = mock(GuiGraphics.class);
            when(graphics.pose()).thenReturn(new PoseStack());
            root.drawInForeground(graphics, 9, 9, 0);
            verify(graphics).renderComponentTooltipFromElements(eq(font), argThat(elements ->
                elements.size() == 1 && elements.get(0).right().orElseThrow() == content), eq(9), eq(9), eq(ItemStack.EMPTY));
            verify(graphics, never()).renderTooltip(eq(font), anyList(), any(java.util.Optional.class), anyInt(), anyInt());

            clearInvocations(graphics);
            root.drawInForeground(graphics, 30, 30, 0);
            verify(graphics, never()).renderComponentTooltipFromElements(any(), anyList(), anyInt(), anyInt(), any());
        }
    }
}
