package cn.dancingsnow.neoecoae.gui.computation;

import appeng.client.Point;
import appeng.client.gui.widgets.Scrollbar;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import java.util.function.IntConsumer;
import net.minecraft.client.renderer.Rect2i;

/** Adapts AE2's native scrollbar, including track paging and held-click repeats, to LDLib events. */
final class ComputationScrollbar extends UIElement {
    static final int WIDTH = 12, THUMB_HEIGHT = 15;
    private final Scrollbar scrollbar = new Scrollbar(Scrollbar.BIG);
    private final IntConsumer onScroll;
    private int published;
    private boolean pressed;

    ComputationScrollbar(UIElement owner, int x, int y, int height, IntConsumer onScroll) {
        this.onScroll = onScroll;
        HostElements.absolute(this, x, y, WIDTH, height);
        scrollbar.setHeight(height).setCaptureMouseWheel(false);
        addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button != 0) return;
            position();
            pressed = scrollbar.onMouseDown(mouse(event), event.button);
            publish();
            event.stopImmediatePropagation();
        });
        owner.addEventListener(UIEvents.MOUSE_MOVE, event -> {
            if (pressed && scrollbar.onMouseDrag(mouse(event), 0)) {
                publish();
                event.stopImmediatePropagation();
            }
        }, true);
        owner.addEventListener(UIEvents.MOUSE_UP, event -> {
            if (event.button == 0 && pressed) {
                scrollbar.onMouseUp(mouse(event), event.button);
                pressed = false;
                event.stopImmediatePropagation();
            }
        }, true);
        addEventListener(UIEvents.TICK, event -> {
            // LDLib dispatches mouse-up only to the hovered element. Release outside the panel too.
            if (pressed && getModularUI() != null && getModularUI().getLastMouseDownButton() != 0) release();
            scrollbar.tick();
            publish();
        });
    }

    void update(int value, int max, int pageSize) {
        scrollbar.setRange(0, Math.max(0, max), pageSize);
        scrollbar.setCurrentScroll(value);
        published = scrollbar.getCurrentScroll();
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        position();
        if (pressed) {
            if (getModularUI() != null && getModularUI().getLastMouseDownButton() != 0) {
                release();
            } else if (scrollbar.onMouseDrag(new Point(Math.round(context.mouseX), Math.round(context.mouseY)), 0)) {
                publish();
            }
        }
        // LDLib backgrounds are buffered while vanilla sprites draw immediately.
        context.graphics.flush();
        scrollbar.drawForegroundLayer(context.graphics, new Rect2i(0, 0, 0, 0),
            new Point(Math.round(context.mouseX), Math.round(context.mouseY)));
    }

    private void position() {
        scrollbar.setPosition(new Point(Math.round(getPositionX()), Math.round(getPositionY())));
    }

    private void release() {
        scrollbar.onMouseUp(new Point(0, 0), 0);
        pressed = false;
    }

    private void publish() {
        int value = scrollbar.getCurrentScroll();
        if (value != published) {
            published = value;
            onScroll.accept(value);
        }
    }

    private static Point mouse(UIEvent event) {
        return new Point(Math.round(event.x), Math.round(event.y));
    }
}
