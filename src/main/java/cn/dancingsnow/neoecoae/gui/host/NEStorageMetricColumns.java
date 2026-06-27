package cn.dancingsnow.neoecoae.gui.host;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

final class NEStorageMetricColumns extends NESnapshotElement {
    private static final int VIEW_X = 8;
    private static final int COLUMN_Y = 32;
    private static final int COLUMN_H = 66;
    private static final int COLUMN_W = 22;
    private static final int COLUMN_GAP = 8;
    private static final int SCROLLBAR_Y = 8;
    private static final int SCROLLBAR_H = 3;

    private final Map<String, Float> animatedColumnRatios = new HashMap<>();
    private final ScrollModel scroll = new ScrollModel();
    private NEStorageSnapshotData snapshot = NEStorageSnapshotData.EMPTY;
    private float mouseX;
    private float mouseY;

    NEStorageMetricColumns(Supplier<byte[]> serverSnapshot) {
        super(serverSnapshot);
        layout(layout -> layout.widthPercent(100).heightPercent(100));
        addEventListener(UIEvents.MOUSE_WHEEL, this::onMouseWheel);
        addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            HoverTooltips tooltip = tooltipAt(mouseX, mouseY);
            if (tooltip != null) {
                event.hoverTooltips = tooltip;
                event.stopPropagation();
            }
        });
    }

    @Override
    protected void acceptSnapshot(byte[] snapshotData) {
        snapshot = NEStorageSnapshotData.decode(snapshotData);
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        this.mouseX = context.mouseX;
        this.mouseY = context.mouseY;
        List<NEStorageMetrics.Metric> stats = columnStats();
        int viewW = viewWidth();
        int contentW = stats.isEmpty() ? 0 : stats.size() * COLUMN_W + (stats.size() - 1) * COLUMN_GAP;
        scroll.update(contentW, viewW);
        if (contentW > viewW) {
            float thumbW = Math.max(12.0F, viewW * viewW / contentW);
            float thumbX = VIEW_X + (viewW - thumbW) * scroll.offset() / Math.max(1.0F, scroll.max());
            NEHostUiPrimitives.rect(this, context, VIEW_X, SCROLLBAR_Y, viewW, SCROLLBAR_H, NEHostUiPrimitives.PANEL_OUTER);
            NEHostUiPrimitives.rect(this, context, thumbX, SCROLLBAR_Y, thumbW, SCROLLBAR_H, NEHostUiPrimitives.PANEL_EDGE);
        }
        animatedColumnRatios.keySet().removeIf(key -> stats.stream().noneMatch(stat -> stat.key().equals(key)));
        float startX = contentW <= viewW ? VIEW_X + (viewW - contentW) / 2.0F : VIEW_X - scroll.offset();
        context.graphics.flush();
        context.enableScissor(NEHostUiPrimitives.ax(this, VIEW_X), NEHostUiPrimitives.ay(this, 18), viewW, Math.round(getSizeHeight()) - 23);
        for (int i = 0; i < stats.size(); i++) {
            NEStorageMetrics.Metric stat = stats.get(i);
            float x = startX + i * (COLUMN_W + COLUMN_GAP);
            if (x + COLUMN_W <= VIEW_X || x >= VIEW_X + viewW) {
                continue;
            }
            drawColumn(context, stat, x, animatedColumnRatio(stat));
        }
        context.graphics.flush();
        context.disableScissor();
    }

    private void drawColumn(GUIContext context, NEStorageMetrics.Metric stat, float x, float ratio) {
        String label = NEHostUiPrimitives.fit(context.mc.font, stat.label().getString(), COLUMN_W + 18);
        NEHostUiPrimitives.centeredText(this, context, Component.literal(label), x - 9, COLUMN_Y - 14, COLUMN_W + 18, NEHostUiPrimitives.TEXT_PRIMARY);
        NEHostUiPrimitives.smallInsetRect(this, context, x, COLUMN_Y, COLUMN_W, COLUMN_H);
        float ix = x + 5;
        float iy = COLUMN_Y + 6;
        float iw = COLUMN_W - 10;
        float ih = COLUMN_H - 12;
        NEHostUiPrimitives.rect(this, context, ix, iy, iw, ih, 0xAA17141E);
        int fillH = Math.round(ih * ratio);
        if (fillH > 0) {
            int color = NEStorageMetrics.metricColor(stat.accentColor(), stat.totalBytes(), ratio);
            NEHostUiPrimitives.rect(this, context, ix, iy + ih - fillH, iw, fillH, color);
            NEHostUiPrimitives.rect(this, context, ix, iy + ih - fillH, iw, Math.min(2, fillH), 0x70FFFFFF);
        }
        for (int i = 1; i < 6; i++) {
            float tickY = iy + ih - ih * i / 6.0F;
            NEHostUiPrimitives.rect(this, context, ix - 2, tickY, 5, 1, 0xCCC9C3D6);
            NEHostUiPrimitives.rect(this, context, ix + iw - 3, tickY, 5, 1, 0xCCC9C3D6);
        }
        String percent = NEHostFormat.percent(stat.usedBytes(), stat.totalBytes());
        NEHostUiPrimitives.smallInsetRect(this, context, x - 2, COLUMN_Y + COLUMN_H + 5, COLUMN_W + 4, 15);
        NEHostUiPrimitives.centeredText(this, context, Component.literal(percent), x - 2, COLUMN_Y + COLUMN_H + 8, COLUMN_W + 4,
            stat.totalBytes() <= 0 ? NEHostUiPrimitives.TEXT_MUTED : NEStorageMetrics.metricColor(stat.accentColor(), stat.totalBytes(), ratio));
    }

    private void onMouseWheel(UIEvent event) {
        if (!UIElement.isMouseOverRect(getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight(), mouseX, mouseY)) {
            return;
        }
        List<NEStorageMetrics.Metric> stats = columnStats();
        int contentW = stats.isEmpty() ? 0 : stats.size() * COLUMN_W + (stats.size() - 1) * COLUMN_GAP;
        scroll.scrollBy((float) -event.deltaY * 18.0F, contentW, viewWidth(), true);
        event.stopPropagation();
    }

    private HoverTooltips tooltipAt(double mouseX, double mouseY) {
        List<NEStorageMetrics.Metric> stats = columnStats();
        int viewW = viewWidth();
        int contentW = stats.isEmpty() ? 0 : stats.size() * COLUMN_W + (stats.size() - 1) * COLUMN_GAP;
        float startX = contentW <= viewW ? VIEW_X + (viewW - contentW) / 2.0F : VIEW_X - scroll.offset();
        for (int i = 0; i < stats.size(); i++) {
            NEStorageMetrics.Metric stat = stats.get(i);
            float x = startX + i * (COLUMN_W + COLUMN_GAP);
            float clippedX = Math.max(x, VIEW_X);
            float clippedW = Math.min(x + COLUMN_W, VIEW_X + viewW) - clippedX;
            if (clippedW <= 0 || !NEHostUiPrimitives.contains(this, clippedX, COLUMN_Y, clippedW, COLUMN_H, mouseX, mouseY)) {
                continue;
            }
            return new HoverTooltips(List.of(
                stat.label(),
                NEHostUiPrimitives.tr("gui.neoecoae.host.metric.bytes", "Bytes")
                    .append(": ")
                    .append(Component.literal(NEHostFormat.usedTotalBytes(stat.usedBytes(), stat.totalBytes())).withStyle(ChatFormatting.WHITE)),
                NEHostUiPrimitives.tr("gui.neoecoae.host.metric.types", "Types")
                    .append(": ")
                    .append(Component.literal(NEHostFormat.usedTotal(stat.usedTypes(), stat.totalTypes())).withStyle(ChatFormatting.WHITE))
            ), null, null, null);
        }
        return null;
    }

    private List<NEStorageMetrics.Metric> columnStats() {
        return NEStorageMetrics.columnStats(snapshot.typeStats());
    }

    private int viewWidth() {
        return Math.max(0, Math.round(getSizeWidth()) - 16);
    }

    private float animatedColumnRatio(NEStorageMetrics.Metric stat) {
        long total = stat.totalBytes();
        float target = total <= 0L ? 0.0F : Mth.clamp((float) stat.usedBytes() / (float) total, 0.0F, 1.0F);
        String key = stat.key();
        float current = animatedColumnRatios.getOrDefault(key, 0.0F);
        float next = approach(current, target);
        animatedColumnRatios.put(key, next);
        return next;
    }

    private static float approach(float current, float target) {
        float next = Mth.lerp(0.16F, current, target);
        return Math.abs(next - target) < 0.05F ? target : next;
    }

    private static final class ScrollModel {
        private float offset;
        private float target;
        private float max;

        void update(int contentSize, int viewportSize) {
            max = Math.max(0, contentSize - viewportSize);
            target = Mth.clamp(target, 0.0F, max);
            offset = max <= 0.0F ? 0.0F : approach(offset, target);
            if (max <= 0.0F) {
                target = 0.0F;
            }
        }

        void scrollBy(float delta, int contentSize, int viewportSize, boolean snap) {
            update(contentSize, viewportSize);
            target = Mth.clamp(target + delta, 0.0F, max);
            if (snap) {
                offset = target;
            }
        }

        float offset() {
            return offset;
        }

        float max() {
            return max;
        }
    }
}
