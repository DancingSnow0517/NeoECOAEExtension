package cn.dancingsnow.neoecoae.client.gui.ldlib.storage;

import static cn.dancingsnow.neoecoae.gui.ldlib.storage.NEStorageLayout.*;

import cn.dancingsnow.neoecoae.client.gui.ldlib.NELDLibClientStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.storage.NEStorageAnimatedRatio;
import cn.dancingsnow.neoecoae.gui.ldlib.storage.NEStorageMetricsModel;
import cn.dancingsnow.neoecoae.gui.ldlib.storage.NEStorageMetricsModel.Metric;
import cn.dancingsnow.neoecoae.gui.ldlib.storage.NEStorageMetricsModel.StorageMetrics;
import cn.dancingsnow.neoecoae.gui.ldlib.storage.NEStorageTextFormatter;
import cn.dancingsnow.neoecoae.gui.ldlib.storage.NEStorageUsageModel;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibAe2StyleRenderer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibValueText;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.IntUnaryOperator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Renderer and input coordinator for the right-hand storage usage panel. */
public final class NEStorageUsagePanel {
    private static final float DETAIL_TEXT_SCALE = 8.0F / 9.0F;

    private final NEStorageAnimatedRatio usageAnimation = new NEStorageAnimatedRatio();
    private final NEStorageHugeStackList hugeStackList = new NEStorageHugeStackList();

    public void drawBackground(
            GuiGraphics g,
            IntUnaryOperator screenX,
            IntUnaryOperator screenY,
            NEStorageUiState state,
            int mouseX,
            int mouseY) {
        NEStorageHostTextures.drawPanel(
                g,
                screenX.applyAsInt(USAGE_PANEL_X),
                screenY.applyAsInt(USAGE_PANEL_Y),
                USAGE_PANEL_W,
                USAGE_PANEL_H,
                mouseX,
                mouseY);
        double usage = usageAnimation.update(
                state.migratingToInfinite() ? 1.0D : NEStorageGaugeRenderer.totalUsagePercent(state));
        NELDLibClientStyle.drawTinyInsetRect(
                g,
                screenX.applyAsInt(USAGE_DARK_X),
                screenY.applyAsInt(USAGE_DARK_Y),
                USAGE_DARK_W,
                USAGE_DARK_H,
                0xFF201E27);
        int x = screenX.applyAsInt(STORAGE_GAUGE_X);
        int y = screenY.applyAsInt(STORAGE_GAUGE_Y);
        if (state.infiniteMode() && !state.migratingToInfinite()) {
            NEStorageGaugeRenderer.drawInfinite(g, x, y, state, NEStorageMetricsModel.from(state));
        } else {
            NEStorageGaugeRenderer.drawFinite(g, x, y, usage, NEStorageGaugeRenderer.colorForPercent(usage));
        }
        if (state.infiniteSlotVisible()) {
            NELDLibAe2StyleRenderer.drawAeSlot(
                    g, screenX.applyAsInt(INFINITE_SLOT_X), screenY.applyAsInt(INFINITE_SLOT_Y));
        }
    }

    public void drawForeground(
            GuiGraphics g, Font font, IntUnaryOperator screenX, IntUnaryOperator screenY, NEStorageUiState state) {
        StorageMetrics metrics = NEStorageMetricsModel.from(state);
        double usage = usageAnimation.update(
                state.migratingToInfinite() ? 1.0D : NEStorageGaugeRenderer.totalUsagePercent(state));
        NEStorageUsageModel.MatrixLoad highestMatrix = NEStorageUsageModel.highestMatrixLoad(state);
        NELDLibClientStyle.drawCentered(
                g,
                font,
                Component.translatable("gui.neoecoae.storage.system_load"),
                screenX.applyAsInt(USAGE_CONTENT_X),
                screenY.applyAsInt(USAGE_PANEL_Y + 2 + USAGE_CONTENT_SHIFT_Y),
                USAGE_CONTENT_W,
                NELDLibStyle.DARK_TEXT_PRIMARY);

        int y = USAGE_DETAIL_Y;
        drawDetailLine(
                g,
                font,
                screenX,
                screenY,
                Component.translatable("gui.neoecoae.storage.current_load")
                        .append(": ")
                        .append(Component.literal(NELDLibText.percentOrNA(state.totalUsedBytes(), state.totalBytes()))),
                y,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        y += USAGE_DETAIL_LINE_H;
        drawDetailLine(
                g,
                font,
                screenX,
                screenY,
                Component.translatable("gui.neoecoae.storage.max_load")
                        .append(": ")
                        .append(Component.literal(
                                state.infiniteMode()
                                        ? "MAX"
                                        : NELDLibText.percentOrNA(highestMatrix.used(), highestMatrix.total()))),
                y,
                state.infiniteMode() ? 0x22CA6CFF : NELDLibStyle.DARK_TEXT_WARNING);
        y += USAGE_DETAIL_LINE_H;
        Metric highestType = state.infiniteMode() ? null : highestPressureMetric(metrics);
        drawDetailLine(
                g,
                font,
                screenX,
                screenY,
                Component.translatable("gui.neoecoae.storage.status")
                        .append(": ")
                        .append(
                                state.infiniteMode()
                                        ? Component.translatable("gui.neoecoae.storage.infinite_value")
                                        : storageStatus(highestType)),
                y,
                state.infiniteMode() ? 0x22CA6CFF : statusColor(highestType));
        y += USAGE_DETAIL_LINE_H;
        drawDetailLine(
                g,
                font,
                screenX,
                screenY,
                Component.translatable("gui.neoecoae.storage.idle_matrices")
                        .append(": ")
                        .append(Component.literal(NELDLibText.number(NEStorageUsageModel.idleMatrixCount(state)))),
                y,
                NELDLibStyle.DARK_TEXT_MUTED);

        NELDLibClientStyle.drawCenteredScaledString(
                g,
                font,
                state.migratingToInfinite()
                        ? NELDLibText.percent(state.infiniteMigrationProgress() / 100.0D)
                        : state.infiniteMode()
                                ? infiniteText()
                                : state.totalBytes() <= 0L ? "N/A" : NELDLibText.percent(usage),
                screenX.applyAsInt(STORAGE_GAUGE_X),
                screenY.applyAsInt(USAGE_PERCENT_Y),
                STORAGE_GAUGE_W,
                8,
                state.infiniteMode()
                        ? 0xFFCA6CFF
                        : state.totalBytes() <= 0L
                                ? NELDLibStyle.DARK_TEXT_MUTED
                                : NELDLibStyle.usedValueColor(
                                        Math.round(usage * state.totalBytes()), state.totalBytes()),
                0.9F);
        hugeStackList.draw(g, font, screenX, screenY, state);
        drawInfiniteSlotOverlay(g, screenX, screenY, state);
    }

    public boolean drawTooltip(
            GuiGraphics g,
            Font font,
            IntUnaryOperator screenX,
            IntUnaryOperator screenY,
            NEStorageUiState state,
            int mouseX,
            int mouseY) {
        if (hugeStackList.drawTooltip(g, font, screenX, screenY, state, mouseX, mouseY)) {
            return true;
        }
        if (!isInside(screenX, screenY, USAGE_DARK_X, USAGE_DARK_Y, USAGE_DARK_W, USAGE_DARK_H, mouseX, mouseY)) {
            return false;
        }
        if (NEStorageHugeStackList.isVisible(state) && hugeStackList.contains(screenX, screenY, mouseX, mouseY)) {
            return false;
        }
        if (state.infiniteMode()) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.neoecoae.storage.system_load").withStyle(ChatFormatting.AQUA));
            lines.add(Component.literal(NELDLibText.preciseHugeAmount(
                            NEStorageTextFormatter.totalInfiniteAmount(state).toString()))
                    .withStyle(style -> style.withColor(NELDLibStyle.DARK_TEXT_USED))
                    .append(Component.literal(" "
                                    + Component.translatable("gui.neoecoae.storage.bytes_used")
                                            .getString())
                            .withStyle(style -> style.withColor(NELDLibStyle.DARK_TEXT_MUTED))));
            lines.add(Component.literal(
                            Component.translatable("gui.neoecoae.common.types").getString()
                                    + ": "
                                    + NELDLibText.number(state.totalUsedTypes()))
                    .withStyle(ChatFormatting.GRAY));
            lines.addAll(typeCountTooltipLines(NEStorageMetricsModel.from(state)));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
            return true;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.neoecoae.storage.system_load").withStyle(ChatFormatting.AQUA));
        lines.add(NELDLibValueText.usedTotalComponent(
                "",
                NELDLibText.storageBytes(state.totalUsedBytes()),
                state.totalBytes() == Long.MAX_VALUE ? infiniteText() : NELDLibText.storageBytes(state.totalBytes()),
                state.totalUsedBytes(),
                finiteMax(state.totalBytes()),
                Component.translatable("gui.neoecoae.storage.bytes_used").getString()));
        lines.add(Component.translatable(
                "gui.neoecoae.machine.types_value",
                NELDLibText.number(state.totalUsedTypes()),
                state.totalTypes() == Long.MAX_VALUE ? infiniteText() : NELDLibText.number(state.totalTypes())));
        lines.addAll(typeCountTooltipLines(NEStorageMetricsModel.from(state)));
        g.renderComponentTooltip(font, lines, mouseX, mouseY);
        return true;
    }

    public boolean mouseWheel(
            IntUnaryOperator screenX,
            IntUnaryOperator screenY,
            NEStorageUiState state,
            double mouseX,
            double mouseY,
            double wheelDelta) {
        return NEStorageHugeStackList.isVisible(state)
                && hugeStackList.contains(screenX, screenY, mouseX, mouseY)
                && hugeStackList.scrollBy(state, wheelDelta);
    }

    public OptionalInt pageRequestAt(
            IntUnaryOperator screenX, IntUnaryOperator screenY, NEStorageUiState state, double mouseX, double mouseY) {
        return hugeStackList.pageRequestAt(screenX, screenY, state, mouseX, mouseY);
    }

    public double targetScrollPixels() {
        return hugeStackList.targetScrollPixels();
    }

    public void restore(double rememberedScrollPixels) {
        hugeStackList.restore(rememberedScrollPixels);
    }

    private static void drawDetailLine(
            GuiGraphics g,
            Font font,
            IntUnaryOperator screenX,
            IntUnaryOperator screenY,
            Component text,
            int localY,
            int color) {
        int x = screenX.applyAsInt(USAGE_DETAIL_X);
        int y = screenY.applyAsInt(localY);
        int maxW = Math.max(1, USAGE_DETAIL_W - 2);
        g.enableScissor(x, y - 1, x + maxW, y + USAGE_DETAIL_LINE_H);
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(DETAIL_TEXT_SCALE, DETAIL_TEXT_SCALE, 1.0F);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
        g.disableScissor();
    }

    private static Metric highestPressureMetric(StorageMetrics metrics) {
        Metric highest = null;
        double highestPercent = -1.0D;
        for (Metric metric : NEStorageMetricsModel.activeMetrics(metrics)) {
            if (metric.percent() > highestPercent) {
                highestPercent = metric.percent();
                highest = metric;
            }
        }
        return highest;
    }

    private static Component storageStatus(Metric highestType) {
        return highestType != null && highestType.percent() >= 0.999D
                ? Component.translatable("gui.neoecoae.storage.status.capacity_full", highestType.label())
                : Component.translatable("gui.neoecoae.storage.status.ok");
    }

    private static int statusColor(Metric highestType) {
        return highestType != null && highestType.percent() >= 0.999D
                ? NELDLibStyle.DARK_TEXT_WARNING
                : NELDLibStyle.DARK_TEXT_MUTED;
    }

    private static List<Component> typeCountTooltipLines(StorageMetrics metrics) {
        List<Component> lines = new ArrayList<>();
        String typesLabel = Component.translatable("gui.neoecoae.common.types").getString();
        for (Metric metric : NEStorageMetricsModel.activeMetrics(metrics)) {
            if (metric.usedTypes() > 0L) {
                lines.add(Component.empty()
                        .append(metric.label().copy().withStyle(style -> style.withColor(metric.accentColor())))
                        .append(Component.literal(" " + typesLabel + ": " + NELDLibText.number(metric.usedTypes()))
                                .withStyle(ChatFormatting.GRAY)));
            }
        }
        return lines;
    }

    private static void drawInfiniteSlotOverlay(
            GuiGraphics g, IntUnaryOperator screenX, IntUnaryOperator screenY, NEStorageUiState state) {
        if (!state.infiniteSlotVisible() || state.canTakeInfiniteComponent()) {
            return;
        }
        int x = screenX.applyAsInt(INFINITE_SLOT_X);
        int y = screenY.applyAsInt(INFINITE_SLOT_Y);
        g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0x88404040);
    }

    private static boolean isInside(
            IntUnaryOperator screenX,
            IntUnaryOperator screenY,
            int localX,
            int localY,
            int width,
            int height,
            double mouseX,
            double mouseY) {
        int x = screenX.applyAsInt(localX);
        int y = screenY.applyAsInt(localY);
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static String infiniteText() {
        return Component.translatable("gui.neoecoae.storage.infinite_value").getString();
    }

    private static long finiteMax(long max) {
        return max == Long.MAX_VALUE ? 0L : max;
    }
}
