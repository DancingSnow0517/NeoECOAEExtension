package cn.dancingsnow.neoecoae.gui.host;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class NEStorageLegacyCanvas extends NEHostCanvas {
    static final int UI_WIDTH = 344;
    static final int UI_HEIGHT = 252;
    private static final int LEFT_PANEL_X = 8;
    private static final int LEFT_PANEL_Y = 24;
    private static final int LEFT_PANEL_W = 218;
    private static final int LEFT_PANEL_H = 132;
    private static final int TEXT_START_X = LEFT_PANEL_X + 8;
    private static final int TEXT_START_Y = LEFT_PANEL_Y + 8;
    private static final int TEXT_LINE_STEP = 13;
    private static final int METRIC_PANEL_X = 234;
    private static final int METRIC_PANEL_Y = 24;
    private static final int METRIC_PANEL_W = 106;
    private static final int METRIC_PANEL_H = 132;
    private static final int PLAYER_INV_X = 8;
    private static final int PLAYER_INV_LABEL_Y = 159;
    static final int PLAYER_INV_Y = 171;
    static final int PLAYER_HOTBAR_Y = 229;
    private static final int MATRIX_PANEL_X = PLAYER_INV_X + 18 * 9 + 4;
    private static final int MATRIX_PANEL_Y = PLAYER_INV_Y;
    private static final int MATRIX_PANEL_W = UI_WIDTH - MATRIX_PANEL_X - 4;
    private static final int MATRIX_PANEL_H = 249 - MATRIX_PANEL_Y;
    private static final int MATRIX_VIEW_X = MATRIX_PANEL_X + 6;
    private static final int MATRIX_VIEW_W = MATRIX_PANEL_W - 12;
    private static final int MATRIX_CARD_W = 82;
    private static final int MATRIX_CARD_H = 18;
    private static final int MATRIX_CARD_GAP = 3;
    private static final int MATRIX_CARD_STRIDE = MATRIX_CARD_W + MATRIX_CARD_GAP;
    private static final int MATRIX_SCROLLBAR_Y = MATRIX_PANEL_Y + 5;
    private static final int MATRIX_SCROLLBAR_H = 4;
    private static final int MATRIX_CARD_FIRST_Y = MATRIX_SCROLLBAR_Y + MATRIX_SCROLLBAR_H + MATRIX_CARD_GAP;
    private static final int MATRIX_ROW_STEP = MATRIX_CARD_H + MATRIX_CARD_GAP;
    private static final int MATRIX_ROWS = 3;

    private static final int COLUMN_VIEW_X = METRIC_PANEL_X + 8;
    private static final int COLUMN_VIEW_W = METRIC_PANEL_W - 16;
    private static final int COLUMN_Y = METRIC_PANEL_Y + 32;
    private static final int COLUMN_H = 66;
    private static final int COLUMN_W = 22;
    private static final int COLUMN_GAP = 8;
    private static final int COLUMN_SCROLLBAR_Y = METRIC_PANEL_Y + 8;
    private static final int COLUMN_SCROLLBAR_H = 3;

    private final ECOStorageSystemBlockEntity storage;
    private final Map<String, Float> animatedColumnRatios = new HashMap<>();
    private float leftScroll;
    private float matrixScroll;
    private float matrixScrollTarget;
    private float columnScroll;
    private float columnScrollTarget;
    private StorageSnapshot snapshot = StorageSnapshot.EMPTY;

    NEStorageLegacyCanvas(ECOStorageSystemBlockEntity storage) {
        super(UI_WIDTH, UI_HEIGHT);
        this.storage = storage;
        bindSnapshot();
        addEventListener(UIEvents.MOUSE_WHEEL, this::onMouseWheel);
        addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            HoverTooltips tooltip = tooltipAt(event.x, event.y);
            if (tooltip != null) {
                event.hoverTooltips = tooltip;
            }
        });
    }

    @Override
    protected byte[] encodeSnapshot() {
        return NEHostSnapshots.encode(buf -> {
            buf.writeBoolean(storage.isFormed());
            buf.writeVarLong(Math.max(0L, storage.getStoredEnergy()));
            buf.writeVarLong(Math.max(0L, storage.getMaxEnergy()));
            NEHostSnapshots.writeTypeStats(buf, storage.createStorageTypeStats());
            NEHostSnapshots.writeMatrixCells(buf, storage.createStorageMatrixCells());
        });
    }

    @Override
    protected void acceptSnapshot(byte[] snapshotData) {
        NEHostSnapshots.decode(snapshotData, buf -> this.snapshot = new StorageSnapshot(
            buf.readBoolean(),
            Math.max(0L, buf.readVarLong()),
            Math.max(0L, buf.readVarLong()),
            NEHostSnapshots.readTypeStats(buf),
            NEHostSnapshots.readMatrixCells(buf)
        ));
    }

    @Override
    protected void drawHostBackground(GUIContext context) {
        drawInsetRect(context, LEFT_PANEL_X, LEFT_PANEL_Y, LEFT_PANEL_W, LEFT_PANEL_H);
        drawInsetRect(context, METRIC_PANEL_X, METRIC_PANEL_Y, METRIC_PANEL_W, METRIC_PANEL_H);
        drawInsetRect(context, MATRIX_PANEL_X, MATRIX_PANEL_Y, MATRIX_PANEL_W, MATRIX_PANEL_H);

        drawHeader(context);
        drawStorageText(context);
        drawMetricColumns(context);
        drawInventoryLabel(context);
        drawInventorySlots(context);
        drawMatrix(context);
    }

    private void drawHeader(GUIContext context) {
        Component label = tr("gui.neoecoae.machine.formed", "Formed").append(": ");
        Component value = boolText(snapshot.formed());
        int width = context.mc.font.width(label) + context.mc.font.width(value);
        int x = 316 - width;
        drawFittedText(context, storage.getHostTitle(), 8, 8, Math.max(40, x - 12), TEXT_PRIMARY);
        drawText(context, label, x, 8, 0xFF4A4A4A);
        drawText(context, value, x + context.mc.font.width(label), 8, snapshot.formed() ? 0xFF1F9D55 : 0xFFD13F3F);
    }

    private void drawStorageText(GUIContext context) {
        List<StorageLine> lines = storageLines();
        int viewportH = LEFT_PANEL_H - 16;
        int contentH = Math.max(0, lines.size() * TEXT_LINE_STEP);
        leftScroll = Mth.clamp(leftScroll, 0.0F, Math.max(0, contentH - viewportH));
        context.graphics.flush();
        context.enableScissor(absX(LEFT_PANEL_X + 4), absY(LEFT_PANEL_Y + 4), LEFT_PANEL_W - 8, LEFT_PANEL_H - 8);
        float y = TEXT_START_Y - leftScroll;
        for (StorageLine line : lines) {
            drawFittedText(context, line.text(), TEXT_START_X, y, LEFT_PANEL_W - 18, line.color());
            y += TEXT_LINE_STEP;
        }
        context.graphics.flush();
        context.disableScissor();
        if (contentH > viewportH) {
            float trackH = LEFT_PANEL_H - 10;
            float thumbH = Math.max(12.0F, trackH * viewportH / contentH);
            float thumbY = LEFT_PANEL_Y + 5 + (trackH - thumbH) * leftScroll / Math.max(1.0F, contentH - viewportH);
            drawScroller(context, LEFT_PANEL_X + LEFT_PANEL_W - 5, LEFT_PANEL_Y + 5, 2, trackH, thumbY, thumbH);
        }
    }

    private List<StorageLine> storageLines() {
        List<StorageLine> lines = new ArrayList<>();
        lines.add(new StorageLine(tr("gui.neoecoae.storage.energy", "Energy Monitor"), TEXT_PRIMARY));
        lines.add(new StorageLine(Component.literal(trString("gui.neoecoae.storage.energy_storage", "Energy Storage")
                + ": " + NEHostFormat.usedTotal(snapshot.storedEnergy(), snapshot.maxEnergy()) + " AE"), TEXT_MUTED));
        for (NEStorageTypeStat stat : snapshot.typeStats()) {
            lines.add(new StorageLine(stat.displayName(), accentColor(stat.typeId().getPath(), lines.size())));
            lines.add(new StorageLine(Component.literal(trString("gui.neoecoae.host.metric.types", "Types")
                    + ": " + NEHostFormat.usedTotal(stat.usedTypes().getAsLong(), stat.totalTypes().getAsLong())), TEXT_MUTED));
            lines.add(new StorageLine(Component.literal(trString("gui.neoecoae.host.metric.bytes", "Bytes")
                    + ": " + NEHostFormat.usedTotalBytes(stat.usedBytes().getAsLong(), stat.totalBytes().getAsLong())), TEXT_MUTED));
        }
        return lines;
    }

    private void drawMetricColumns(GUIContext context) {
        List<NEStorageTypeStat> stats = columnStats();
        int contentW = stats.isEmpty() ? 0 : stats.size() * COLUMN_W + (stats.size() - 1) * COLUMN_GAP;
        float maxScroll = Math.max(0, contentW - COLUMN_VIEW_W);
        columnScrollTarget = Mth.clamp(columnScrollTarget, 0.0F, maxScroll);
        columnScroll = approach(columnScroll, columnScrollTarget);
        if (maxScroll <= 0.0F) {
            columnScroll = 0.0F;
            columnScrollTarget = 0.0F;
        }
        if (contentW > COLUMN_VIEW_W) {
            float thumbW = Math.max(12.0F, COLUMN_VIEW_W * COLUMN_VIEW_W / contentW);
            float thumbX = COLUMN_VIEW_X + (COLUMN_VIEW_W - thumbW) * columnScroll / Math.max(1.0F, maxScroll);
            fillLocal(context, COLUMN_VIEW_X, COLUMN_SCROLLBAR_Y, COLUMN_VIEW_W, COLUMN_SCROLLBAR_H, PANEL_OUTER);
            fillLocal(context, thumbX, COLUMN_SCROLLBAR_Y, thumbW, COLUMN_SCROLLBAR_H, PANEL_EDGE);
        }
        animatedColumnRatios.keySet().removeIf(key -> stats.stream().noneMatch(stat -> stat.typeId().toString().equals(key)));
        float startX = contentW <= COLUMN_VIEW_W ? COLUMN_VIEW_X + (COLUMN_VIEW_W - contentW) / 2.0F : COLUMN_VIEW_X - columnScroll;
        context.graphics.flush();
        context.enableScissor(absX(COLUMN_VIEW_X), absY(METRIC_PANEL_Y + 18), COLUMN_VIEW_W, METRIC_PANEL_H - 23);
        for (int i = 0; i < stats.size(); i++) {
            NEStorageTypeStat stat = stats.get(i);
            float x = startX + i * (COLUMN_W + COLUMN_GAP);
            if (x + COLUMN_W <= COLUMN_VIEW_X || x >= COLUMN_VIEW_X + COLUMN_VIEW_W) {
                continue;
            }
            float ratio = animatedColumnRatio(stat);
            drawColumn(context, stat, x, ratio);
        }
        context.graphics.flush();
        context.disableScissor();
    }

    private void drawColumn(GUIContext context, NEStorageTypeStat stat, float x, float ratio) {
        String label = NEHostDraw.fit(context, stat.displayName().getString(), COLUMN_W + 18);
        drawCenteredText(context, label, x - 9, COLUMN_Y - 14, COLUMN_W + 18, TEXT_PRIMARY);
        drawSmallInsetRect(context, x, COLUMN_Y, COLUMN_W, COLUMN_H);
        float ix = x + 5;
        float iy = COLUMN_Y + 6;
        float iw = COLUMN_W - 10;
        float ih = COLUMN_H - 12;
        fillLocal(context, ix, iy, iw, ih, 0xAA17141E);
        long used = stat.usedBytes().getAsLong();
        long total = stat.totalBytes().getAsLong();
        int fillH = Math.round(ih * ratio);
        if (fillH > 0) {
            int color = accentColor(stat.typeId().getPath(), 0);
            fillLocal(context, ix, iy + ih - fillH, iw, fillH, color);
            fillLocal(context, ix, iy + ih - fillH, iw, Math.min(2, fillH), 0x70FFFFFF);
        }
        for (int i = 1; i < 6; i++) {
            float tickY = iy + ih - ih * i / 6.0F;
            fillLocal(context, ix - 2, tickY, 5, 1, 0xCCC9C3D6);
            fillLocal(context, ix + iw - 3, tickY, 5, 1, 0xCCC9C3D6);
        }
        String percent = NEHostFormat.percent(used, total);
        drawSmallInsetRect(context, x - 2, COLUMN_Y + COLUMN_H + 5, COLUMN_W + 4, 15);
        drawCenteredText(context, percent, x - 2, COLUMN_Y + COLUMN_H + 8, COLUMN_W + 4, total <= 0 ? TEXT_MUTED : accentColor(stat.typeId().getPath(), 0));
    }

    private void drawInventoryLabel(GUIContext context) {
        drawFittedText(context, tr("gui.neoecoae.common.inventory", "Inventory"), PLAYER_INV_X, PLAYER_INV_LABEL_Y, 18 * 9, TEXT_MUTED);
    }

    private void drawInventorySlots(GUIContext context) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(context, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlot(context, PLAYER_INV_X + col * 18, PLAYER_HOTBAR_Y);
        }
    }

    private void drawMatrix(GUIContext context) {
        List<NEStorageMatrixCell> cells = snapshot.matrixCells().stream()
                .sorted(Comparator.comparingInt(NEStorageMatrixCell::row).thenComparingInt(NEStorageMatrixCell::column))
                .toList();
        int columns = cells.stream().mapToInt(cell -> Math.max(0, cell.column()) + 1).max().orElse(0);
        int contentW = columns <= 0 ? 0 : (columns - 1) * MATRIX_CARD_STRIDE + MATRIX_CARD_W;
        float maxScroll = Math.max(0, contentW - MATRIX_VIEW_W);
        matrixScrollTarget = Mth.clamp(matrixScrollTarget, 0.0F, maxScroll);
        matrixScroll = approach(matrixScroll, matrixScrollTarget);
        if (maxScroll <= 0.0F) {
            matrixScroll = 0.0F;
            matrixScrollTarget = 0.0F;
        }
        if (contentW > MATRIX_VIEW_W) {
            float thumbW = Math.max(12.0F, MATRIX_VIEW_W * MATRIX_VIEW_W / contentW);
            float thumbX = MATRIX_VIEW_X + (MATRIX_VIEW_W - thumbW) * matrixScroll / Math.max(1.0F, maxScroll);
            fillLocal(context, MATRIX_VIEW_X, MATRIX_SCROLLBAR_Y, MATRIX_VIEW_W, MATRIX_SCROLLBAR_H, PANEL_OUTER);
            fillLocal(context, thumbX, MATRIX_SCROLLBAR_Y, thumbW, MATRIX_SCROLLBAR_H, PANEL_MIDDLE);
            fillLocal(context, thumbX, MATRIX_SCROLLBAR_Y, thumbW, 1, PANEL_EDGE);
        }
        if (cells.isEmpty()) {
            drawCenteredText(context, tr("gui.neoecoae.storage.matrix.empty", "No storage drives"),
                    MATRIX_PANEL_X, MATRIX_PANEL_Y + MATRIX_PANEL_H / 2.0F - 4, MATRIX_PANEL_W, TEXT_MUTED);
            return;
        }
        context.graphics.flush();
        context.enableScissor(absX(MATRIX_VIEW_X), absY(MATRIX_CARD_FIRST_Y), MATRIX_VIEW_W, PLAYER_HOTBAR_Y + 18 - MATRIX_CARD_FIRST_Y);
        for (NEStorageMatrixCell cell : cells) {
            if (!cell.hasCell() || cell.row() < 0 || cell.row() >= MATRIX_ROWS) {
                continue;
            }
            float x = MATRIX_VIEW_X + cell.column() * MATRIX_CARD_STRIDE - matrixScroll;
            float y = MATRIX_CARD_FIRST_Y + cell.row() * MATRIX_ROW_STEP;
            if (x + MATRIX_CARD_W <= MATRIX_VIEW_X || x >= MATRIX_VIEW_X + MATRIX_VIEW_W) {
                continue;
            }
            drawMatrixCard(context, cell, x, y);
        }
        context.graphics.flush();
        context.disableScissor();
    }

    private void drawMatrixCard(GUIContext context, NEStorageMatrixCell cell, float x, float y) {
        boolean hovered = containsLocal(Math.max(x, MATRIX_VIEW_X), y, Math.min(x + MATRIX_CARD_W, MATRIX_VIEW_X + MATRIX_VIEW_W) - Math.max(x, MATRIX_VIEW_X), MATRIX_CARD_H, context.mouseX, context.mouseY);
        int bg = hovered ? 0xFF3B3645 : 0xFF302C38;
        fillLocal(context, x + 2, y, MATRIX_CARD_W - 4, MATRIX_CARD_H, bg);
        fillLocal(context, x, y + 2, MATRIX_CARD_W, MATRIX_CARD_H - 4, bg);
        fillLocal(context, x + 1, y + 1, MATRIX_CARD_W - 2, MATRIX_CARD_H - 2, bg);
        drawItem(context, cell.stack(), x + 1, y + 1);
        drawScaledText(context,
                tr("gui.neoecoae.storage.matrix_card.title", "%s Storage", tierName(cell.tier())),
                x + 19,
                y + 5,
                0.72F,
                tierColor(cell.tier()));
    }

    private void onMouseWheel(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent event) {
        if (containsLocal(LEFT_PANEL_X, LEFT_PANEL_Y, LEFT_PANEL_W, LEFT_PANEL_H, event.x, event.y)) {
            int viewportH = LEFT_PANEL_H - 16;
            int contentH = storageLines().size() * TEXT_LINE_STEP;
            leftScroll = Mth.clamp(leftScroll - event.deltaY * 13.0F, 0.0F, Math.max(0, contentH - viewportH));
            event.stopPropagation();
            return;
        }
        if (containsLocal(METRIC_PANEL_X, METRIC_PANEL_Y, METRIC_PANEL_W, METRIC_PANEL_H, event.x, event.y)) {
            List<NEStorageTypeStat> stats = columnStats();
            int contentW = stats.isEmpty() ? 0 : stats.size() * COLUMN_W + (stats.size() - 1) * COLUMN_GAP;
            columnScrollTarget = Mth.clamp(columnScrollTarget - event.deltaY * 18.0F, 0.0F, Math.max(0, contentW - COLUMN_VIEW_W));
            event.stopPropagation();
            return;
        }
        if (containsLocal(MATRIX_PANEL_X, MATRIX_PANEL_Y, MATRIX_PANEL_W, MATRIX_PANEL_H, event.x, event.y)) {
            int columns = snapshot.matrixCells().stream()
                    .mapToInt(cell -> Math.max(0, cell.column()) + 1)
                    .max()
                    .orElse(0);
            int contentW = columns <= 0 ? 0 : (columns - 1) * MATRIX_CARD_STRIDE + MATRIX_CARD_W;
            matrixScrollTarget = Mth.clamp(matrixScrollTarget - event.deltaY * 18.0F, 0.0F, Math.max(0, contentW - MATRIX_VIEW_W));
            event.stopPropagation();
        }
    }

    private HoverTooltips tooltipAt(double mouseX, double mouseY) {
        HoverTooltips matrix = matrixTooltip(mouseX, mouseY);
        if (matrix != null) {
            return matrix;
        }
        HoverTooltips column = columnTooltip(mouseX, mouseY);
        if (column != null) {
            return column;
        }
        return null;
    }

    private HoverTooltips matrixTooltip(double mouseX, double mouseY) {
        for (NEStorageMatrixCell cell : snapshot.matrixCells()) {
            if (!cell.hasCell() || cell.row() < 0 || cell.row() >= MATRIX_ROWS) {
                continue;
            }
            float x = MATRIX_VIEW_X + cell.column() * MATRIX_CARD_STRIDE - matrixScroll;
            float y = MATRIX_CARD_FIRST_Y + cell.row() * MATRIX_ROW_STEP;
            float clippedX = Math.max(x, MATRIX_VIEW_X);
            float clippedW = Math.min(x + MATRIX_CARD_W, MATRIX_VIEW_X + MATRIX_VIEW_W) - clippedX;
            if (clippedW <= 0 || !containsLocal(clippedX, y, clippedW, MATRIX_CARD_H, mouseX, mouseY)) {
                continue;
            }
            ItemStack stack = cell.stack();
            return new HoverTooltips(List.of(
                    stack.getHoverName(),
                    tr("gui.neoecoae.storage.matrix_card.title", "%s Storage", tierName(cell.tier())).withStyle(ChatFormatting.AQUA),
                    tr("gui.neoecoae.host.metric.types", "Types")
                            .append(": ")
                            .append(Component.literal(NEHostFormat.usedTotal(cell.usedTypes(), cell.totalTypes())).withStyle(ChatFormatting.WHITE)),
                    tr("gui.neoecoae.host.metric.bytes", "Bytes")
                            .append(": ")
                            .append(Component.literal(NEHostFormat.usedTotalBytes(cell.usedBytes(), cell.totalBytes())).withStyle(ChatFormatting.WHITE))
            ), stack.getTooltipImage().orElse(null), null, stack);
        }
        return null;
    }

    private HoverTooltips columnTooltip(double mouseX, double mouseY) {
        List<NEStorageTypeStat> stats = columnStats();
        int contentW = stats.isEmpty() ? 0 : stats.size() * COLUMN_W + (stats.size() - 1) * COLUMN_GAP;
        float startX = contentW <= COLUMN_VIEW_W ? COLUMN_VIEW_X + (COLUMN_VIEW_W - contentW) / 2.0F : COLUMN_VIEW_X - columnScroll;
        for (int i = 0; i < stats.size(); i++) {
            NEStorageTypeStat stat = stats.get(i);
            float x = startX + i * (COLUMN_W + COLUMN_GAP);
            float clippedX = Math.max(x, COLUMN_VIEW_X);
            float clippedW = Math.min(x + COLUMN_W, COLUMN_VIEW_X + COLUMN_VIEW_W) - clippedX;
            if (clippedW <= 0 || !containsLocal(clippedX, COLUMN_Y, clippedW, COLUMN_H, mouseX, mouseY)) {
                continue;
            }
            return new HoverTooltips(List.of(
                    stat.displayName(),
                    tr("gui.neoecoae.host.metric.bytes", "Bytes")
                            .append(": ")
                            .append(Component.literal(NEHostFormat.usedTotalBytes(stat.usedBytes().getAsLong(), stat.totalBytes().getAsLong())).withStyle(ChatFormatting.WHITE)),
                    tr("gui.neoecoae.host.metric.types", "Types")
                            .append(": ")
                            .append(Component.literal(NEHostFormat.usedTotal(stat.usedTypes().getAsLong(), stat.totalTypes().getAsLong())).withStyle(ChatFormatting.WHITE))
            ), null, null, null);
        }
        return null;
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

    private static int accentColor(String key, int index) {
        String lower = key.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("fluid")) {
            return 0xFF3A8FD6;
        }
        if (lower.contains("item")) {
            return 0xFF43B678;
        }
        if (lower.contains("chemical") || lower.contains("gas")) {
            return 0xFF9A6AE8;
        }
        int[] palette = {0xFFE06C75, 0xFF61AFEF, 0xFF98C379, 0xFFD19A66, 0xFFC678DD};
        return palette[Math.floorMod(index, palette.length)];
    }

    private List<NEStorageTypeStat> columnStats() {
        List<NEStorageTypeStat> stats = snapshot.typeStats().stream()
                .filter(stat -> stat.totalBytes().getAsLong() > 0 || stat.totalTypes().getAsLong() > 0)
                .toList();
        return stats.isEmpty() ? snapshot.typeStats() : stats;
    }

    private float animatedColumnRatio(NEStorageTypeStat stat) {
        long total = stat.totalBytes().getAsLong();
        float target = total <= 0L ? 0.0F : Mth.clamp((float) stat.usedBytes().getAsLong() / (float) total, 0.0F, 1.0F);
        String key = stat.typeId().toString();
        float current = animatedColumnRatios.getOrDefault(key, target);
        float next = approach(current, target);
        animatedColumnRatios.put(key, next);
        return next;
    }

    private static float approach(float current, float target) {
        float next = Mth.lerp(0.16F, current, target);
        return Math.abs(next - target) < 0.05F ? target : next;
    }

    private record StorageLine(Component text, int color) {
    }

    private record StorageSnapshot(
        boolean formed,
        long storedEnergy,
        long maxEnergy,
        List<NEStorageTypeStat> typeStats,
        List<NEStorageMatrixCell> matrixCells
    ) {
        private static final StorageSnapshot EMPTY = new StorageSnapshot(false, 0L, 0L, List.of(), List.of());
    }
}
