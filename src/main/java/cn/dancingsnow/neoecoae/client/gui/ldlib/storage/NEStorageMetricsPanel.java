package cn.dancingsnow.neoecoae.client.gui.ldlib.storage;

import static cn.dancingsnow.neoecoae.gui.ldlib.storage.NEStorageLayout.*;

import cn.dancingsnow.neoecoae.client.gui.ldlib.NELDLibClientStyle;
import cn.dancingsnow.neoecoae.client.gui.ldlib.host.NEHostTextures;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.storage.NEStorageMetricsModel;
import cn.dancingsnow.neoecoae.gui.ldlib.storage.NEStorageMetricsModel.Metric;
import cn.dancingsnow.neoecoae.gui.ldlib.storage.NEStorageMetricsModel.StorageMetrics;
import cn.dancingsnow.neoecoae.gui.ldlib.storage.NEStorageScrollbar;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibValueText;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import java.util.List;
import java.util.function.IntUnaryOperator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Stateful renderer and input handler for the left-hand storage metrics panel. */
public final class NEStorageMetricsPanel {
    private static final double SCROLL_SPEED = 13.0D;

    private double scrollPixels;
    private boolean scrollbarDragging;
    private double scrollbarDragStartY;
    private double scrollbarDragStartScroll;

    public void drawBackground(
            GuiGraphics g,
            IntUnaryOperator screenX,
            IntUnaryOperator screenY,
            NEStorageUiState state,
            int mouseX,
            int mouseY) {
        NEHostTextures.drawPanel(
                g,
                screenX.applyAsInt(LEFT_PANEL_X),
                screenY.applyAsInt(LEFT_PANEL_Y),
                LEFT_PANEL_W,
                panelHeight(state),
                mouseX,
                mouseY);
    }

    public void draw(
            GuiGraphics g,
            Font font,
            IntUnaryOperator screenX,
            IntUnaryOperator screenY,
            NEStorageUiState state,
            int mouseX,
            int mouseY) {
        StorageMetrics metrics = NEStorageMetricsModel.from(state);
        scrollPixels = Mth.clamp(scrollPixels, 0.0D, maxScrollPixels(font, state, metrics));
        List<Metric> activeMetrics = NEStorageMetricsModel.activeMetrics(metrics);
        int x = screenX.applyAsInt(TEXT_START_X);
        int y = screenY.applyAsInt(TEXT_START_Y - (int) Math.round(scrollPixels));
        g.enableScissor(
                screenX.applyAsInt(LEFT_PANEL_X + 4),
                screenY.applyAsInt(LEFT_PANEL_Y + 4),
                screenX.applyAsInt(LEFT_PANEL_X + LEFT_CONTENT_W),
                screenY.applyAsInt(LEFT_PANEL_Y + panelHeight(state) - 4));

        g.drawString(
                font,
                Component.translatable("gui.neoecoae.storage.energy"),
                x,
                y,
                NELDLibStyle.DARK_TEXT_PRIMARY,
                false);
        y += TEXT_LINE_STEP;
        drawEnergyLine(g, font, metrics.energy(), x, y);
        y += TEXT_LINE_STEP;
        for (Metric metric : activeMetrics) {
            y = drawTypeBlock(g, font, metric, x, y, state.infiniteMode());
        }
        g.disableScissor();
        drawScrollbar(g, font, screenX, screenY, state, metrics, mouseX, mouseY);
    }

    public boolean drawTooltip(
            GuiGraphics g,
            Font font,
            IntUnaryOperator screenX,
            IntUnaryOperator screenY,
            NEStorageUiState state,
            int mouseX,
            int mouseY) {
        if (!state.infiniteMode()
                || !Widget.isMouseOver(
                        screenX.applyAsInt(LEFT_PANEL_X),
                        screenY.applyAsInt(LEFT_PANEL_Y),
                        LEFT_PANEL_W,
                        panelHeight(state),
                        mouseX,
                        mouseY)) {
            return false;
        }
        g.renderComponentTooltip(
                font,
                List.of(
                        Component.translatable("gui.neoecoae.storage.infinite_domain")
                                .withStyle(ChatFormatting.AQUA),
                        Component.literal(Component.translatable("gui.neoecoae.common.types")
                                                .getString()
                                        + ": "
                                        + NELDLibText.number(state.totalUsedTypes()))
                                .withStyle(style -> style.withColor(NELDLibStyle.DARK_TEXT_USED))),
                mouseX,
                mouseY);
        return true;
    }

    public boolean mouseWheel(
            Font font,
            IntUnaryOperator screenX,
            IntUnaryOperator screenY,
            NEStorageUiState state,
            double mouseX,
            double mouseY,
            double wheelDelta) {
        if (!Widget.isMouseOver(
                screenX.applyAsInt(LEFT_PANEL_X),
                screenY.applyAsInt(LEFT_PANEL_Y),
                LEFT_PANEL_W,
                panelHeight(state),
                mouseX,
                mouseY)) {
            return false;
        }
        double maxScroll = maxScrollPixels(font, state, NEStorageMetricsModel.from(state));
        double previous = scrollPixels;
        scrollPixels = Mth.clamp(scrollPixels - wheelDelta * SCROLL_SPEED, 0.0D, maxScroll);
        return scrollPixels != previous || maxScroll > 0.0D;
    }

    public boolean mouseClicked(
            Font font,
            IntUnaryOperator screenX,
            IntUnaryOperator screenY,
            NEStorageUiState state,
            double mouseX,
            double mouseY) {
        double maxScroll = maxScrollPixels(font, state, NEStorageMetricsModel.from(state));
        int panelHeight = panelHeight(state);
        if (maxScroll <= 0.0D
                || !Widget.isMouseOver(
                        screenX.applyAsInt(scrollbarX()),
                        screenY.applyAsInt(LEFT_PANEL_Y),
                        STORAGE_SCROLLBAR_W,
                        panelHeight,
                        mouseX,
                        mouseY)) {
            return false;
        }
        int thumbY =
                screenY.applyAsInt(LEFT_PANEL_Y) + NEStorageScrollbar.thumbOffset(scrollPixels, maxScroll, panelHeight);
        if (!Widget.isMouseOver(
                screenX.applyAsInt(scrollbarX()),
                thumbY,
                STORAGE_SCROLLBAR_W,
                STORAGE_SCROLLBAR_THUMB_H,
                mouseX,
                mouseY)) {
            scrollPixels = NEStorageScrollbar.scrollFromMouse(
                    mouseY, screenY.applyAsInt(LEFT_PANEL_Y), panelHeight, maxScroll);
        }
        scrollbarDragging = true;
        scrollbarDragStartY = mouseY;
        scrollbarDragStartScroll = scrollPixels;
        return true;
    }

    public boolean mouseDragged(NEStorageUiState state, Font font, double mouseY) {
        if (!scrollbarDragging) {
            return false;
        }
        double maxScroll = maxScrollPixels(font, state, NEStorageMetricsModel.from(state));
        int travel = Math.max(1, panelHeight(state) - STORAGE_SCROLLBAR_THUMB_H);
        scrollPixels = Mth.clamp(
                scrollbarDragStartScroll + (mouseY - scrollbarDragStartY) * maxScroll / travel, 0.0D, maxScroll);
        return true;
    }

    public boolean mouseReleased() {
        if (!scrollbarDragging) {
            return false;
        }
        scrollbarDragging = false;
        return true;
    }

    public double scrollPixels() {
        return scrollPixels;
    }

    public void restore(double rememberedScrollPixels) {
        scrollPixels = Math.max(0.0D, rememberedScrollPixels);
    }

    public static int panelHeight(NEStorageUiState state) {
        return state.infiniteSlotVisible() ? LEFT_PANEL_H_INFINITE : LEFT_PANEL_H;
    }

    private void drawScrollbar(
            GuiGraphics g,
            Font font,
            IntUnaryOperator screenX,
            IntUnaryOperator screenY,
            NEStorageUiState state,
            StorageMetrics metrics,
            int mouseX,
            int mouseY) {
        double maxScroll = maxScrollPixels(font, state, metrics);
        if (maxScroll <= 0.0D) {
            scrollPixels = 0.0D;
            return;
        }
        int height = panelHeight(state);
        int scrollbarX = screenX.applyAsInt(scrollbarX());
        int trackY = screenY.applyAsInt(LEFT_PANEL_Y);
        int trackX = scrollbarX + (STORAGE_SCROLLBAR_W - STORAGE_SCROLLBAR_TRACK_W) / 2;
        int thumbY = trackY + NEStorageScrollbar.thumbOffset(scrollPixels, maxScroll, height);
        NEHostTextures.drawScrollbarTrack(g, trackX, trackY, STORAGE_SCROLLBAR_TRACK_W, height, mouseX, mouseY);
        NEHostTextures.drawScrollbarThumb(
                g, scrollbarX, thumbY, STORAGE_SCROLLBAR_W, STORAGE_SCROLLBAR_THUMB_H, mouseX, mouseY);
    }

    private static double maxScrollPixels(Font font, NEStorageUiState state, StorageMetrics metrics) {
        int typeCount = NEStorageMetricsModel.activeMetrics(metrics).size();
        int lineCount = 2 + typeCount * 3;
        int contentHeight = (lineCount - 1) * TEXT_LINE_STEP + font.lineHeight;
        return Math.max(0, contentHeight - (panelHeight(state) - 16));
    }

    private static int drawTypeBlock(GuiGraphics g, Font font, Metric metric, int x, int y, boolean infiniteMode) {
        g.drawString(font, metric.label(), x, y, metric.accentColor(), false);
        y += TEXT_LINE_STEP;
        if (infiniteMode) {
            drawInfiniteLine(
                    g,
                    font,
                    NELDLibText.number(metric.usedTypes()),
                    Component.translatable("gui.neoecoae.common.types").getString(),
                    x,
                    y);
        } else {
            drawTypeLine(g, font, metric, x, y);
        }
        y += TEXT_LINE_STEP;
        if (infiniteMode) {
            drawInfiniteLine(
                    g,
                    font,
                    NELDLibText.hugeAmount(metric.usedAmount()),
                    Component.translatable("gui.neoecoae.storage.bytes_used").getString(),
                    x,
                    y);
        } else {
            drawByteLine(g, font, metric.used(), metric.max(), x, y);
        }
        return y + TEXT_LINE_STEP;
    }

    private static void drawInfiniteLine(GuiGraphics g, Font font, String used, String suffix, int x, int y) {
        int cursor = NELDLibClientStyle.drawSegment(g, font, used, x, y, NELDLibStyle.DARK_TEXT_USED);
        NELDLibClientStyle.drawSegment(g, font, " " + suffix, x + cursor, y, NELDLibStyle.DARK_TEXT_MUTED);
    }

    private static void drawEnergyLine(GuiGraphics g, Font font, Metric energy, int x, int y) {
        String prefix =
                Component.translatable("gui.neoecoae.storage.energy_storage").getString() + ": ";
        String used = NELDLibText.number(energy.used());
        String max = NELDLibText.number(energy.max());
        if (usedTotalWidth(font, prefix, used, max, "AE") > TEXT_MAX_W) {
            used = NELDLibText.compactTaskAmount(energy.used());
            max = NELDLibText.compactTaskAmount(energy.max());
        }
        NELDLibValueText.drawUsedTotal(g, font, prefix, used, max, energy.used(), energy.max(), "AE", x, y);
    }

    private static void drawTypeLine(GuiGraphics g, Font font, Metric metric, int x, int y) {
        String used = NELDLibText.number(metric.usedTypes());
        String max = metric.totalTypes() == Long.MAX_VALUE ? infiniteText() : NELDLibText.number(metric.totalTypes());
        String suffix = Component.translatable("gui.neoecoae.common.types").getString();
        if (usedTotalWidth(font, "", used, max, suffix) > TEXT_MAX_W) {
            used = NELDLibText.compactTaskAmount(metric.usedTypes());
            max = NELDLibText.compactTaskAmount(metric.totalTypes());
        }
        NELDLibValueText.drawUsedTotal(
                g, font, "", used, max, metric.usedTypes(), finiteMax(metric.totalTypes()), suffix, x, y);
    }

    private static void drawByteLine(GuiGraphics g, Font font, long usedValue, long maxValue, int x, int y) {
        String used = NELDLibText.storageBytes(usedValue);
        String max = maxValue == Long.MAX_VALUE ? infiniteText() : NELDLibText.storageBytes(maxValue);
        String suffix =
                Component.translatable("gui.neoecoae.storage.bytes_used").getString();
        if (usedTotalWidth(font, "", used, max, suffix) > TEXT_MAX_W) {
            suffix = Component.translatable("gui.neoecoae.storage.used_short").getString();
        }
        if (usedTotalWidth(font, "", used, max, suffix) > TEXT_MAX_W) {
            used = NELDLibText.storageBytesCompact(usedValue);
            max = NELDLibText.storageBytesCompact(maxValue);
        }
        NELDLibValueText.drawUsedTotal(g, font, "", used, max, usedValue, finiteMax(maxValue), suffix, x, y);
    }

    private static int usedTotalWidth(Font font, String prefix, String used, String max, String suffix) {
        return font.width(prefix + used + " / " + max) + (suffix.isEmpty() ? 0 : font.width(" " + suffix));
    }

    private static String infiniteText() {
        return Component.translatable("gui.neoecoae.storage.infinite_value").getString();
    }

    private static long finiteMax(long max) {
        return max == Long.MAX_VALUE ? 0L : max;
    }

    private static int scrollbarX() {
        return LEFT_PANEL_X + LEFT_PANEL_W;
    }
}
