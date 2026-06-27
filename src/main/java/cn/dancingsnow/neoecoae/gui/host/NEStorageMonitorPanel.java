package cn.dancingsnow.neoecoae.gui.host;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

final class NEStorageMonitorPanel extends NESnapshotElement {
    private static final int EDGE = 8;
    private static final int TEXT_LINE_STEP = 13;
    private final ScrollModel scroll = new ScrollModel();
    private NEStorageSnapshotData snapshot = NEStorageSnapshotData.EMPTY;
    private float mouseX;
    private float mouseY;

    NEStorageMonitorPanel(Supplier<byte[]> serverSnapshot) {
        super(serverSnapshot);
        layout(layout -> layout.widthPercent(100).heightPercent(100));
        addEventListener(UIEvents.MOUSE_WHEEL, this::onMouseWheel);
    }

    @Override
    protected void acceptSnapshot(byte[] snapshotData) {
        snapshot = NEStorageSnapshotData.decode(snapshotData);
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        this.mouseX = context.mouseX;
        this.mouseY = context.mouseY;
        List<StorageLine> lines = storageLines();
        int viewportH = Math.max(0, Math.round(getSizeHeight()) - EDGE * 2);
        int contentH = Math.max(0, (lines.size() - 1) * TEXT_LINE_STEP + context.mc.font.lineHeight);
        scroll.update(contentH, viewportH);
        context.graphics.flush();
        context.enableScissor(NEHostUiPrimitives.ax(this, 4), NEHostUiPrimitives.ay(this, 4), Math.round(getSizeWidth()) - 8, Math.round(getSizeHeight()) - 8);
        float y = EDGE - scroll.offset();
        for (StorageLine line : lines) {
            line.draw(this, context, EDGE, y, Math.round(getSizeWidth()) - 18);
            y += TEXT_LINE_STEP;
        }
        context.graphics.flush();
        context.disableScissor();
        if (contentH > viewportH) {
            float trackH = getSizeHeight() - 10;
            float thumbH = Math.max(12.0F, trackH * viewportH / contentH);
            float thumbY = 5 + (trackH - thumbH) * scroll.offset() / Math.max(1.0F, contentH - viewportH);
            NEHostUiPrimitives.scroller(this, context, getSizeWidth() - 5, 5, 2, trackH, thumbY, thumbH);
        }
    }

    private void onMouseWheel(UIEvent event) {
        if (!UIElement.isMouseOverRect(getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight(), mouseX, mouseY)) {
            return;
        }
        int viewportH = Math.max(0, Math.round(getSizeHeight()) - EDGE * 2);
        int contentH = Math.max(0, storageLines().size() * TEXT_LINE_STEP);
        scroll.scrollBy((float) -event.deltaY * 13.0F, contentH, viewportH, false);
        event.stopPropagation();
    }

    private List<StorageLine> storageLines() {
        List<StorageLine> lines = new ArrayList<>();
        lines.add(StorageLine.plain(NEHostUiPrimitives.tr("gui.neoecoae.storage.energy", "Energy Monitor"), NEHostUiPrimitives.TEXT_PRIMARY));
        lines.add(StorageLine.usedTotal(
            NEHostUiPrimitives.trString("gui.neoecoae.storage.energy_storage", "Energy Storage") + ": ",
            NEHostFormat.number(snapshot.storedEnergy()),
            NEHostFormat.number(snapshot.maxEnergy()),
            snapshot.storedEnergy(),
            snapshot.maxEnergy(),
            "AE"));
        for (NEStorageMetrics.Metric metric : NEStorageMetrics.storageMetrics(snapshot.typeStats())) {
            lines.add(StorageLine.plain(metric.label(), metric.accentColor()));
            lines.add(StorageLine.usedTotal(
                "",
                NEHostFormat.number(metric.usedTypes()),
                NEHostFormat.number(metric.totalTypes()),
                metric.usedTypes(),
                metric.totalTypes(),
                NEHostUiPrimitives.trString("gui.neoecoae.common.types", "Types")));
            lines.add(StorageLine.usedTotal(
                "",
                NEHostFormat.bytes(metric.usedBytes()),
                NEHostFormat.bytes(metric.totalBytes()),
                metric.usedBytes(),
                metric.totalBytes(),
                NEHostUiPrimitives.trString("gui.neoecoae.storage.bytes_used", "bytes used")));
        }
        return lines;
    }

    private sealed interface StorageLine permits PlainStorageLine, UsedTotalStorageLine {
        void draw(UIElement element, GUIContext context, float x, float y, int maxWidth);

        static StorageLine plain(Component text, int color) {
            return new PlainStorageLine(text, color);
        }

        static StorageLine usedTotal(String prefix, String usedText, String maxText, long used, long max, String suffix) {
            return new UsedTotalStorageLine(prefix, usedText, maxText, used, max, suffix);
        }
    }

    private record PlainStorageLine(Component text, int color) implements StorageLine {
        @Override
        public void draw(UIElement element, GUIContext context, float x, float y, int maxWidth) {
            NEHostUiPrimitives.fittedText(element, context, text, x, y, maxWidth, color);
        }
    }

    private record UsedTotalStorageLine(String prefix, String usedText, String maxText, long used, long max, String suffix) implements StorageLine {
        @Override
        public void draw(UIElement element, GUIContext context, float x, float y, int maxWidth) {
            String safeSuffix = suffix == null || suffix.isBlank() ? "" : " " + suffix;
            int fullWidth = context.mc.font.width(prefix + usedText + " / " + maxText + safeSuffix);
            String renderedSuffix = safeSuffix;
            if (fullWidth > maxWidth && !safeSuffix.isEmpty()) {
                renderedSuffix = " " + NEHostUiPrimitives.trString("gui.neoecoae.storage.used_short", "used");
            }
            int cursor = 0;
            cursor += drawSegment(element, context, prefix, x + cursor, y, NEHostUiPrimitives.TEXT_MUTED);
            cursor += drawSegment(element, context, usedText, x + cursor, y, NEStorageMetrics.usedValueColor(used, max));
            cursor += drawSegment(element, context, " / ", x + cursor, y, NEHostUiPrimitives.TEXT_MUTED);
            cursor += drawSegment(element, context, maxText, x + cursor, y, NEHostUiPrimitives.TEXT_VALUE);
            if (!renderedSuffix.isBlank()) {
                drawSegment(element, context, renderedSuffix, x + cursor, y, NEHostUiPrimitives.TEXT_MUTED);
            }
        }

        private int drawSegment(UIElement element, GUIContext context, String text, float x, float y, int color) {
            if (text == null || text.isEmpty()) {
                return 0;
            }
            NEHostUiPrimitives.text(element, context, text, x, y, color);
            return context.mc.font.width(text);
        }
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
    }
}
