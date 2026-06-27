package cn.dancingsnow.neoecoae.gui.host;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

final class NEStorageMatrixPanel extends NESnapshotElement {
    private static final int VIEW_X = 6;
    private static final int CARD_W = 82;
    private static final int CARD_H = 18;
    private static final int CARD_GAP = 3;
    private static final int CARD_STRIDE = CARD_W + CARD_GAP;
    private static final int SCROLLBAR_Y = 5;
    private static final int SCROLLBAR_H = 4;
    private static final int CARD_FIRST_Y = SCROLLBAR_Y + SCROLLBAR_H + CARD_GAP;
    private static final int ROW_STEP = CARD_H + CARD_GAP;
    private static final int ROWS = 3;
    private static final int MAX_SCROLL_STATES = 128;
    private static final Map<ScrollStateKey, Float> SCROLL_STATES = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ScrollStateKey, Float> eldest) {
            return size() > MAX_SCROLL_STATES;
        }
    };

    private final ScrollStateKey scrollStateKey;
    private final ScrollModel scroll = new ScrollModel();
    private boolean restoredScroll;
    private NEStorageSnapshotData snapshot = NEStorageSnapshotData.EMPTY;
    private float mouseX;
    private float mouseY;

    NEStorageMatrixPanel(Supplier<byte[]> serverSnapshot, ScrollStateKey scrollStateKey) {
        super(serverSnapshot);
        this.scrollStateKey = scrollStateKey;
        layout(layout -> layout.widthPercent(100).heightPercent(100));
        addEventListener(UIEvents.MOUSE_WHEEL, this::onMouseWheel);
        addEventListener(UIEvents.REMOVED, event -> saveScrollState());
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
        restoreScrollState();
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        this.mouseX = context.mouseX;
        this.mouseY = context.mouseY;
        List<NEStorageMatrixCell> cells = sortedCells();
        int columns = cells.stream().mapToInt(cell -> Math.max(0, cell.column()) + 1).max().orElse(0);
        int viewW = viewWidth();
        int contentW = columns <= 0 ? 0 : (columns - 1) * CARD_STRIDE + CARD_W;
        scroll.update(contentW, viewW);
        saveScrollState();
        if (contentW > viewW) {
            float thumbW = Math.max(12.0F, viewW * viewW / contentW);
            float thumbX = VIEW_X + (viewW - thumbW) * scroll.offset() / Math.max(1.0F, scroll.max());
            NEHostUiPrimitives.rect(this, context, VIEW_X, SCROLLBAR_Y, viewW, SCROLLBAR_H, NEHostUiPrimitives.PANEL_OUTER);
            NEHostUiPrimitives.rect(this, context, thumbX, SCROLLBAR_Y, thumbW, SCROLLBAR_H, NEHostUiPrimitives.PANEL_MIDDLE);
            NEHostUiPrimitives.rect(this, context, thumbX, SCROLLBAR_Y, thumbW, 1, NEHostUiPrimitives.PANEL_EDGE);
        }
        if (cells.isEmpty()) {
            NEHostUiPrimitives.centeredText(this, context, NEHostUiPrimitives.tr("gui.neoecoae.storage.matrix.empty", "No storage drives"),
                0, getSizeHeight() / 2.0F - 4, getSizeWidth(), NEHostUiPrimitives.TEXT_MUTED);
            return;
        }
        context.graphics.flush();
        context.enableScissor(NEHostUiPrimitives.ax(this, VIEW_X), NEHostUiPrimitives.ay(this, CARD_FIRST_Y), viewW, Math.round(getSizeHeight()) - CARD_FIRST_Y);
        for (NEStorageMatrixCell cell : cells) {
            if (!cell.hasCell() || cell.row() < 0 || cell.row() >= ROWS) {
                continue;
            }
            float x = VIEW_X + cell.column() * CARD_STRIDE - scroll.offset();
            float y = CARD_FIRST_Y + cell.row() * ROW_STEP;
            if (x + CARD_W <= VIEW_X || x >= VIEW_X + viewW) {
                continue;
            }
            drawMatrixCard(context, cell, x, y);
        }
        context.graphics.flush();
        context.disableScissor();
    }

    private void drawMatrixCard(GUIContext context, NEStorageMatrixCell cell, float x, float y) {
        boolean hovered = NEHostUiPrimitives.contains(this, Math.max(x, VIEW_X), y,
            Math.min(x + CARD_W, VIEW_X + viewWidth()) - Math.max(x, VIEW_X), CARD_H, mouseX, mouseY);
        int bg = hovered ? 0xFF3B3645 : 0xFF302C38;
        NEHostUiPrimitives.rect(this, context, x + 2, y, CARD_W - 4, CARD_H, bg);
        NEHostUiPrimitives.rect(this, context, x, y + 2, CARD_W, CARD_H - 4, bg);
        NEHostUiPrimitives.rect(this, context, x + 1, y + 1, CARD_W - 2, CARD_H - 2, bg);
        NEHostUiPrimitives.item(this, context, cell.stack(), x + 1, y + 1);
        NEHostUiPrimitives.scaledText(this, context,
            NEHostUiPrimitives.tr("gui.neoecoae.storage.matrix_card.title", "%s Storage", tierName(cell.tier())),
            x + 19, y + 5, 0.72F, tierColor(cell.tier()));
    }

    private void onMouseWheel(UIEvent event) {
        if (!UIElement.isMouseOverRect(getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight(), mouseX, mouseY)) {
            return;
        }
        int columns = snapshot.matrixCells().stream()
            .mapToInt(cell -> Math.max(0, cell.column()) + 1)
            .max()
            .orElse(0);
        int contentW = columns <= 0 ? 0 : (columns - 1) * CARD_STRIDE + CARD_W;
        scroll.scrollBy((float) -event.deltaY * 18.0F, contentW, viewWidth(), true);
        saveScrollState();
        event.stopPropagation();
    }

    private HoverTooltips tooltipAt(double mouseX, double mouseY) {
        for (NEStorageMatrixCell cell : snapshot.matrixCells()) {
            if (!cell.hasCell() || cell.row() < 0 || cell.row() >= ROWS) {
                continue;
            }
            float x = VIEW_X + cell.column() * CARD_STRIDE - scroll.offset();
            float y = CARD_FIRST_Y + cell.row() * ROW_STEP;
            float clippedX = Math.max(x, VIEW_X);
            float clippedW = Math.min(x + CARD_W, VIEW_X + viewWidth()) - clippedX;
            if (clippedW <= 0 || !NEHostUiPrimitives.contains(this, clippedX, y, clippedW, CARD_H, mouseX, mouseY)) {
                continue;
            }
            return new HoverTooltips(List.of(
                NEHostUiPrimitives.tr("gui.neoecoae.storage.matrix_card.title", "%s Storage", tierName(cell.tier())).withStyle(ChatFormatting.AQUA),
                NEHostUiPrimitives.tr("gui.neoecoae.host.metric.types", "Types")
                    .append(": ")
                    .append(Component.literal(NEHostFormat.usedTotal(cell.usedTypes(), cell.totalTypes())).withStyle(ChatFormatting.WHITE)),
                NEHostUiPrimitives.tr("gui.neoecoae.host.metric.bytes", "Bytes")
                    .append(": ")
                    .append(Component.literal(NEHostFormat.usedTotalBytes(cell.usedBytes(), cell.totalBytes())).withStyle(ChatFormatting.WHITE))
            ), null, null, null);
        }
        return null;
    }

    private List<NEStorageMatrixCell> sortedCells() {
        return snapshot.matrixCells().stream()
            .sorted(Comparator.comparingInt(NEStorageMatrixCell::row).thenComparingInt(NEStorageMatrixCell::column))
            .toList();
    }

    private int viewWidth() {
        return Math.max(0, Math.round(getSizeWidth()) - VIEW_X * 2);
    }

    private void restoreScrollState() {
        if (restoredScroll) {
            return;
        }
        restoredScroll = true;
        Float value = SCROLL_STATES.get(scrollStateKey);
        if (value != null) {
            scroll.restore(value);
        }
    }

    private void saveScrollState() {
        SCROLL_STATES.put(scrollStateKey, scroll.target());
    }

    private static String tierName(int tier) {
        return switch (tier) {
            case 3 -> "L9";
            case 2 -> "L6";
            default -> "L4";
        };
    }

    private static int tierColor(int tier) {
        return switch (tier) {
            case 3 -> 0xFFFF55FF;
            case 2 -> 0xFF55FFFF;
            default -> 0xFFFFFF55;
        };
    }

    static ScrollStateKey scrollStateKey(Level level, BlockPos pos) {
        ResourceLocation dimension = level == null ? ResourceLocation.withDefaultNamespace("overworld") : dimension(level.dimension());
        return new ScrollStateKey(dimension, pos);
    }

    private static ResourceLocation dimension(ResourceKey<Level> dimension) {
        return dimension.location();
    }

    private static float approach(float current, float target) {
        float next = Mth.lerp(0.16F, current, target);
        return Math.abs(next - target) < 0.05F ? target : next;
    }

    record ScrollStateKey(ResourceLocation dimension, BlockPos pos) {
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

        void restore(float value) {
            offset = Math.max(0.0F, value);
            target = offset;
        }

        float offset() {
            return offset;
        }

        float target() {
            return target;
        }

        float max() {
            return max;
        }
    }
}
