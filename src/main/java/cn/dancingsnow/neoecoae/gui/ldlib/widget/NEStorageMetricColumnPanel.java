package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.client.gui.ldlib.NELDLibClientStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibScrollBar;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import cn.dancingsnow.neoecoae.gui.ldlib.widget.NEStorageMetricsModel.Metric;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

final class NEStorageMetricColumnPanel {
    private static final int PANEL_X = 234;
    private static final int PANEL_Y = 24;
    private static final int PANEL_W = 106;
    private static final int PANEL_H = 132;
    private static final int VIEW_X = PANEL_X + 8;
    private static final int VIEW_W = PANEL_W - 16;
    private static final int COLUMN_Y = PANEL_Y + 32;
    private static final int COLUMN_H = 66;
    private static final int COLUMN_W = 22;
    private static final int COLUMN_GAP = 8;
    private static final int SCROLLBAR_Y = PANEL_Y + 8;
    private static final int SCROLLBAR_H = 3;
    private static final int PERCENT_GAP = 5;
    private static final int PERCENT_H = 15;
    private static final double SCROLL_SPEED = 18.0D;
    private static final double SCROLL_LERP = 0.24D;

    private static final String TOOLTIP_TYPE_USED = "gui.neoecoae.storage.tooltip.type_used";
    private static final String TOOLTIP_USED_TOTAL = "gui.neoecoae.storage.tooltip.used_total";

    private final Supplier<Font> fontSupplier;
    private final IntUnaryOperator absX;
    private final IntUnaryOperator absY;
    private final DoubleSupplier scrollPixels;
    private final DoubleSupplier scrollTargetPixels;
    private final DoubleConsumer scrollPixelsSetter;
    private final DoubleConsumer scrollTargetSetter;
    private final Runnable rememberScrollState;

    NEStorageMetricColumnPanel(
            Supplier<Font> fontSupplier,
            IntUnaryOperator absX,
            IntUnaryOperator absY,
            DoubleSupplier scrollPixels,
            DoubleSupplier scrollTargetPixels,
            DoubleConsumer scrollPixelsSetter,
            DoubleConsumer scrollTargetSetter,
            Runnable rememberScrollState) {
        this.fontSupplier = fontSupplier;
        this.absX = absX;
        this.absY = absY;
        this.scrollPixels = scrollPixels;
        this.scrollTargetPixels = scrollTargetPixels;
        this.scrollPixelsSetter = scrollPixelsSetter;
        this.scrollTargetSetter = scrollTargetSetter;
        this.rememberScrollState = rememberScrollState;
    }

    boolean isMouseInPanel(double mouseX, double mouseY) {
        return Widget.isMouseOver(absX(PANEL_X), absY(PANEL_Y), PANEL_W, PANEL_H, mouseX, mouseY);
    }

    boolean isMouseInScrollbar(List<Metric> metrics, double mouseX, double mouseY) {
        return maxScrollPixels(metrics) > 0.0D
                && Widget.isMouseOver(absX(VIEW_X), absY(SCROLLBAR_Y), VIEW_W, SCROLLBAR_H, mouseX, mouseY);
    }

    boolean scrollBy(List<Metric> metrics, double wheelDelta) {
        double maxScroll = maxScrollPixels(metrics);
        double oldTarget = scrollTargetPixels.getAsDouble();
        setScrollTarget(Mth.clamp(oldTarget - wheelDelta * SCROLL_SPEED, 0.0D, maxScroll));
        rememberScrollState.run();
        return scrollTargetPixels.getAsDouble() != oldTarget || maxScroll > 0.0D;
    }

    void draw(GuiGraphics graphics, List<Metric> metrics, double[] animatedValues, boolean dragging) {
        NELDLibClientStyle.drawDarkInsetRect(graphics, absX(PANEL_X), absY(PANEL_Y), PANEL_W, PANEL_H);

        int count = metrics.size();
        if (count <= 0) {
            return;
        }
        double maxScroll = maxScrollPixels(metrics);
        setScrollTarget(Mth.clamp(scrollTargetPixels.getAsDouble(), 0.0D, maxScroll));
        if (dragging) {
            setScrollPixels(scrollTargetPixels.getAsDouble());
        } else {
            setScrollPixels(Mth.clamp(
                    Mth.lerp(SCROLL_LERP, scrollPixels.getAsDouble(), scrollTargetPixels.getAsDouble()),
                    0.0D,
                    maxScroll));
        }
        if (Math.abs(scrollPixels.getAsDouble() - scrollTargetPixels.getAsDouble()) < 0.05D) {
            setScrollPixels(scrollTargetPixels.getAsDouble());
        }

        drawScrollbar(graphics, metrics);

        int startX = columnStartX(metrics);
        int clipLeft = absX(VIEW_X);
        int clipTop = absY(PANEL_Y + 18);
        int clipRight = absX(VIEW_X + VIEW_W);
        int clipBottom = absY(PANEL_Y + PANEL_H - 5);
        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);

        for (int i = 0; i < count; i++) {
            int x = startX + i * (COLUMN_W + COLUMN_GAP);
            if (x + COLUMN_W <= VIEW_X || x >= VIEW_X + VIEW_W) {
                continue;
            }
            drawColumn(graphics, metrics.get(i), absX(x), absY(COLUMN_Y), COLUMN_W, COLUMN_H, animatedValues[i]);
        }
        graphics.disableScissor();
    }

    boolean renderTooltip(GuiGraphics graphics, List<Metric> metrics, int mouseX, int mouseY) {
        int count = metrics.size();
        if (count <= 0) {
            return false;
        }
        int startX = columnStartX(metrics);

        for (int i = 0; i < count; i++) {
            int x = startX + i * (COLUMN_W + COLUMN_GAP);
            if (x + COLUMN_W <= VIEW_X || x >= VIEW_X + VIEW_W) {
                continue;
            }
            int clippedX = Math.max(x, VIEW_X);
            int clippedW = Math.min(x + COLUMN_W, VIEW_X + VIEW_W) - clippedX;
            if (!isMouseIn(clippedX, COLUMN_Y, clippedW, COLUMN_H, mouseX, mouseY)) {
                continue;
            }
            Metric metric = metrics.get(i);
            graphics.renderTooltip(
                    font(),
                    List.of(
                            Component.translatable(
                                    TOOLTIP_TYPE_USED,
                                    metric.label(),
                                    NELDLibText.percentOrNA(metric.used(), metric.max())),
                            Component.translatable(
                                    TOOLTIP_USED_TOTAL,
                                    NELDLibText.number(metric.used()),
                                    NELDLibText.number(metric.max())),
                            Component.translatable(
                                    "gui.neoecoae.machine.types_value",
                                    NELDLibText.number(metric.usedTypes()),
                                    NELDLibText.number(metric.totalTypes()))),
                    Optional.empty(),
                    mouseX,
                    mouseY);
            return true;
        }
        return false;
    }

    double maxScrollPixels(List<Metric> metrics) {
        return NELDLibScrollBar.maxScroll(contentWidth(metrics), VIEW_W);
    }

    void updateScrollFromMouse(double mouseX, List<Metric> metrics) {
        int contentWidth = contentWidth(metrics);
        if (contentWidth <= VIEW_W) {
            setScrollTarget(0.0D);
            setScrollPixels(0.0D);
            rememberScrollState.run();
            return;
        }
        setScrollTarget(NELDLibScrollBar.scrollFromMouse(mouseX, absX(VIEW_X), VIEW_W, VIEW_W, contentWidth, 12));
        setScrollPixels(scrollTargetPixels.getAsDouble());
        rememberScrollState.run();
    }

    private void drawScrollbar(GuiGraphics graphics, List<Metric> metrics) {
        NELDLibScrollBar.drawHorizontal(
                graphics,
                absX(VIEW_X),
                absY(SCROLLBAR_Y),
                VIEW_W,
                SCROLLBAR_H,
                contentWidth(metrics),
                VIEW_W,
                scrollPixels.getAsDouble(),
                NELDLibStyle.DARK_PANEL_OUTER,
                NELDLibStyle.DARK_PANEL_MIDDLE,
                NELDLibStyle.DARK_PANEL_LIGHT_EDGE,
                12);
    }

    private void drawColumn(GuiGraphics g, Metric metric, int x, int y, int w, int h, double pct) {
        NELDLibClientStyle.drawCenteredFitted(
                g, font(), metric.label(), x - 9, y - 14, w + 18, NELDLibStyle.DARK_TEXT_PRIMARY);
        NELDLibClientStyle.drawTinyInsetRect(g, x, y, w, h, 0xFF201E27);

        int ix = x + 5;
        int iy = y + 6;
        int iw = w - 10;
        int ih = h - 12;
        int fillH = Mth.clamp((int) Math.round(ih * pct), 0, ih);
        int fillY = iy + ih - fillH;

        g.fill(ix, iy, ix + iw, iy + ih, 0xAA17141E);
        g.fill(ix + 1, iy + 3, ix + 3, iy + ih - 3, 0x45C9C3D6);
        g.fill(ix + iw - 3, iy + 3, ix + iw - 1, iy + ih - 3, 0x40202020);

        if (fillH > 0) {
            int color = NELDLibStyle.metricColor(metric.accentColor(), metric.max(), pct);
            g.fill(ix, fillY, ix + iw, iy + ih, color);
            g.fill(ix, fillY, ix + iw, Math.min(fillY + 2, iy + ih), 0x70FFFFFF);
            g.fill(ix, iy + ih - 2, ix + iw, iy + ih, 0x70000000);
        }

        for (int i = 1; i < 6; i++) {
            int tickY = iy + ih - Math.round(ih * i / 6.0F);
            g.fill(ix - 2, tickY, ix + 3, tickY + 1, 0xCCC9C3D6);
            g.fill(ix + iw - 3, tickY, ix + iw + 2, tickY + 1, 0xCCC9C3D6);
        }

        g.fill(x + 2, y + 2, x + w - 2, y + 5, 0xCC17141E);
        g.fill(x + 2, y + h - 5, x + w - 2, y + h - 2, 0xCC17141E);
        g.fill(x + 3, y + 3, x + 8, y + 10, 0xAA100E16);
        g.fill(x + w - 8, y + 3, x + w - 3, y + 10, 0xAA100E16);
        g.fill(x + 3, y + h - 10, x + 8, y + h - 3, 0xAA100E16);
        g.fill(x + w - 8, y + h - 10, x + w - 3, y + h - 3, 0xAA100E16);

        int percentY = y + h + PERCENT_GAP;
        int percentColor = metric.max() <= 0
                ? NELDLibStyle.DARK_TEXT_MUTED
                : NELDLibStyle.metricColor(metric.accentColor(), metric.max(), pct);
        String percentText = NELDLibText.percentOrNA(metric.used(), metric.max());
        NELDLibClientStyle.drawTinyInsetRect(g, x - 2, percentY, w + 4, PERCENT_H, 0xFF201E27);
        NELDLibClientStyle.drawCenteredScaledString(
                g, font(), percentText, x - 2, percentY, w + 4, PERCENT_H, percentColor, 0.9F);
    }

    private int columnStartX(List<Metric> metrics) {
        int contentWidth = contentWidth(metrics);
        if (contentWidth <= VIEW_W) {
            return VIEW_X + (VIEW_W - contentWidth) / 2;
        }
        return VIEW_X - (int) Math.round(scrollPixels.getAsDouble());
    }

    private static int contentWidth(List<Metric> metrics) {
        int count = metrics.size();
        return count <= 0 ? 0 : count * COLUMN_W + (count - 1) * COLUMN_GAP;
    }

    private boolean isMouseIn(int localX, int localY, int width, int height, int mouseX, int mouseY) {
        return Widget.isMouseOver(absX(localX), absY(localY), width, height, mouseX, mouseY);
    }

    private void setScrollPixels(double value) {
        scrollPixelsSetter.accept(value);
    }

    private void setScrollTarget(double value) {
        scrollTargetSetter.accept(value);
    }

    private Font font() {
        return fontSupplier.get();
    }

    private int absX(int localX) {
        return absX.applyAsInt(localX);
    }

    private int absY(int localY) {
        return absY.applyAsInt(localY);
    }
}
