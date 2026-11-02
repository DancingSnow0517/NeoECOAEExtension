package cn.dancingsnow.neoecoae.gui.common;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

import static org.mockito.Mockito.*;

public final class HostProgressTestSupport {
    private HostProgressTestSupport() {}

    /** Existing geometry/hover checks inspect the settled frame without a real-time sleep. */
    public static void drawAfterOpening(UIElement element, GUIContext context) {
        try (var clock = mockStatic(Util.class, CALLS_REAL_METHODS)) {
            clock.when(Util::getMillis).thenReturn(0L);
            GUIContext firstFrame = mock(GUIContext.class);
            firstFrame.graphics = mock(GuiGraphicsExtractor.class);
            Matrix3x2fStack pose = new Matrix3x2fStack(16);
            when(firstFrame.graphics.pose()).thenReturn(pose);
            UiTestRendering.drawBackgroundAdditional(element, firstFrame);
            clock.when(Util::getMillis).thenReturn(500L);
            UiTestRendering.drawBackgroundAdditional(element, context);
        }
    }
}
