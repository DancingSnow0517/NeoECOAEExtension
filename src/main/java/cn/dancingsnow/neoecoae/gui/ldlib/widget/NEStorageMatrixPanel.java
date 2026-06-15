package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.client.gui.ldlib.NELDLibClientStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiMatrixState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibScrollBar;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibValueText;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

final class NEStorageMatrixPanel {
    private static final int PANEL_X =
            NEStorageControllerWidget.PLAYER_INV_X + NEStorageControllerWidget.SLOT_SIZE * 9 + 4;
    private static final int PANEL_BOTTOM = 249;
    private static final int PANEL_Y = NEStorageControllerWidget.PLAYER_INV_Y;
    private static final int PANEL_W = NEStorageControllerWidget.UI_WIDTH - PANEL_X - 4;
    private static final int PANEL_H = PANEL_BOTTOM - PANEL_Y;
    private static final int VIEW_X = PANEL_X + 6;
    private static final int VIEW_W = PANEL_W - 12;
    private static final int CARD_W = 82;
    private static final int CARD_H = 18;
    private static final int CARD_GAP = 3;
    private static final int CARD_STRIDE = CARD_W + CARD_GAP;
    private static final int SCROLLBAR_Y = PANEL_Y + 5;
    private static final int SCROLLBAR_H = 4;
    private static final int CARD_FIRST_Y = SCROLLBAR_Y + SCROLLBAR_H + CARD_GAP;
    private static final int CARD_ROW_STEP = CARD_H + CARD_GAP;
    private static final int ROWS = 3;
    private static final int CARD_COLOR = 0xFF302C38;
    private static final int CARD_HOVER_COLOR = 0xFF3B3645;
    private static final double SCROLL_LERP = 0.24D;

    private final Supplier<Font> fontSupplier;
    private final IntUnaryOperator absX;
    private final IntUnaryOperator absY;
    private final DoubleSupplier scrollPixels;
    private final DoubleSupplier scrollTargetPixels;
    private final DoubleConsumer scrollPixelsSetter;
    private final DoubleConsumer scrollTargetSetter;
    private final Runnable rememberScrollState;

    NEStorageMatrixPanel(
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

    static int panelX() {
        return PANEL_X;
    }

    static int panelY() {
        return PANEL_Y;
    }

    static int panelW() {
        return PANEL_W;
    }

    static int panelH() {
        return PANEL_H;
    }

    static int viewX() {
        return VIEW_X;
    }

    static int viewW() {
        return VIEW_W;
    }

    static int scrollbarY() {
        return SCROLLBAR_Y;
    }

    static int scrollbarH() {
        return SCROLLBAR_H;
    }

    void draw(GuiGraphics graphics, NEStorageUiState state, int mouseX, int mouseY, boolean dragging) {
        double maxScroll = maxScrollPixels(state);
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

        NELDLibClientStyle.drawDarkInsetRect(graphics, absX(PANEL_X), absY(PANEL_Y), PANEL_W, PANEL_H);
        drawScrollbar(graphics, state);
        int clipLeft = absX(VIEW_X);
        int clipTop = absY(CARD_FIRST_Y);
        int clipRight = absX(VIEW_X + VIEW_W);
        int clipBottom = absY(NEStorageControllerWidget.PLAYER_HOTBAR_Y + NEStorageControllerWidget.SLOT_SIZE);
        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        for (NEStorageUiMatrixState matrix : state.matrixStates()) {
            if (!visibleMatrix(matrix)) {
                continue;
            }
            int x = matrixX(matrix);
            int y = matrixY(matrix);
            if (x + CARD_W <= VIEW_X || x >= VIEW_X + VIEW_W) {
                continue;
            }
            int accent = tierColor(matrix.tier());
            boolean hovered = isMouseInCard(x, y, mouseX, mouseY);
            drawRoundedCard(graphics, absX(x), absY(y), CARD_W, CARD_H, hovered);
            graphics.renderItem(matrix.stack(), absX(x + 1), absY(y + 1));
            drawCompressedTitle(
                    graphics,
                    Component.translatable("gui.neoecoae.storage.matrix_card.title", tierName(matrix.tier()))
                            .getString(),
                    absX(x + 18),
                    absY(y + 2),
                    CARD_W - 20,
                    CARD_H - 4,
                    accent);
        }
        graphics.disableScissor();
    }

    boolean renderTooltip(GuiGraphics graphics, NEStorageUiState state, int mouseX, int mouseY) {
        for (NEStorageUiMatrixState matrix : state.matrixStates()) {
            if (!visibleMatrix(matrix)) {
                continue;
            }
            int x = matrixX(matrix);
            int y = matrixY(matrix);
            if (!isMouseInCard(x, y, mouseX, mouseY)) {
                continue;
            }
            ItemStack stack = matrix.stack();
            graphics.renderComponentTooltip(
                    font(),
                    List.of(
                            stack.getHoverName(),
                            tierTooltipLine(matrix.tier()),
                            NELDLibValueText.usedTotalComponent(
                                    Component.translatable("gui.neoecoae.common.types")
                                                    .getString() + ": ",
                                    NELDLibText.number(matrix.usedTypes()),
                                    NELDLibText.number(matrix.totalTypes()),
                                    matrix.usedTypes(),
                                    matrix.totalTypes(),
                                    ""),
                            NELDLibValueText.usedTotalComponent(
                                    "",
                                    NELDLibText.storageBytes(matrix.usedBytes()),
                                    NELDLibText.storageBytes(matrix.totalBytes()),
                                    matrix.usedBytes(),
                                    matrix.totalBytes(),
                                    Component.translatable("gui.neoecoae.storage.bytes_used")
                                            .getString())),
                    mouseX,
                    mouseY);
            return true;
        }
        return false;
    }

    double maxScrollPixels(NEStorageUiState state) {
        return NELDLibScrollBar.maxScroll(contentWidth(state), VIEW_W);
    }

    void updateScrollFromMouse(NEStorageUiState state, double mouseX) {
        int contentWidth = contentWidth(state);
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

    private void drawScrollbar(GuiGraphics graphics, NEStorageUiState state) {
        NELDLibScrollBar.drawHorizontal(
                graphics,
                absX(VIEW_X),
                absY(SCROLLBAR_Y),
                VIEW_W,
                SCROLLBAR_H,
                contentWidth(state),
                VIEW_W,
                scrollPixels.getAsDouble(),
                NELDLibStyle.DARK_PANEL_OUTER,
                NELDLibStyle.DARK_PANEL_MIDDLE,
                NELDLibStyle.DARK_PANEL_LIGHT_EDGE,
                12);
    }

    private int contentWidth(NEStorageUiState state) {
        int columns = columnCount(state);
        return columns <= 0 ? 0 : (columns - 1) * CARD_STRIDE + CARD_W;
    }

    private int columnCount(NEStorageUiState state) {
        int columns = 0;
        for (NEStorageUiMatrixState matrix : state.matrixStates()) {
            columns = Math.max(columns, matrix.column() + 1);
        }
        return columns;
    }

    private boolean isMouseInCard(int x, int y, int mouseX, int mouseY) {
        return mouseX >= absX(Math.max(x, VIEW_X))
                && mouseX < absX(Math.min(x + CARD_W, VIEW_X + VIEW_W))
                && mouseY >= absY(y)
                && mouseY < absY(y + CARD_H);
    }

    private int matrixX(NEStorageUiMatrixState matrix) {
        return VIEW_X + (int) Math.round(matrix.column() * CARD_STRIDE - scrollPixels.getAsDouble());
    }

    private static int matrixY(NEStorageUiMatrixState matrix) {
        return CARD_FIRST_Y + matrix.row() * CARD_ROW_STEP;
    }

    private static boolean visibleMatrix(NEStorageUiMatrixState matrix) {
        return matrix.hasMatrix() && matrix.row() >= 0 && matrix.row() < ROWS;
    }

    private static void drawRoundedCard(GuiGraphics graphics, int x, int y, int width, int height, boolean hovered) {
        int color = hovered ? CARD_HOVER_COLOR : CARD_COLOR;
        graphics.fill(x + 2, y, x + width - 2, y + height, color);
        graphics.fill(x, y + 2, x + width, y + height - 2, color);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, color);
    }

    private void drawCompressedTitle(
            GuiGraphics graphics, String text, int x, int y, int width, int height, int color) {
        Font font = font();
        int textWidth = Math.max(1, font.width(text));
        float scaleX = Math.min(0.62F, (float) width / textWidth);
        float scaleY = 0.72F;
        float drawnWidth = textWidth * scaleX;
        float drawnHeight = font.lineHeight * scaleY;
        graphics.pose().pushPose();
        graphics.pose().translate(x + (width - drawnWidth) / 2.0F, y + (height - drawnHeight) / 2.0F, 200.0F);
        graphics.pose().scale(scaleX, scaleY, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static Component tierTooltipLine(int tier) {
        return Component.translatable("gui.neoecoae.storage.matrix_card.title", tierName(tier))
                .withStyle(style -> style.withColor(tierColor(tier)));
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
