package cn.dancingsnow.neoecoae.gui.host;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class NEStorageAeCanvas extends NEHostCanvas {
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
    static final int PLAYER_INV_X = 8;
    private static final int PLAYER_INV_LABEL_Y = 159;
    static final int PLAYER_INV_Y = 171;
    static final int PLAYER_HOTBAR_Y = 229;
    private static final int MATRIX_PANEL_X = PLAYER_INV_X + PLAYER_INVENTORY_WIDTH + 4;
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
    private static final Map<ScrollStateKey, ScrollState> SCROLL_STATES = new HashMap<>();

    private final ECOStorageSystemBlockEntity storage;
    private final Map<String, Float> animatedColumnRatios = new HashMap<>();
    private final ScrollStateKey scrollStateKey;
    private final ScrollModel leftScroll = new ScrollModel();
    private final ScrollModel columnScroll = new ScrollModel();
    private final ScrollModel matrixScroll = new ScrollModel();
    private boolean restoredScroll;
    private StorageSnapshot snapshot = StorageSnapshot.EMPTY;

    NEStorageAeCanvas(ECOStorageSystemBlockEntity storage) {
        super(UI_WIDTH, UI_HEIGHT);
        this.storage = storage;
        this.scrollStateKey = ScrollStateKey.of(storage);
        bindSnapshot();
        addEventListener(UIEvents.MOUSE_WHEEL, this::onMouseWheel);
        addEventListener(UIEvents.REMOVED, event -> saveScrollState());
        addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            HoverTooltips tooltip = tooltipAt(currentMouseX(), currentMouseY());
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
        NEHostSnapshots.decode(snapshotData, buf -> {
            this.snapshot = new StorageSnapshot(
                buf.readBoolean(),
                Math.max(0L, buf.readVarLong()),
                Math.max(0L, buf.readVarLong()),
                NEHostSnapshots.readTypeStats(buf),
                NEHostSnapshots.readMatrixCells(buf)
            );
            restoreScrollState();
        });
    }

    @Override
    protected void drawHostBackground(GUIContext context) {
        drawInsetRect(context, LEFT_PANEL_X, LEFT_PANEL_Y, LEFT_PANEL_W, LEFT_PANEL_H);
        drawInsetRect(context, METRIC_PANEL_X, METRIC_PANEL_Y, METRIC_PANEL_W, METRIC_PANEL_H);
        drawInsetRect(context, MATRIX_PANEL_X, MATRIX_PANEL_Y, MATRIX_PANEL_W, MATRIX_PANEL_H);

        drawHeader(context);
        drawStorageText(context);
        drawMetricColumns(context);
        drawFittedText(context, tr("gui.neoecoae.common.inventory", "Inventory"),
                PLAYER_INV_X, PLAYER_INV_LABEL_Y, PLAYER_INVENTORY_WIDTH, TEXT_MUTED);
        drawMatrix(context);
    }

    private void drawHeader(GUIContext context) {
        Component label = tr("gui.neoecoae.machine.formed", "Formed").append(": ");
        Component value = boolText(snapshot.formed());
        int width = context.mc.font.width(label) + context.mc.font.width(value);
        int x = 316 - width;
        drawFittedText(context, storage.getHostTitle(), 8, 8, Math.max(40, x - 12), TEXT_TITLE);
        drawText(context, label, x, 8, 0xFF4A4A4A);
        drawText(context, value, x + context.mc.font.width(label), 8, snapshot.formed() ? 0xFF1F9D55 : 0xFFD13F3F);
    }

    private void drawStorageText(GUIContext context) {
        List<StorageLine> lines = storageLines();
        int viewportH = LEFT_PANEL_H - 16;
        int contentH = Math.max(0, (lines.size() - 1) * TEXT_LINE_STEP + context.mc.font.lineHeight);
        leftScroll.update(contentH, viewportH);
        saveScrollState();
        context.graphics.flush();
        context.enableScissor(absX(LEFT_PANEL_X + 4), absY(LEFT_PANEL_Y + 4), LEFT_PANEL_W - 8, LEFT_PANEL_H - 8);
        float y = TEXT_START_Y - leftScroll.offset();
        for (StorageLine line : lines) {
            line.draw(this, context, TEXT_START_X, y, LEFT_PANEL_W - 18);
            y += TEXT_LINE_STEP;
        }
        context.graphics.flush();
        context.disableScissor();
        if (contentH > viewportH) {
            float trackH = LEFT_PANEL_H - 10;
            float thumbH = Math.max(12.0F, trackH * viewportH / contentH);
            float thumbY = LEFT_PANEL_Y + 5 + (trackH - thumbH) * leftScroll.offset() / Math.max(1.0F, contentH - viewportH);
            drawScroller(context, LEFT_PANEL_X + LEFT_PANEL_W - 5, LEFT_PANEL_Y + 5, 2, trackH, thumbY, thumbH);
        }
    }

    private List<StorageLine> storageLines() {
        List<StorageLine> lines = new ArrayList<>();
        lines.add(StorageLine.plain(tr("gui.neoecoae.storage.energy", "Energy Monitor"), TEXT_PRIMARY));
        lines.add(StorageLine.usedTotal(
                trString("gui.neoecoae.storage.energy_storage", "Energy Storage") + ": ",
                NEHostFormat.number(snapshot.storedEnergy()),
                NEHostFormat.number(snapshot.maxEnergy()),
                snapshot.storedEnergy(),
                snapshot.maxEnergy(),
                "AE"));
        for (StorageMetric metric : storageMetrics()) {
            lines.add(StorageLine.plain(metric.label(), metric.accentColor()));
            lines.add(StorageLine.usedTotal(
                    "",
                    NEHostFormat.number(metric.usedTypes()),
                    NEHostFormat.number(metric.totalTypes()),
                    metric.usedTypes(),
                    metric.totalTypes(),
                    trString("gui.neoecoae.common.types", "Types")));
            lines.add(StorageLine.usedTotal(
                    "",
                    NEHostFormat.bytes(metric.usedBytes()),
                    NEHostFormat.bytes(metric.totalBytes()),
                    metric.usedBytes(),
                    metric.totalBytes(),
                    trString("gui.neoecoae.storage.bytes_used", "bytes used")));
        }
        return lines;
    }

    private void drawMetricColumns(GUIContext context) {
        List<StorageMetric> stats = columnStats();
        int contentW = stats.isEmpty() ? 0 : stats.size() * COLUMN_W + (stats.size() - 1) * COLUMN_GAP;
        columnScroll.update(contentW, COLUMN_VIEW_W);
        saveScrollState();
        if (contentW > COLUMN_VIEW_W) {
            float thumbW = Math.max(12.0F, COLUMN_VIEW_W * COLUMN_VIEW_W / contentW);
            float thumbX = COLUMN_VIEW_X + (COLUMN_VIEW_W - thumbW) * columnScroll.offset() / Math.max(1.0F, columnScroll.max());
            fillLocal(context, COLUMN_VIEW_X, COLUMN_SCROLLBAR_Y, COLUMN_VIEW_W, COLUMN_SCROLLBAR_H, PANEL_OUTER);
            fillLocal(context, thumbX, COLUMN_SCROLLBAR_Y, thumbW, COLUMN_SCROLLBAR_H, PANEL_EDGE);
        }
        animatedColumnRatios.keySet().removeIf(key -> stats.stream().noneMatch(stat -> stat.key().equals(key)));
        float startX = contentW <= COLUMN_VIEW_W ? COLUMN_VIEW_X + (COLUMN_VIEW_W - contentW) / 2.0F : COLUMN_VIEW_X - columnScroll.offset();
        context.graphics.flush();
        context.enableScissor(absX(COLUMN_VIEW_X), absY(METRIC_PANEL_Y + 18), COLUMN_VIEW_W, METRIC_PANEL_H - 23);
        for (int i = 0; i < stats.size(); i++) {
            StorageMetric stat = stats.get(i);
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

    private void drawColumn(GUIContext context, StorageMetric stat, float x, float ratio) {
        String label = fit(context, stat.label().getString(), COLUMN_W + 18);
        drawCenteredText(context, label, x - 9, COLUMN_Y - 14, COLUMN_W + 18, TEXT_PRIMARY);
        drawSmallInsetRect(context, x, COLUMN_Y, COLUMN_W, COLUMN_H);
        float ix = x + 5;
        float iy = COLUMN_Y + 6;
        float iw = COLUMN_W - 10;
        float ih = COLUMN_H - 12;
        fillLocal(context, ix, iy, iw, ih, 0xAA17141E);
        long used = stat.usedBytes();
        long total = stat.totalBytes();
        int fillH = Math.round(ih * ratio);
        if (fillH > 0) {
            int color = metricColor(stat.accentColor(), total, ratio);
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
        drawCenteredText(context, percent, x - 2, COLUMN_Y + COLUMN_H + 8, COLUMN_W + 4, total <= 0 ? TEXT_MUTED : metricColor(stat.accentColor(), total, ratio));
    }

    private void drawMatrix(GUIContext context) {
        List<NEStorageMatrixCell> cells = snapshot.matrixCells().stream()
                .sorted(Comparator.comparingInt(NEStorageMatrixCell::row).thenComparingInt(NEStorageMatrixCell::column))
                .toList();
        int columns = cells.stream().mapToInt(cell -> Math.max(0, cell.column()) + 1).max().orElse(0);
        int contentW = columns <= 0 ? 0 : (columns - 1) * MATRIX_CARD_STRIDE + MATRIX_CARD_W;
        matrixScroll.update(contentW, MATRIX_VIEW_W);
        saveScrollState();
        if (contentW > MATRIX_VIEW_W) {
            float thumbW = Math.max(12.0F, MATRIX_VIEW_W * MATRIX_VIEW_W / contentW);
            float thumbX = MATRIX_VIEW_X + (MATRIX_VIEW_W - thumbW) * matrixScroll.offset() / Math.max(1.0F, matrixScroll.max());
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
            float x = MATRIX_VIEW_X + cell.column() * MATRIX_CARD_STRIDE - matrixScroll.offset();
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
        boolean hovered = containsLocal(Math.max(x, MATRIX_VIEW_X), y, Math.min(x + MATRIX_CARD_W, MATRIX_VIEW_X + MATRIX_VIEW_W) - Math.max(x, MATRIX_VIEW_X), MATRIX_CARD_H, currentMouseX(), currentMouseY());
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

    private void onMouseWheel(UIEvent event) {
        float mouseX = currentMouseX();
        float mouseY = currentMouseY();
        if (containsLocal(LEFT_PANEL_X, LEFT_PANEL_Y, LEFT_PANEL_W, LEFT_PANEL_H, mouseX, mouseY)) {
            int viewportH = LEFT_PANEL_H - 16;
            int contentH = Math.max(0, storageLines().size() * TEXT_LINE_STEP);
            leftScroll.scrollBy((float) -event.deltaY * 13.0F, contentH, viewportH, false);
            saveScrollState();
            event.stopPropagation();
            return;
        }
        if (containsLocal(METRIC_PANEL_X, METRIC_PANEL_Y, METRIC_PANEL_W, METRIC_PANEL_H, mouseX, mouseY)) {
            List<StorageMetric> stats = columnStats();
            int contentW = stats.isEmpty() ? 0 : stats.size() * COLUMN_W + (stats.size() - 1) * COLUMN_GAP;
            columnScroll.scrollBy((float) -event.deltaY * 18.0F, contentW, COLUMN_VIEW_W, true);
            saveScrollState();
            event.stopPropagation();
            return;
        }
        if (containsLocal(MATRIX_PANEL_X, MATRIX_PANEL_Y, MATRIX_PANEL_W, MATRIX_PANEL_H, mouseX, mouseY)) {
            int columns = snapshot.matrixCells().stream()
                    .mapToInt(cell -> Math.max(0, cell.column()) + 1)
                    .max()
                    .orElse(0);
            int contentW = columns <= 0 ? 0 : (columns - 1) * MATRIX_CARD_STRIDE + MATRIX_CARD_W;
            matrixScroll.scrollBy((float) -event.deltaY * 18.0F, contentW, MATRIX_VIEW_W, true);
            saveScrollState();
            event.stopPropagation();
        }
    }

    private void restoreScrollState() {
        if (restoredScroll) {
            return;
        }
        restoredScroll = true;
        ScrollState state = SCROLL_STATES.get(scrollStateKey);
        if (state == null) {
            return;
        }
        leftScroll.restore(state.leftScroll());
        columnScroll.restore(state.columnScroll());
        matrixScroll.restore(state.matrixScroll());
    }

    private void saveScrollState() {
        SCROLL_STATES.put(scrollStateKey, new ScrollState(leftScroll.target(), columnScroll.target(), matrixScroll.target()));
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
            float x = MATRIX_VIEW_X + cell.column() * MATRIX_CARD_STRIDE - matrixScroll.offset();
            float y = MATRIX_CARD_FIRST_Y + cell.row() * MATRIX_ROW_STEP;
            float clippedX = Math.max(x, MATRIX_VIEW_X);
            float clippedW = Math.min(x + MATRIX_CARD_W, MATRIX_VIEW_X + MATRIX_VIEW_W) - clippedX;
            if (clippedW <= 0 || !containsLocal(clippedX, y, clippedW, MATRIX_CARD_H, mouseX, mouseY)) {
                continue;
            }
            List<Component> lines = new ArrayList<>();
            lines.add(tr("gui.neoecoae.storage.matrix_card.title", "%s Storage", tierName(cell.tier())).withStyle(ChatFormatting.AQUA));
            lines.add(tr("gui.neoecoae.host.metric.types", "Types")
                    .append(": ")
                    .append(Component.literal(NEHostFormat.usedTotal(cell.usedTypes(), cell.totalTypes())).withStyle(ChatFormatting.WHITE)));
            lines.add(tr("gui.neoecoae.host.metric.bytes", "Bytes")
                    .append(": ")
                    .append(Component.literal(NEHostFormat.usedTotalBytes(cell.usedBytes(), cell.totalBytes())).withStyle(ChatFormatting.WHITE)));
            return new HoverTooltips(lines, null, null, null);
        }
        return null;
    }

    private HoverTooltips columnTooltip(double mouseX, double mouseY) {
        List<StorageMetric> stats = columnStats();
        int contentW = stats.isEmpty() ? 0 : stats.size() * COLUMN_W + (stats.size() - 1) * COLUMN_GAP;
        float startX = contentW <= COLUMN_VIEW_W ? COLUMN_VIEW_X + (COLUMN_VIEW_W - contentW) / 2.0F : COLUMN_VIEW_X - columnScroll.offset();
        for (int i = 0; i < stats.size(); i++) {
            StorageMetric stat = stats.get(i);
            float x = startX + i * (COLUMN_W + COLUMN_GAP);
            float clippedX = Math.max(x, COLUMN_VIEW_X);
            float clippedW = Math.min(x + COLUMN_W, COLUMN_VIEW_X + COLUMN_VIEW_W) - clippedX;
            if (clippedW <= 0 || !containsLocal(clippedX, COLUMN_Y, clippedW, COLUMN_H, mouseX, mouseY)) {
                continue;
            }
            return new HoverTooltips(List.of(
                    stat.label(),
                    tr("gui.neoecoae.host.metric.bytes", "Bytes")
                            .append(": ")
                            .append(Component.literal(NEHostFormat.usedTotalBytes(stat.usedBytes(), stat.totalBytes())).withStyle(ChatFormatting.WHITE)),
                    tr("gui.neoecoae.host.metric.types", "Types")
                            .append(": ")
                            .append(Component.literal(NEHostFormat.usedTotal(stat.usedTypes(), stat.totalTypes())).withStyle(ChatFormatting.WHITE))
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

    private List<StorageMetric> columnStats() {
        List<StorageMetric> metrics = storageMetrics();
        List<StorageMetric> stats = metrics.stream()
                .filter(stat -> stat.totalBytes() > 0 || stat.totalTypes() > 0)
                .toList();
        return stats.isEmpty() ? metrics : stats;
    }

    private List<StorageMetric> storageMetrics() {
        List<NEStorageTypeStat> stats = snapshot.typeStats();
        List<StorageMetric> metrics = new ArrayList<>();
        NEStorageTypeStat itemStat = findTypeStat(stats, "item");
        NEStorageTypeStat fluidStat = findTypeStat(stats, "fluid");
        metrics.add(createMetric("neoecoae:items", itemStat, tr("gui.neoecoae.storage.items", "Items"), 0xFF43B678));
        metrics.add(createMetric("neoecoae:fluids", fluidStat, tr("gui.neoecoae.storage.fluids", "Fluids"), 0xFF3A8FD6));
        for (NEStorageTypeStat stat : stats) {
            if (matchesTypeStat(stat, "item") || matchesTypeStat(stat, "fluid")) {
                continue;
            }
            metrics.add(createMetric(stat.typeId().toString(), stat, stat.displayName(), typeAccentColor(stat, metrics.size())));
        }
        return List.copyOf(metrics);
    }

    private static StorageMetric createMetric(String key, NEStorageTypeStat stat, Component fallbackLabel, int accentColor) {
        if (stat == null) {
            return new StorageMetric(key, fallbackLabel, 0L, 0L, 0L, 0L, accentColor);
        }
        return new StorageMetric(
                key,
                fallbackLabel,
                stat.usedBytes(),
                stat.totalBytes(),
                stat.usedTypes(),
                stat.totalTypes(),
                accentColor);
    }

    private static NEStorageTypeStat findTypeStat(List<NEStorageTypeStat> stats, String needle) {
        String lowerNeedle = needle.toLowerCase(Locale.ROOT);
        String pluralNeedle = lowerNeedle + "s";
        for (NEStorageTypeStat stat : stats) {
            String path = stat.typeId().getPath().toLowerCase(Locale.ROOT);
            if (path.equals(lowerNeedle) || path.equals(pluralNeedle)) {
                return stat;
            }
        }
        for (NEStorageTypeStat stat : stats) {
            String path = stat.typeId().getPath().toLowerCase(Locale.ROOT);
            String name = stat.displayName().getString().toLowerCase(Locale.ROOT);
            if (path.contains(lowerNeedle) || name.contains(lowerNeedle)) {
                return stat;
            }
        }
        return null;
    }

    private static boolean matchesTypeStat(NEStorageTypeStat stat, String needle) {
        String lowerNeedle = needle.toLowerCase(Locale.ROOT);
        String pluralNeedle = lowerNeedle + "s";
        String path = stat.typeId().getPath().toLowerCase(Locale.ROOT);
        return path.equals(lowerNeedle) || path.equals(pluralNeedle);
    }

    private static int typeAccentColor(NEStorageTypeStat stat, int index) {
        String path = stat.typeId().getPath().toLowerCase(Locale.ROOT);
        String name = stat.displayName().getString().toLowerCase(Locale.ROOT);
        if (containsAny(path, name, "chemical", "chem", "gas", "infuse", "infusion", "pigment", "slurry")) {
            return 0xFF9A6AE8;
        }
        if (containsAny(path, name, "flux", "fe", "energy")) {
            return 0xFFE8A84A;
        }
        if (containsAny(path, name, "mana")) {
            return 0xFF33B6D8;
        }
        if (containsAny(path, name, "source")) {
            return 0xFFB66AE8;
        }
        int[] palette = {0xFFE06C75, 0xFF61AFEF, 0xFF98C379, 0xFFD19A66, 0xFFC678DD};
        return palette[Math.floorMod(index, palette.length)];
    }

    private static boolean containsAny(String path, String name, String... needles) {
        for (String needle : needles) {
            if (path.contains(needle) || name.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private float animatedColumnRatio(StorageMetric stat) {
        long total = stat.totalBytes();
        float target = total <= 0L ? 0.0F : Mth.clamp((float) stat.usedBytes() / (float) total, 0.0F, 1.0F);
        String key = stat.key();
        float current = animatedColumnRatios.getOrDefault(key, 0.0F);
        float next = approach(current, target);
        animatedColumnRatios.put(key, next);
        return next;
    }

    private static int usedValueColor(long used, long max) {
        if (used <= 0 || max <= 0) {
            return 0xFF00FC00;
        }
        double pct = (double) used / (double) max;
        if (pct >= 1.0D) {
            return TEXT_ERROR;
        }
        if (pct >= 0.9D) {
            return 0xFFFF9A3D;
        }
        if (pct >= 0.75D) {
            return TEXT_WARNING;
        }
        return 0xFF00FC00;
    }

    private static int metricColor(int accentColor, long max, double pct) {
        if (max <= 0L) {
            return TEXT_MUTED;
        }
        return lerpColor(darken(accentColor, 0.72D), accentColor, Mth.clamp(pct + 0.2D, 0.0D, 1.0D));
    }

    private static int darken(int color, double factor) {
        int a = (color >>> 24) & 0xFF;
        int r = (int) (((color >>> 16) & 0xFF) * factor);
        int g = (int) (((color >>> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpColor(int start, int end, double t) {
        double safeT = Mth.clamp(t, 0.0D, 1.0D);
        int a = (int) Mth.lerp(safeT, (start >>> 24) & 0xFF, (end >>> 24) & 0xFF);
        int r = (int) Mth.lerp(safeT, (start >>> 16) & 0xFF, (end >>> 16) & 0xFF);
        int g = (int) Mth.lerp(safeT, (start >>> 8) & 0xFF, (end >>> 8) & 0xFF);
        int b = (int) Mth.lerp(safeT, start & 0xFF, end & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float approach(float current, float target) {
        float next = Mth.lerp(0.16F, current, target);
        return Math.abs(next - target) < 0.05F ? target : next;
    }

    private record StorageMetric(String key, Component label, long usedBytes, long totalBytes, long usedTypes, long totalTypes, int accentColor) {
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

    private record ScrollState(float leftScroll, float columnScroll, float matrixScroll) {
    }

    private record ScrollStateKey(ResourceLocation dimension, BlockPos pos) {
        private static ScrollStateKey of(ECOStorageSystemBlockEntity storage) {
            Level level = storage.getLevel();
            ResourceLocation dimension = level == null ? ResourceLocation.withDefaultNamespace("overworld") : dimension(level.dimension());
            return new ScrollStateKey(dimension, storage.getBlockPos());
        }

        private static ResourceLocation dimension(ResourceKey<Level> dimension) {
            return dimension.location();
        }
    }

    private sealed interface StorageLine permits PlainStorageLine, UsedTotalStorageLine {
        void draw(NEStorageAeCanvas canvas, GUIContext context, float x, float y, int maxWidth);

        static StorageLine plain(Component text, int color) {
            return new PlainStorageLine(text, color);
        }

        static StorageLine usedTotal(String prefix, String usedText, String maxText, long used, long max, String suffix) {
            return new UsedTotalStorageLine(prefix, usedText, maxText, used, max, suffix);
        }
    }

    private record PlainStorageLine(Component text, int color) implements StorageLine {
        @Override
        public void draw(NEStorageAeCanvas canvas, GUIContext context, float x, float y, int maxWidth) {
            canvas.drawFittedText(context, text, x, y, maxWidth, color);
        }
    }

    private record UsedTotalStorageLine(String prefix, String usedText, String maxText, long used, long max, String suffix) implements StorageLine {
        @Override
        public void draw(NEStorageAeCanvas canvas, GUIContext context, float x, float y, int maxWidth) {
            String safeSuffix = suffix == null || suffix.isBlank() ? "" : " " + suffix;
            int fullWidth = context.mc.font.width(prefix + usedText + " / " + maxText + safeSuffix);
            String renderedSuffix = safeSuffix;
            if (fullWidth > maxWidth && !safeSuffix.isEmpty()) {
                renderedSuffix = " " + NEStorageAeCanvas.trString("gui.neoecoae.storage.used_short", "used");
            }
            int cursor = 0;
            cursor += canvas.drawSegment(context, prefix, x + cursor, y, TEXT_MUTED);
            cursor += canvas.drawSegment(context, usedText, x + cursor, y, usedValueColor(used, max));
            cursor += canvas.drawSegment(context, " / ", x + cursor, y, TEXT_MUTED);
            cursor += canvas.drawSegment(context, maxText, x + cursor, y, TEXT_VALUE);
            if (!renderedSuffix.isBlank()) {
                canvas.drawSegment(context, renderedSuffix, x + cursor, y, TEXT_MUTED);
            }
        }
    }

    private int drawSegment(GUIContext context, String text, float x, float y, int color) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        drawText(context, text, x, y, color);
        return context.mc.font.width(text);
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
