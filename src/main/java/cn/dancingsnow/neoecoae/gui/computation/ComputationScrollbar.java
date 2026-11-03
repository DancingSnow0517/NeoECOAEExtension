package cn.dancingsnow.neoecoae.gui.computation;

import appeng.client.Point;
import appeng.client.gui.widgets.Scrollbar;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvent;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import java.util.function.IntConsumer;
import net.minecraft.client.renderer.Rect2i;

/** Adapts AE2's native scrollbar, including track paging and held-click repeats, to LDLib events. */
final class ComputationScrollbar extends UIElement {
    static final int WIDTH = 12, THUMB_HEIGHT = 15;
    static final int SMALL_WIDTH = 7, THIN_WIDTH = 6;
    // AE2's scrollbar is a client class; the dedicated-server copy of this tree only tracks the published value.
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private Scrollbar scrollbar;
    private final IntConsumer onScroll;
    private final boolean thin;
    private int published, max;
    private boolean pressed;

    ComputationScrollbar(UIElement owner, int x, int y, int height, IntConsumer onScroll) {
        this(owner, x, y, height, false, WIDTH, onScroll);
    }

    ComputationScrollbar(UIElement owner, int x, int y, int height, boolean small, int width,
            IntConsumer onScroll) {
        this(owner, x, y, height, small, width, false, onScroll);
    }

    /** A 6-pixel artwork track: AE2 keeps the input rules while the thumb is drawn inside the 4-pixel interior. */
    static ComputationScrollbar thin(UIElement owner, int x, int y, int height, IntConsumer onScroll) {
        return new ComputationScrollbar(owner, x, y, height, true, THIN_WIDTH, true, onScroll);
    }

    private ComputationScrollbar(UIElement owner, int x, int y, int height, boolean small, int width,
            boolean thin, IntConsumer onScroll) {
        this.onScroll = onScroll;
        this.thin = thin;
        HostElements.absolute(this, x, y, width, height);
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            initializeClient(owner, height, small);
        }
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private void initializeClient(UIElement owner, int height, boolean small) {
        scrollbar = new Scrollbar(clientStyle(small));
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
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            resizeClient(height);
        }
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private void resizeClient(int height) {
        scrollbar.setHeight(height);
    }

    void update(int value, int max, int pageSize) {
        this.max = Math.max(0, max);
        published = Math.max(0, Math.min(value, this.max));
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            updateClient(value, pageSize);
        }
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private void updateClient(int value, int pageSize) {
        scrollbar.setRange(0, max, pageSize);
        scrollbar.setCurrentScroll(value);
        published = scrollbar.getCurrentScroll();
    }

    @Override
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
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

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private void drawThin(GUIContext context) {
        int left = Math.round(getPositionX()) + 1, top = Math.round(getPositionY()) + 1;
        int travel = Math.max(0, Math.round(getSizeHeight()) - 2 - THUMB_HEIGHT);
        int thumbY = top + (max <= 0 ? 0 : Math.round(travel * (scrollbar.getCurrentScroll() / (float) max)));
        int body = max <= 0 ? 0xFF6E6A82 : pressed ? 0xFFE6E1F2 : 0xFFC9C3DA;
        context.graphics.fill(left, thumbY, left + THIN_WIDTH - 2, thumbY + THUMB_HEIGHT, 0xFF3F3D52);
        context.graphics.fill(left, thumbY + 1, left + THIN_WIDTH - 2, thumbY + THUMB_HEIGHT - 1, body);
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private void position() {
        scrollbar.setPosition(new Point(Math.round(getPositionX()), Math.round(getPositionY())));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private void release() {
        scrollbar.onMouseUp(new Point(0, 0), 0);
        pressed = false;
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private void publish() {
        int value = scrollbar.getCurrentScroll();
        if (value != published) {
            published = value;
            onScroll.accept(value);
        }
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static Point mouse(UIEvent event) {
        return new Point(Math.round(event.x), Math.round(event.y));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static Scrollbar.Style clientStyle(boolean small) {
        return small ? Scrollbar.SMALL : cn.dancingsnow.neoecoae.client.gui.widget.NEScrollbars.BIG;
    }
}
