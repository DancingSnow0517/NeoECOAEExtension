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
    static final int SMALL_WIDTH = 7, THIN_WIDTH = 6;
    private final Scrollbar scrollbar;
    private final IntConsumer onScroll;
    private final boolean thin;
    private int published, max;
    private boolean pressed;

    ComputationScrollbar(UIElement owner, int x, int y, int height, IntConsumer onScroll) {
        this(owner, x, y, height, Scrollbar.BIG, WIDTH, onScroll);
    }

    ComputationScrollbar(UIElement owner, int x, int y, int height, Scrollbar.Style style, int width,
            IntConsumer onScroll) {
        this(owner, x, y, height, style, width, false, onScroll);
    }

    /** A 6-pixel artwork track: AE2 keeps the input rules while the thumb is drawn inside the 4-pixel interior. */
    static ComputationScrollbar thin(UIElement owner, int x, int y, int height, IntConsumer onScroll) {
        return new ComputationScrollbar(owner, x, y, height, Scrollbar.SMALL, THIN_WIDTH, true, onScroll);
    }

    private ComputationScrollbar(UIElement owner, int x, int y, int height, Scrollbar.Style style, int width,
            boolean thin, IntConsumer onScroll) {
        scrollbar = new Scrollbar(style);
        this.onScroll = onScroll;
        this.thin = thin;
        HostElements.absolute(this, x, y, width, height);
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

    void resize(int height) {
        layout(layout -> layout.height(height));
        scrollbar.setHeight(height);
    }

    void update(int value, int max, int pageSize) {
        this.max = Math.max(0, max);
        scrollbar.setRange(0, this.max, pageSize);
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
        if (thin) {
            drawThin(context);
            return;
        }
        scrollbar.drawForegroundLayer(context.graphics, new Rect2i(0, 0, 0, 0),
            new Point(Math.round(context.mouseX), Math.round(context.mouseY)));
    }

    private void drawThin(GUIContext context) {
        int left = Math.round(getPositionX()) + 1, top = Math.round(getPositionY()) + 1;
        int travel = Math.max(0, Math.round(getSizeHeight()) - 2 - THUMB_HEIGHT);
        int thumbY = top + (max <= 0 ? 0 : Math.round(travel * (scrollbar.getCurrentScroll() / (float) max)));
        int body = max <= 0 ? 0xFF6E6A82 : pressed ? 0xFFE6E1F2 : 0xFFC9C3DA;
        context.graphics.fill(left, thumbY, left + THIN_WIDTH - 2, thumbY + THUMB_HEIGHT, 0xFF3F3D52);
        context.graphics.fill(left, thumbY + 1, left + THIN_WIDTH - 2, thumbY + THUMB_HEIGHT - 1, body);
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
