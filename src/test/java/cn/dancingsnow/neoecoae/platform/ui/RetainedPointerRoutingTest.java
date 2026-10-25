package cn.dancingsnow.neoecoae.platform.ui;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RetainedPointerRoutingTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void retainedPressKeepsItsDragAndReleaseOutOfNativeWidgets() {
        var root = element();
        var nativeWidget = nativeWidget(root);
        var releases = new AtomicInteger();
        root.addEventListener(UIEvents.MOUSE_DOWN, event -> event.hasHandler = true);
        root.addEventListener(UIEvents.MOUSE_UP, event -> releases.incrementAndGet());

        assertTrue(root.mouseClicked(9, 9, 0));
        root.mouseDragged(10, 9, 0, 1, 0);
        assertTrue(root.mouseReleased(10, 9, 0));

        assertEquals(1, releases.get(), "retained release listeners still finish their gesture");
        verify(nativeWidget, never()).mouseClicked(anyDouble(), anyDouble(), anyInt());
        verify(nativeWidget, never()).mouseDragged(anyDouble(), anyDouble(), anyInt(), anyDouble(), anyDouble());
        verify(nativeWidget, never()).mouseReleased(anyDouble(), anyDouble(), anyInt());
    }

    @Test void capturedPressKeepsItsReleaseOutOfTheChildNativeWidget() {
        var root = element();
        var child = element();
        root.addChild(child);
        var nativeWidget = nativeWidget(child);
        root.addEventListener(UIEvents.MOUSE_DOWN, event -> event.stopImmediatePropagation(), true);

        assertTrue(root.mouseClicked(9, 9, 0));
        assertTrue(root.mouseReleased(9, 9, 0));

        verify(nativeWidget, never()).mouseClicked(anyDouble(), anyDouble(), anyInt());
        verify(nativeWidget, never()).mouseReleased(anyDouble(), anyDouble(), anyInt());
    }

    @Test void nativeInventoryGesturesStillReceivePressDragAndReleaseAfterARetainedGesture() {
        var root = element();
        var nativeWidget = nativeWidget(root);
        root.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 1) event.hasHandler = true;
        });
        assertTrue(root.mouseClicked(9, 9, 1));
        assertTrue(root.mouseReleased(9, 9, 1));
        clearInvocations(nativeWidget);

        assertTrue(root.mouseClicked(9, 9, 0));
        assertTrue(root.mouseDragged(10, 9, 0, 1, 0));
        assertTrue(root.mouseReleased(10, 9, 0));

        verify(nativeWidget).mouseClicked(9, 9, 0);
        verify(nativeWidget).mouseDragged(10, 9, 0, 1, 0);
        verify(nativeWidget).mouseReleased(10, 9, 0);
    }

    private static UIElement element() {
        var element = new UIElement();
        element.layout(layout -> layout.width(18).height(18));
        element.arrange();
        return element;
    }

    private static Widget nativeWidget(UIElement owner) {
        var widget = mock(Widget.class);
        when(widget.isVisible()).thenReturn(true);
        when(widget.isActive()).thenReturn(true);
        when(widget.mouseClicked(anyDouble(), anyDouble(), anyInt())).thenReturn(true);
        when(widget.mouseReleased(anyDouble(), anyDouble(), anyInt())).thenReturn(true);
        when(widget.mouseDragged(anyDouble(), anyDouble(), anyInt(), anyDouble(), anyDouble())).thenReturn(true);
        owner.addWidget(widget);
        return widget;
    }
}
