package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;

import static org.mockito.Mockito.*;

public final class HostProgressTestSupport {
    private HostProgressTestSupport() {}

    /** Existing geometry/hover checks inspect the settled frame without a real-time sleep. */
    public static void drawAfterOpening(UIElement element, GUIContext context) {
        try (var clock = mockStatic(Util.class, CALLS_REAL_METHODS)) {
            clock.when(Util::getMillis).thenReturn(0L);
            GUIContext firstFrame = mock(GUIContext.class);
            cn.dancingsnow.neoecoae.util.UiTestSupport.set(firstFrame,"graphics",mock(GuiGraphics.class));
            PoseStack pose = new PoseStack();
            when(firstFrame.graphics.pose()).thenReturn(pose);
            element.drawBackgroundAdditional(firstFrame);
            clock.when(Util::getMillis).thenReturn(500L);
            element.drawBackgroundAdditional(context);
        }
    }
}
