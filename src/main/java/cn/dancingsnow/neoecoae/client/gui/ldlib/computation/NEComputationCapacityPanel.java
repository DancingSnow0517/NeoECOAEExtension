package cn.dancingsnow.neoecoae.client.gui.ldlib.computation;

import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.*;

import cn.dancingsnow.neoecoae.client.gui.ldlib.NELDLibClientStyle;
import cn.dancingsnow.neoecoae.client.gui.ldlib.host.NEHostTextures;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEComputationUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibTaskCards;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import java.util.List;
import java.util.Optional;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Compact LDLib1 rendering of the LDLib2 computation-capacity card. */
public final class NEComputationCapacityPanel {
    public void drawBackground(
            GuiGraphics g,
            IntUnaryOperator screenX,
            IntUnaryOperator screenY,
            NEComputationUiState state,
            int mouseX,
            int mouseY) {
        NEHostTextures.drawPanel(
                g,
                screenX.applyAsInt(CAPACITY_PANEL_X),
                screenY.applyAsInt(CAPACITY_PANEL_Y),
                CAPACITY_PANEL_W,
                CAPACITY_PANEL_H,
                mouseX,
                mouseY);
        long usedStorage = usedStorage(state);
        drawCompactProgress(
                g,
                screenX.applyAsInt(CAPACITY_CONTENT_X),
                screenY.applyAsInt(STORAGE_DETAIL_Y + 2),
                usedStorage,
                state.totalStorage());
        drawCompactProgress(
                g,
                screenX.applyAsInt(CAPACITY_CONTENT_X),
                screenY.applyAsInt(THREAD_DETAIL_Y + 2),
                state.usedThreads(),
                state.maxThreads());
    }

    public void drawForeground(
            GuiGraphics g, Font font, IntUnaryOperator screenX, IntUnaryOperator screenY, NEComputationUiState state) {
        int x = screenX.applyAsInt(CAPACITY_CONTENT_X);
        g.drawString(
                font,
                Component.translatable("gui.neoecoae.computation.capacity"),
                x,
                screenY.applyAsInt(CAPACITY_TITLE_Y),
                NELDLibStyle.DARK_TEXT_PRIMARY,
                false);
        g.drawString(
                font,
                Component.translatable("gui.neoecoae.computation.storage_used"),
                x,
                screenY.applyAsInt(STORAGE_LABEL_Y),
                NELDLibStyle.DARK_TEXT_MUTED,
                false);
        drawStoragePair(
                g,
                font,
                screenX.applyAsInt(PROGRESS_VALUE_X),
                screenY.applyAsInt(STORAGE_DETAIL_Y),
                usedStorage(state),
                state.totalStorage());
        g.drawString(
                font,
                Component.translatable("gui.neoecoae.computation.threads"),
                x,
                screenY.applyAsInt(THREAD_LABEL_Y),
                NELDLibStyle.DARK_TEXT_MUTED,
                false);
        drawNumberPair(
                g,
                font,
                screenX.applyAsInt(PROGRESS_VALUE_X),
                screenY.applyAsInt(THREAD_DETAIL_Y),
                state.usedThreads(),
                state.maxThreads());
        drawFittedLine(
                g,
                font,
                screenX,
                screenY,
                Component.translatable(
                        "gui.neoecoae.computation.accelerators", NELDLibText.number(state.accelerators())),
                ACCELERATOR_Y,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        drawAvailableStorage(g, font, screenX, screenY, state.availableStorage());
    }

    public boolean drawTooltip(
            GuiGraphics g,
            Font font,
            IntUnaryOperator screenX,
            IntUnaryOperator screenY,
            NEComputationUiState state,
            int mouseX,
            int mouseY) {
        int panelX = screenX.applyAsInt(CAPACITY_CONTENT_X);
        if (inside(panelX, screenY.applyAsInt(STORAGE_LABEL_Y), CAPACITY_CONTENT_W, 20, mouseX, mouseY)) {
            g.renderTooltip(
                    font,
                    List.of(
                            Component.translatable("gui.neoecoae.computation.storage_used"),
                            Component.literal(
                                    NELDLibText.usedTotal(usedStorage(state), state.totalStorage()) + " bytes")),
                    Optional.empty(),
                    mouseX,
                    mouseY);
            return true;
        }
        if (inside(panelX, screenY.applyAsInt(THREAD_LABEL_Y), CAPACITY_CONTENT_W, 20, mouseX, mouseY)) {
            g.renderTooltip(
                    font,
                    List.of(
                            Component.translatable("gui.neoecoae.computation.threads"),
                            Component.literal(NELDLibText.usedTotal(state.usedThreads(), state.maxThreads()))),
                    Optional.empty(),
                    mouseX,
                    mouseY);
            return true;
        }
        return false;
    }

    private static void drawCompactProgress(GuiGraphics g, int x, int y, long used, long max) {
        g.fill(x, y, x + PROGRESS_W, y + PROGRESS_H, 0xAA1F2F34);
        int fillWidth = NELDLibTaskCards.ratioWidth(used, max, PROGRESS_W - 2);
        if (fillWidth > 0) {
            g.fill(x + 1, y + 1, x + 1 + fillWidth, y + PROGRESS_H - 1, 0xFF26A6BD);
        }
    }

    private static void drawStoragePair(GuiGraphics g, Font font, int x, int y, long used, long max) {
        String usedText = NELDLibText.storageBytesCompact(used);
        String maxText = NELDLibText.storageBytesCompact(max);
        drawPair(g, font, x, y, usedText, maxText, used, max);
    }

    private static void drawNumberPair(GuiGraphics g, Font font, int x, int y, long used, long max) {
        drawPair(g, font, x, y, NELDLibText.number(used), NELDLibText.number(max), used, max);
    }

    private static void drawPair(
            GuiGraphics g, Font font, int x, int y, String usedText, String maxText, long used, long max) {
        float scale = Math.min(1.0F, 72.0F / Math.max(1, font.width(usedText + " / " + maxText)));
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        int cursor = NELDLibClientStyle.drawSegment(g, font, usedText, 0, 0, NELDLibStyle.usedValueColor(used, max));
        cursor += NELDLibClientStyle.drawSegment(g, font, " / ", cursor, 0, NELDLibStyle.DARK_TEXT_MUTED);
        NELDLibClientStyle.drawSegment(g, font, maxText, cursor, 0, NELDLibStyle.DARK_TEXT_VALUE);
        g.pose().popPose();
    }

    private static void drawFittedLine(
            GuiGraphics g,
            Font font,
            IntUnaryOperator screenX,
            IntUnaryOperator screenY,
            Component text,
            int localY,
            int color) {
        int x = screenX.applyAsInt(CAPACITY_CONTENT_X);
        int y = screenY.applyAsInt(localY);
        g.enableScissor(x, y - 1, x + CAPACITY_CONTENT_W, y + font.lineHeight + 1);
        g.drawString(font, text, x, y, color, false);
        g.disableScissor();
    }

    private static void drawAvailableStorage(
            GuiGraphics g, Font font, IntUnaryOperator screenX, IntUnaryOperator screenY, long availableStorage) {
        Component label = Component.translatable("gui.neoecoae.computation.available_storage")
                .append(": ");
        String value = NELDLibText.storageBytesCompact(availableStorage);
        int labelWidth = font.width(label);
        int fullWidth = labelWidth + font.width(value);
        float scale = Math.min(1.0F, (float) CAPACITY_CONTENT_W / Math.max(1, fullWidth));
        int x = screenX.applyAsInt(CAPACITY_CONTENT_X);
        int y = screenY.applyAsInt(FREE_STORAGE_Y);

        g.enableScissor(x, y - 1, x + CAPACITY_CONTENT_W, y + font.lineHeight + 1);
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        NELDLibClientStyle.drawSegment(g, font, label, 0, 0, NELDLibStyle.DARK_TEXT_MUTED);
        NELDLibClientStyle.drawSegment(g, font, value, labelWidth, 0, NELDLibStyle.DARK_TEXT_VALUE);
        g.pose().popPose();
        g.disableScissor();
    }

    private static long usedStorage(NEComputationUiState state) {
        return Math.max(0L, state.totalStorage() - state.availableStorage());
    }

    private static boolean inside(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
