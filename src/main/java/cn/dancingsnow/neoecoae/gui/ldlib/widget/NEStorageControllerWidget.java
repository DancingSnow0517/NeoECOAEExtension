package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import appeng.client.gui.Icon;
import appeng.core.localization.GuiText;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.PriorityMenu;
import appeng.menu.locator.MenuLocators;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.client.gui.ldlib.NELDLibClientStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiMatrixState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiTypeState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibAe2StyleRenderer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStateCodecs;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class NEStorageControllerWidget extends NELDLibSyncedStateWidget<NEStorageUiState> {
    public static final int UI_WIDTH = 344;
    private static final int UI_HEIGHT = 252;
    private static final int LEFT_PANEL_X = 8;
    private static final int LEFT_PANEL_Y = 24;
    private static final int LEFT_PANEL_W = 218;
    private static final int LEFT_PANEL_H = 132;
    private static final int RIGHT_PANEL_X = 234;
    private static final int RIGHT_PANEL_Y = 24;
    private static final int RIGHT_PANEL_W = 106;
    private static final int RIGHT_PANEL_H = 132;
    private static final int TEXT_START_X = LEFT_PANEL_X + 8;
    private static final int TEXT_START_Y = LEFT_PANEL_Y + 8;
    private static final int TEXT_LINE_STEP = 13;
    private static final int TEXT_MAX_W = LEFT_PANEL_W - 16;
    private static final int COLUMN_VIEW_X = RIGHT_PANEL_X + 8;
    private static final int COLUMN_VIEW_W = RIGHT_PANEL_W - 16;
    private static final int COLUMN_Y = RIGHT_PANEL_Y + 32;
    private static final int COLUMN_H = 66;
    private static final int COLUMN_W = 22;
    private static final int COLUMN_GAP = 8;
    private static final int COLUMN_SCROLLBAR_Y = RIGHT_PANEL_Y + 8;
    private static final int COLUMN_SCROLLBAR_H = 3;
    private static final int COLUMN_PERCENT_GAP = 5;
    private static final int COLUMN_PERCENT_H = 15;
    private static final int PRIORITY_BUTTON_X = UI_WIDTH - 22;
    private static final int PRIORITY_BUTTON_Y = 0;
    private static final int PRIORITY_BUTTON_W = 22;
    private static final int PRIORITY_BUTTON_H = 22;
    private static final int SLOT_SIZE = 18;
    private static final int HEADER_STATUS_RIGHT = PRIORITY_BUTTON_X - 6;
    private static final int PLAYER_INV_X = LEFT_PANEL_X;
    private static final int PLAYER_INV_LABEL_Y = 159;
    private static final int PLAYER_INV_Y = 171;
    private static final int PLAYER_HOTBAR_Y = 229;
    private static final int MATRIX_PANEL_X = PLAYER_INV_X + SLOT_SIZE * 9 + 4;
    private static final int MATRIX_PANEL_BOTTOM = 249;
    private static final int MATRIX_PANEL_Y = PLAYER_INV_Y;
    private static final int MATRIX_PANEL_W = UI_WIDTH - MATRIX_PANEL_X - 4;
    private static final int MATRIX_PANEL_H = MATRIX_PANEL_BOTTOM - MATRIX_PANEL_Y;
    private static final int MATRIX_VIEW_X = MATRIX_PANEL_X + 6;
    private static final int MATRIX_VIEW_Y = MATRIX_PANEL_Y + 5;
    private static final int MATRIX_VIEW_W = MATRIX_PANEL_W - 12;
    private static final int MATRIX_VIEW_H = MATRIX_PANEL_H - 10;
    private static final int MATRIX_CARD_W = 82;
    private static final int MATRIX_CARD_H = 18;
    private static final int MATRIX_CARD_GAP = 3;
    private static final int MATRIX_CARD_STRIDE = MATRIX_CARD_W + MATRIX_CARD_GAP;
    private static final int MATRIX_SCROLLBAR_Y = MATRIX_VIEW_Y;
    private static final int MATRIX_SCROLLBAR_H = 4;
    private static final int MATRIX_CARD_FIRST_Y = MATRIX_SCROLLBAR_Y + MATRIX_SCROLLBAR_H + MATRIX_CARD_GAP;
    private static final int MATRIX_CARD_ROW_STEP = MATRIX_CARD_H + MATRIX_CARD_GAP;
    private static final int MATRIX_ROWS = 3;
    private static final int MATRIX_CARD_COLOR = 0xFF302C38;
    private static final int MATRIX_CARD_HOVER_COLOR = 0xFF3B3645;
    private static final double MATRIX_SCROLL_SPEED = 18.0D;
    private static final double MATRIX_SCROLL_LERP = 0.24D;
    private static final double COLUMN_SCROLL_SPEED = 18.0D;
    private static final double COLUMN_SCROLL_LERP = 0.24D;
    private static final double LEFT_SCROLL_SPEED = 13.0D;
    private static final double ANIMATION_SPEED = 0.16D;

    private static final String TOOLTIP_TYPE_USED = "gui.neoecoae.storage.tooltip.type_used";
    private static final String TOOLTIP_USED_TOTAL = "gui.neoecoae.storage.tooltip.used_total";
    private static final Map<ScrollKey, ScrollSnapshot> SCROLL_MEMORY = new HashMap<>();

    private final ECOStorageSystemBlockEntity storage;
    private final Player player;
    private final Inventory playerInventory;

    private double animatedEnergyPct;
    private final Map<String, Double> animatedTypePct = new HashMap<>();
    private double matrixScrollPixels;
    private double matrixScrollTargetPixels;
    private boolean matrixScrollbarDragging;
    private double metricScrollPixels;
    private double metricScrollTargetPixels;
    private boolean metricScrollbarDragging;
    private double leftScrollPixels;

    public NEStorageControllerWidget(ECOStorageSystemBlockEntity storage, Player player) {
        super(
                storage.getBlockState().getBlock().getName(),
                UI_WIDTH,
                uiHeight(storage),
                NEStorageUiState.empty(storage.getBlockPos()),
                storage::createStorageUiState,
                NELDLibStateCodecs::writeStorage,
                NELDLibStateCodecs::readStorage,
                20);
        this.storage = storage;
        this.player = player;
        this.playerInventory = player.getInventory();
        restoreScrollState();
    }

    public static int uiHeight(ECOStorageSystemBlockEntity storage) {
        return UI_HEIGHT;
    }

    @Override
    protected boolean shouldAddTitleWidget() {
        return false;
    }

    @Override
    protected void initLdWidgets() {
        addWidget(new NEAe2IconButtonWidget(
                        PRIORITY_BUTTON_X,
                        PRIORITY_BUTTON_Y,
                        PRIORITY_BUTTON_W,
                        PRIORITY_BUTTON_H,
                        Icon.WRENCH,
                        click -> {
                            if (!click.isRemote && player instanceof ServerPlayer serverPlayer && storage.isFormed()) {
                                MenuOpener.open(PriorityMenu.TYPE, serverPlayer, MenuLocators.forBlockEntity(storage));
                            }
                        })
                .useAeTabButton());
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addWidget(new SlotWidget(
                                playerInventory,
                                col + row * 9 + 9,
                                PLAYER_INV_X + col * SLOT_SIZE,
                                PLAYER_INV_Y + row * SLOT_SIZE,
                                true,
                                true)
                        .setBackgroundTexture(IGuiTexture.EMPTY)
                        .setLocationInfo(true, false));
            }
        }
        for (int col = 0; col < 9; col++) {
            addWidget(new SlotWidget(playerInventory, col, PLAYER_INV_X + col * SLOT_SIZE, PLAYER_HOTBAR_Y, true, true)
                    .setBackgroundTexture(IGuiTexture.EMPTY)
                    .setLocationInfo(true, true));
        }
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        restoreScrollState();
    }

    @Override
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        StorageMetrics metrics = buildStorageMetrics(currentState());
        animatedEnergyPct = animateTo(animatedEnergyPct, metrics.energy().percent());
        animatedTypePct.keySet().removeIf(key -> metrics.types().stream()
                .noneMatch(metric -> metric.key().equals(key)));

        int ox = getPositionX();
        int oy = getPositionY();
        NELDLibClientStyle.drawDarkInsetRect(
                graphics, ox + LEFT_PANEL_X, oy + LEFT_PANEL_Y, LEFT_PANEL_W, LEFT_PANEL_H);
        NELDLibClientStyle.drawDarkInsetRect(
                graphics, ox + RIGHT_PANEL_X, oy + RIGHT_PANEL_Y, RIGHT_PANEL_W, RIGHT_PANEL_H);

        List<Metric> columns = columnMetrics(metrics);
        double[] values = new double[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            Metric metric = columns.get(i);
            double value = animateTo(animatedTypePct.getOrDefault(metric.key(), 0.0D), metric.percent());
            animatedTypePct.put(metric.key(), value);
            values[i] = value;
        }
        drawBoundMetricColumns(graphics, columns, values);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                NELDLibAe2StyleRenderer.drawAeSlot(
                        graphics, absX(PLAYER_INV_X + col * SLOT_SIZE), absY(PLAYER_INV_Y + row * SLOT_SIZE));
            }
        }
        for (int col = 0; col < 9; col++) {
            NELDLibAe2StyleRenderer.drawAeSlot(graphics, absX(PLAYER_INV_X + col * SLOT_SIZE), absY(PLAYER_HOTBAR_Y));
        }
        drawMatrixCards(graphics, mouseX, mouseY);
    }

    @Override
    protected void drawMachineForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        StorageMetrics metrics = buildStorageMetrics(currentState());
        drawLocalString(graphics, title, NELDLibUiTitleX(), NELDLibUiTitleY(), TEXT_PRIMARY);
        drawStorageTextLines(graphics, metrics);
        drawLeftScrollbar(graphics, metrics);
        drawFormedStatus(graphics, currentState().formed());
        drawLocalString(
                graphics,
                Component.translatable("gui.neoecoae.common.inventory"),
                PLAYER_INV_X,
                PLAYER_INV_LABEL_Y,
                TEXT_MUTED);
    }

    @Override
    protected void drawMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isMouseIn(PRIORITY_BUTTON_X, PRIORITY_BUTTON_Y, PRIORITY_BUTTON_W, PRIORITY_BUTTON_H, mouseX, mouseY)) {
            graphics.renderComponentTooltip(font(), List.of(GuiText.Priority.text()), mouseX, mouseY);
            return;
        }
        if (renderMatrixCardTooltip(graphics, mouseX, mouseY)) {
            return;
        }
        renderMetricColumnTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (mouseX >= absX(LEFT_PANEL_X)
                && mouseX < absX(LEFT_PANEL_X + LEFT_PANEL_W)
                && mouseY >= absY(LEFT_PANEL_Y)
                && mouseY < absY(LEFT_PANEL_Y + LEFT_PANEL_H)) {
            double maxScroll = maxLeftScrollPixels();
            double previous = leftScrollPixels;
            leftScrollPixels = Mth.clamp(leftScrollPixels - wheelDelta * LEFT_SCROLL_SPEED, 0.0D, maxScroll);
            rememberScrollState();
            return leftScrollPixels != previous || maxScroll > 0.0D;
        }
        if (mouseX >= absX(RIGHT_PANEL_X)
                && mouseX < absX(RIGHT_PANEL_X + RIGHT_PANEL_W)
                && mouseY >= absY(RIGHT_PANEL_Y)
                && mouseY < absY(RIGHT_PANEL_Y + RIGHT_PANEL_H)) {
            List<Metric> columns = columnMetrics(buildStorageMetrics(currentState()));
            double oldTarget = metricScrollTargetPixels;
            metricScrollTargetPixels = Mth.clamp(
                    metricScrollTargetPixels - wheelDelta * COLUMN_SCROLL_SPEED, 0.0D, maxMetricScrollPixels(columns));
            rememberScrollState();
            return metricScrollTargetPixels != oldTarget || maxMetricScrollPixels(columns) > 0.0D;
        }
        if (mouseX >= absX(MATRIX_PANEL_X)
                && mouseX < absX(MATRIX_PANEL_X + MATRIX_PANEL_W)
                && mouseY >= absY(MATRIX_PANEL_Y)
                && mouseY < absY(MATRIX_PANEL_Y + MATRIX_PANEL_H)) {
            double oldTarget = matrixScrollTargetPixels;
            matrixScrollTargetPixels = Mth.clamp(
                    matrixScrollTargetPixels - wheelDelta * MATRIX_SCROLL_SPEED, 0.0D, maxMatrixScrollPixels());
            rememberScrollState();
            return matrixScrollTargetPixels != oldTarget || maxMatrixScrollPixels() > 0.0D;
        }
        return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0
                && maxMatrixScrollPixels() > 0.0D
                && mouseX >= absX(MATRIX_VIEW_X)
                && mouseX < absX(MATRIX_VIEW_X + MATRIX_VIEW_W)
                && mouseY >= absY(MATRIX_SCROLLBAR_Y)
                && mouseY < absY(MATRIX_SCROLLBAR_Y + MATRIX_SCROLLBAR_H)) {
            matrixScrollbarDragging = true;
            updateMatrixScrollFromMouse(mouseX);
            return true;
        }
        if (button == 0
                && maxMetricScrollPixels(columnMetrics(buildStorageMetrics(currentState()))) > 0.0D
                && mouseX >= absX(COLUMN_VIEW_X)
                && mouseX < absX(COLUMN_VIEW_X + COLUMN_VIEW_W)
                && mouseY >= absY(COLUMN_SCROLLBAR_Y)
                && mouseY < absY(COLUMN_SCROLLBAR_Y + COLUMN_SCROLLBAR_H)) {
            metricScrollbarDragging = true;
            updateMetricScrollFromMouse(mouseX, columnMetrics(buildStorageMetrics(currentState())));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && matrixScrollbarDragging) {
            updateMatrixScrollFromMouse(mouseX);
            return true;
        }
        if (button == 0 && metricScrollbarDragging) {
            updateMetricScrollFromMouse(mouseX, columnMetrics(buildStorageMetrics(currentState())));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && matrixScrollbarDragging) {
            matrixScrollbarDragging = false;
            rememberScrollState();
            return true;
        }
        if (button == 0 && metricScrollbarDragging) {
            metricScrollbarDragging = false;
            rememberScrollState();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void drawMatrixCards(GuiGraphics graphics, int mouseX, int mouseY) {
        double maxScroll = maxMatrixScrollPixels();
        matrixScrollTargetPixels = Mth.clamp(matrixScrollTargetPixels, 0.0D, maxScroll);
        matrixScrollPixels = matrixScrollbarDragging
                ? matrixScrollTargetPixels
                : Mth.clamp(
                        Mth.lerp(MATRIX_SCROLL_LERP, matrixScrollPixels, matrixScrollTargetPixels), 0.0D, maxScroll);
        if (Math.abs(matrixScrollPixels - matrixScrollTargetPixels) < 0.05D) {
            matrixScrollPixels = matrixScrollTargetPixels;
        }
        NELDLibClientStyle.drawDarkInsetRect(
                graphics, absX(MATRIX_PANEL_X), absY(MATRIX_PANEL_Y), MATRIX_PANEL_W, MATRIX_PANEL_H);
        drawMatrixScrollbar(graphics);
        int clipLeft = absX(MATRIX_VIEW_X);
        int clipTop = absY(MATRIX_CARD_FIRST_Y);
        int clipRight = absX(MATRIX_VIEW_X + MATRIX_VIEW_W);
        int clipBottom = absY(PLAYER_HOTBAR_Y + SLOT_SIZE);
        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        for (NEStorageUiMatrixState state : currentState().matrixStates()) {
            if (!state.hasMatrix() || state.row() < 0 || state.row() >= MATRIX_ROWS) {
                continue;
            }
            int x = MATRIX_VIEW_X + (int) Math.round(state.column() * MATRIX_CARD_STRIDE - matrixScrollPixels);
            int y = MATRIX_CARD_FIRST_Y + state.row() * MATRIX_CARD_ROW_STEP;
            if (x + MATRIX_CARD_W <= MATRIX_VIEW_X || x >= MATRIX_VIEW_X + MATRIX_VIEW_W) {
                continue;
            }
            int accent = matrixTierColor(state.tier());
            boolean hovered = isMouseInMatrixCard(x, y, mouseX, mouseY);
            drawRoundedMatrixCard(graphics, absX(x), absY(y), MATRIX_CARD_W, MATRIX_CARD_H, hovered);
            graphics.renderItem(state.stack(), absX(x + 1), absY(y + 1));
            drawCompressedMatrixTitle(
                    graphics,
                    Component.translatable("gui.neoecoae.storage.matrix_card.title", matrixTierName(state.tier()))
                            .getString(),
                    absX(x + 18),
                    absY(y + 2),
                    MATRIX_CARD_W - 20,
                    MATRIX_CARD_H - 4,
                    accent);
        }
        graphics.disableScissor();
    }

    private void drawRoundedMatrixCard(GuiGraphics graphics, int x, int y, int width, int height, boolean hovered) {
        int color = hovered ? MATRIX_CARD_HOVER_COLOR : MATRIX_CARD_COLOR;
        graphics.fill(x + 2, y, x + width - 2, y + height, color);
        graphics.fill(x, y + 2, x + width, y + height - 2, color);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, color);
    }

    private void drawCompressedMatrixTitle(
            GuiGraphics graphics, String text, int x, int y, int width, int height, int color) {
        int textWidth = Math.max(1, font().width(text));
        float scaleX = Math.min(0.62F, (float) width / textWidth);
        float scaleY = 0.72F;
        float drawnWidth = textWidth * scaleX;
        float drawnHeight = font().lineHeight * scaleY;
        graphics.pose().pushPose();
        graphics.pose().translate(x + (width - drawnWidth) / 2.0F, y + (height - drawnHeight) / 2.0F, 200.0F);
        graphics.pose().scale(scaleX, scaleY, 1.0F);
        graphics.drawString(font(), text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void drawMatrixScrollbar(GuiGraphics graphics) {
        int trackX = absX(MATRIX_VIEW_X);
        int trackY = absY(MATRIX_SCROLLBAR_Y);
        int trackWidth = MATRIX_VIEW_W;
        graphics.fill(trackX, trackY, trackX + trackWidth, trackY + MATRIX_SCROLLBAR_H, NELDLibStyle.DARK_PANEL_OUTER);
        graphics.fill(
                trackX + 1,
                trackY + 1,
                trackX + trackWidth - 1,
                trackY + MATRIX_SCROLLBAR_H - 1,
                NELDLibStyle.DARK_PANEL_MIDDLE);
        int contentWidth = matrixContentWidth();
        if (contentWidth <= MATRIX_VIEW_W) {
            graphics.fill(
                    trackX + 1,
                    trackY + 1,
                    trackX + trackWidth - 1,
                    trackY + MATRIX_SCROLLBAR_H - 1,
                    NELDLibStyle.DARK_PANEL_LIGHT_EDGE);
            return;
        }
        int thumbWidth = Math.max(12, trackWidth * MATRIX_VIEW_W / contentWidth);
        int travel = trackWidth - thumbWidth;
        int thumbX = trackX + (int) Math.round(travel * matrixScrollPixels / maxMatrixScrollPixels());
        graphics.fill(
                thumbX, trackY, thumbX + thumbWidth, trackY + MATRIX_SCROLLBAR_H, NELDLibStyle.DARK_PANEL_LIGHT_EDGE);
    }

    private boolean renderMatrixCardTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (NEStorageUiMatrixState state : currentState().matrixStates()) {
            if (!state.hasMatrix() || state.row() < 0 || state.row() >= MATRIX_ROWS) {
                continue;
            }
            int x = MATRIX_VIEW_X + (int) Math.round(state.column() * MATRIX_CARD_STRIDE - matrixScrollPixels);
            int y = MATRIX_CARD_FIRST_Y + state.row() * MATRIX_CARD_ROW_STEP;
            if (!isMouseInMatrixCard(x, y, mouseX, mouseY)) {
                continue;
            }
            ItemStack stack = state.stack();
            graphics.renderComponentTooltip(
                    font(),
                    List.of(
                            stack.getHoverName(),
                            matrixTierTooltipLine(state.tier()),
                            matrixUsedTotalTooltipLine(
                                    Component.translatable("gui.neoecoae.common.types")
                                                    .getString() + ": ",
                                    NELDLibText.number(state.usedTypes()),
                                    NELDLibText.number(state.totalTypes()),
                                    state.usedTypes(),
                                    state.totalTypes(),
                                    ""),
                            matrixUsedTotalTooltipLine(
                                    "",
                                    NELDLibText.storageBytes(state.usedBytes()),
                                    NELDLibText.storageBytes(state.totalBytes()),
                                    state.usedBytes(),
                                    state.totalBytes(),
                                    Component.translatable("gui.neoecoae.storage.bytes_used")
                                            .getString())),
                    mouseX,
                    mouseY);
            return true;
        }
        return false;
    }

    private Component matrixTierTooltipLine(int tier) {
        return Component.translatable("gui.neoecoae.storage.matrix_card.title", matrixTierName(tier))
                .withStyle(style -> style.withColor(matrixTierColor(tier)));
    }

    private Component matrixUsedTotalTooltipLine(
            String prefix, String usedText, String totalText, long used, long total, String suffix) {
        MutableComponent line =
                Component.literal(prefix).withStyle(style -> style.withColor(NELDLibStyle.DARK_TEXT_MUTED));
        line.append(Component.literal(usedText)
                .withStyle(style -> style.withColor(NELDLibStyle.usedValueColor(used, total))));
        line.append(Component.literal(" / ").withStyle(style -> style.withColor(NELDLibStyle.DARK_TEXT_MUTED)));
        line.append(Component.literal(totalText).withStyle(style -> style.withColor(NELDLibStyle.DARK_TEXT_VALUE)));
        if (!suffix.isEmpty()) {
            line.append(
                    Component.literal(" " + suffix).withStyle(style -> style.withColor(NELDLibStyle.DARK_TEXT_MUTED)));
        }
        return line;
    }

    private int matrixColumnCount() {
        int columns = 0;
        for (NEStorageUiMatrixState state : currentState().matrixStates()) {
            columns = Math.max(columns, state.column() + 1);
        }
        return columns;
    }

    private int matrixContentWidth() {
        int columns = matrixColumnCount();
        return columns <= 0 ? 0 : (columns - 1) * MATRIX_CARD_STRIDE + MATRIX_CARD_W;
    }

    private double maxMatrixScrollPixels() {
        return Math.max(0.0D, matrixContentWidth() - MATRIX_VIEW_W);
    }

    private void updateMatrixScrollFromMouse(double mouseX) {
        int contentWidth = matrixContentWidth();
        if (contentWidth <= MATRIX_VIEW_W) {
            matrixScrollTargetPixels = 0.0D;
            matrixScrollPixels = 0.0D;
            rememberScrollState();
            return;
        }
        int thumbWidth = Math.max(12, MATRIX_VIEW_W * MATRIX_VIEW_W / contentWidth);
        int travel = MATRIX_VIEW_W - thumbWidth;
        double relativeX = mouseX - absX(MATRIX_VIEW_X) - thumbWidth / 2.0D;
        matrixScrollTargetPixels =
                Mth.clamp(relativeX * maxMatrixScrollPixels() / Math.max(1, travel), 0.0D, maxMatrixScrollPixels());
        matrixScrollPixels = matrixScrollTargetPixels;
        rememberScrollState();
    }

    private boolean isMouseInMatrixCard(int x, int y, int mouseX, int mouseY) {
        return mouseX >= absX(Math.max(x, MATRIX_VIEW_X))
                && mouseX < absX(Math.min(x + MATRIX_CARD_W, MATRIX_VIEW_X + MATRIX_VIEW_W))
                && mouseY >= absY(y)
                && mouseY < absY(y + MATRIX_CARD_H);
    }

    private static String matrixTierName(int tier) {
        return switch (tier) {
            case 3 -> "L9";
            case 2 -> "L6";
            default -> "L4";
        };
    }

    private static int matrixTierColor(int tier) {
        return switch (tier) {
            case 3 -> 0xFFFF55FF;
            case 2 -> 0xFF55FFFF;
            default -> 0xFFFFFF55;
        };
    }

    private void drawStorageTextLines(GuiGraphics g, StorageMetrics metrics) {
        leftScrollPixels = Mth.clamp(leftScrollPixels, 0.0D, maxLeftScrollPixels(metrics));
        int x = absX(TEXT_START_X);
        int y = absY(TEXT_START_Y - (int) Math.round(leftScrollPixels));
        g.enableScissor(
                absX(LEFT_PANEL_X + 4),
                absY(LEFT_PANEL_Y + 4),
                absX(LEFT_PANEL_X + LEFT_PANEL_W - 4),
                absY(LEFT_PANEL_Y + LEFT_PANEL_H - 4));

        drawPlainLine(g, Component.translatable("gui.neoecoae.storage.energy"), x, y, NELDLibStyle.DARK_TEXT_PRIMARY);
        y += TEXT_LINE_STEP;
        drawPrefixedUsedTotalLine(
                g,
                Component.translatable("gui.neoecoae.storage.energy_storage").getString() + ": ",
                metrics.energy().used(),
                metrics.energy().max(),
                "AE",
                x,
                y);
        y += TEXT_LINE_STEP;

        for (Metric metric : metrics.types()) {
            y = drawStorageTypeBlock(g, metric, x, y);
        }
        g.disableScissor();
    }

    private double maxLeftScrollPixels() {
        return maxLeftScrollPixels(buildStorageMetrics(currentState()));
    }

    private double maxLeftScrollPixels(StorageMetrics metrics) {
        int typeCount = metrics.types().size();
        int lineCount = 2 + typeCount * 3;
        int contentHeight = (lineCount - 1) * TEXT_LINE_STEP + font().lineHeight;
        int viewportHeight = LEFT_PANEL_H - 16;
        return Math.max(0, contentHeight - viewportHeight);
    }

    private void drawLeftScrollbar(GuiGraphics g, StorageMetrics metrics) {
        double maxScroll = maxLeftScrollPixels(metrics);
        if (maxScroll <= 0.0D) {
            leftScrollPixels = 0.0D;
            return;
        }
        leftScrollPixels = Mth.clamp(leftScrollPixels, 0.0D, maxScroll);
        int trackX = absX(LEFT_PANEL_X + LEFT_PANEL_W - 5);
        int trackY = absY(LEFT_PANEL_Y + 5);
        int trackH = LEFT_PANEL_H - 10;
        int contentH = trackH + (int) Math.ceil(maxScroll);
        int thumbH = Math.max(12, trackH * trackH / contentH);
        int thumbY = trackY + (int) Math.round((trackH - thumbH) * leftScrollPixels / maxScroll);
        g.fill(trackX, trackY, trackX + 2, trackY + trackH, 0xAA17141E);
        g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xFF8B83A0);
    }

    private int drawStorageTypeBlock(GuiGraphics g, Metric metric, int x, int y) {
        drawPlainLine(g, metric.label(), x, y, metric.accentColor());
        y += TEXT_LINE_STEP;
        drawUsedTotalLine(
                g,
                NELDLibText.number(metric.usedTypes()),
                NELDLibText.number(metric.totalTypes()),
                metric.usedTypes(),
                metric.totalTypes(),
                Component.translatable("gui.neoecoae.common.types").getString(),
                x,
                y);
        y += TEXT_LINE_STEP;
        drawByteUsedTotalLine(g, metric.used(), metric.max(), x, y);
        return y + TEXT_LINE_STEP;
    }

    private void drawPrefixedUsedTotalLine(
            GuiGraphics g, String prefix, long used, long max, String suffix, int x, int y) {
        int cursor = NELDLibClientStyle.drawSegment(g, font(), prefix, x, y, NELDLibStyle.DARK_TEXT_MUTED);
        cursor += NELDLibClientStyle.drawSegment(
                g,
                font(),
                NELDLibText.number(Math.max(0L, used)),
                x + cursor,
                y,
                NELDLibStyle.usedValueColor(used, max));
        cursor += NELDLibClientStyle.drawSegment(g, font(), " / ", x + cursor, y, NELDLibStyle.DARK_TEXT_MUTED);
        cursor += NELDLibClientStyle.drawSegment(
                g, font(), NELDLibText.number(Math.max(0L, max)), x + cursor, y, NELDLibStyle.DARK_TEXT_VALUE);
        if (!suffix.isEmpty()) {
            NELDLibClientStyle.drawSegment(g, font(), " " + suffix, x + cursor, y, NELDLibStyle.DARK_TEXT_MUTED);
        }
    }

    private void drawByteUsedTotalLine(GuiGraphics g, long used, long max, int x, int y) {
        String usedText = NELDLibText.storageBytes(used);
        String maxText = NELDLibText.storageBytes(max);
        String suffix =
                Component.translatable("gui.neoecoae.storage.bytes_used").getString();
        if (font().width(usedText + " / " + maxText + " " + suffix) > TEXT_MAX_W) {
            suffix = Component.translatable("gui.neoecoae.storage.used_short").getString();
        }
        drawUsedTotalLine(g, usedText, maxText, used, max, suffix, x, y);
    }

    private void drawUsedTotalLine(
            GuiGraphics g, String usedText, String maxText, long used, long max, String suffix, int x, int y) {
        int cursor = NELDLibClientStyle.drawSegment(g, font(), usedText, x, y, NELDLibStyle.usedValueColor(used, max));
        cursor += NELDLibClientStyle.drawSegment(g, font(), " / ", x + cursor, y, NELDLibStyle.DARK_TEXT_MUTED);
        cursor += NELDLibClientStyle.drawSegment(g, font(), maxText, x + cursor, y, NELDLibStyle.DARK_TEXT_VALUE);
        NELDLibClientStyle.drawSegment(g, font(), " " + suffix, x + cursor, y, NELDLibStyle.DARK_TEXT_MUTED);
    }

    private void drawBoundMetricColumns(GuiGraphics g, List<Metric> metrics, double[] animatedValues) {
        int count = metrics.size();
        if (count <= 0) {
            return;
        }
        double maxScroll = maxMetricScrollPixels(metrics);
        metricScrollTargetPixels = Mth.clamp(metricScrollTargetPixels, 0.0D, maxScroll);
        metricScrollPixels = metricScrollbarDragging
                ? metricScrollTargetPixels
                : Mth.clamp(
                        Mth.lerp(COLUMN_SCROLL_LERP, metricScrollPixels, metricScrollTargetPixels), 0.0D, maxScroll);
        if (Math.abs(metricScrollPixels - metricScrollTargetPixels) < 0.05D) {
            metricScrollPixels = metricScrollTargetPixels;
        }

        drawMetricScrollbar(g, metrics);

        int startX = metricColumnStartX(metrics);
        int clipLeft = absX(COLUMN_VIEW_X);
        int clipTop = absY(RIGHT_PANEL_Y + 18);
        int clipRight = absX(COLUMN_VIEW_X + COLUMN_VIEW_W);
        int clipBottom = absY(RIGHT_PANEL_Y + RIGHT_PANEL_H - 5);
        g.enableScissor(clipLeft, clipTop, clipRight, clipBottom);

        for (int i = 0; i < count; i++) {
            int x = startX + i * (COLUMN_W + COLUMN_GAP);
            if (x + COLUMN_W <= COLUMN_VIEW_X || x >= COLUMN_VIEW_X + COLUMN_VIEW_W) {
                continue;
            }
            drawBoundMetricColumn(g, metrics.get(i), absX(x), absY(COLUMN_Y), COLUMN_W, COLUMN_H, animatedValues[i]);
        }
        g.disableScissor();
    }

    private void drawMetricScrollbar(GuiGraphics graphics, List<Metric> metrics) {
        int trackX = absX(COLUMN_VIEW_X);
        int trackY = absY(COLUMN_SCROLLBAR_Y);
        graphics.fill(
                trackX, trackY, trackX + COLUMN_VIEW_W, trackY + COLUMN_SCROLLBAR_H, NELDLibStyle.DARK_PANEL_OUTER);
        graphics.fill(
                trackX + 1,
                trackY + 1,
                trackX + COLUMN_VIEW_W - 1,
                trackY + COLUMN_SCROLLBAR_H - 1,
                NELDLibStyle.DARK_PANEL_MIDDLE);
        int contentWidth = metricContentWidth(metrics);
        if (contentWidth <= COLUMN_VIEW_W) {
            graphics.fill(
                    trackX + 1,
                    trackY + 1,
                    trackX + COLUMN_VIEW_W - 1,
                    trackY + COLUMN_SCROLLBAR_H - 1,
                    NELDLibStyle.DARK_PANEL_LIGHT_EDGE);
            return;
        }
        int thumbWidth = Math.max(12, COLUMN_VIEW_W * COLUMN_VIEW_W / contentWidth);
        int travel = COLUMN_VIEW_W - thumbWidth;
        int thumbX = trackX + (int) Math.round(travel * metricScrollPixels / maxMetricScrollPixels(metrics));
        graphics.fill(
                thumbX, trackY, thumbX + thumbWidth, trackY + COLUMN_SCROLLBAR_H, NELDLibStyle.DARK_PANEL_LIGHT_EDGE);
    }

    private void drawBoundMetricColumn(GuiGraphics g, Metric metric, int x, int y, int w, int h, double pct) {
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

        int percentY = y + h + COLUMN_PERCENT_GAP;
        int percentColor = metric.max() <= 0
                ? NELDLibStyle.DARK_TEXT_MUTED
                : NELDLibStyle.metricColor(metric.accentColor(), metric.max(), pct);
        String percentText = NELDLibText.percentOrNA(metric.used(), metric.max());
        NELDLibClientStyle.drawTinyInsetRect(g, x - 2, percentY, w + 4, COLUMN_PERCENT_H, 0xFF201E27);
        NELDLibClientStyle.drawCenteredScaledString(
                g, font(), percentText, x - 2, percentY, w + 4, COLUMN_PERCENT_H, percentColor, 0.9F);
    }

    private void drawFormedStatus(GuiGraphics g, boolean formed) {
        Component label = Component.translatable("gui.neoecoae.machine.formed").append(": ");
        Component value = boolText(formed);
        int textW = font().width(label) + font().width(value);
        int textX = absX(HEADER_STATUS_RIGHT - textW);
        int textY = absY(NELDLibUiTitleY());
        g.drawString(font(), label, textX, textY, 0xFF4A4A4A, false);
        g.drawString(font(), value, textX + font().width(label), textY, formed ? 0xFF1F9D55 : 0xFFD13F3F, false);
    }

    private void renderMetricColumnTooltip(GuiGraphics g, int mouseX, int mouseY) {
        StorageMetrics metrics = buildStorageMetrics(currentState());
        List<Metric> columns = columnMetrics(metrics);

        int count = columns.size();
        if (count <= 0) {
            return;
        }
        int startX = metricColumnStartX(columns);

        for (int i = 0; i < count; i++) {
            int x = startX + i * (COLUMN_W + COLUMN_GAP);
            if (x + COLUMN_W <= COLUMN_VIEW_X || x >= COLUMN_VIEW_X + COLUMN_VIEW_W) {
                continue;
            }
            int clippedX = Math.max(x, COLUMN_VIEW_X);
            int clippedW = Math.min(x + COLUMN_W, COLUMN_VIEW_X + COLUMN_VIEW_W) - clippedX;
            if (!isMouseIn(clippedX, COLUMN_Y, clippedW, COLUMN_H, mouseX, mouseY)) {
                continue;
            }
            Metric metric = columns.get(i);
            g.renderTooltip(
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
            return;
        }
    }

    private StorageMetrics buildStorageMetrics(NEStorageUiState state) {
        List<NEStorageUiTypeState> types = state.typeStates();
        NEStorageUiTypeState itemState = findTypeState(types, "item");
        NEStorageUiTypeState fluidState = findTypeState(types, "fluid");
        Metric energy = new Metric(
                "energy",
                Component.translatable("gui.neoecoae.common.energy"),
                state.storedEnergy(),
                state.maxEnergy(),
                0,
                0,
                NELDLibStyle.DARK_TEXT_VALUE);
        List<Metric> typeMetrics = new ArrayList<>();
        typeMetrics.add(createTypeMetric(
                "neoecoae:items", itemState, Component.translatable("gui.neoecoae.storage.items"), 0xFF43B678));
        typeMetrics.add(createTypeMetric(
                "neoecoae:fluids", fluidState, Component.translatable("gui.neoecoae.storage.fluids"), 0xFF3A8FD6));
        for (NEStorageUiTypeState type : types) {
            if (matchesTypeState(type, "item") || matchesTypeState(type, "fluid")) {
                continue;
            }
            typeMetrics.add(createTypeMetric(
                    type.typeId().toString(),
                    type,
                    Component.literal(type.displayName()),
                    typeAccentColor(type, typeMetrics.size())));
        }
        return new StorageMetrics(energy, List.copyOf(typeMetrics));
    }

    private static Metric createTypeMetric(
            String key, NEStorageUiTypeState state, Component fallbackLabel, int accentColor) {
        if (state == null) {
            return new Metric(key, fallbackLabel, 0, 0, 0, 0, accentColor);
        }
        return new Metric(
                key,
                fallbackLabel,
                state.usedBytes(),
                state.totalBytes(),
                state.usedTypes(),
                state.totalTypes(),
                accentColor);
    }

    private void drawPlainLine(GuiGraphics g, Component text, int x, int y, int color) {
        g.drawString(font(), text, x, y, color, false);
    }

    private int NELDLibUiTitleX() {
        return 8;
    }

    private int NELDLibUiTitleY() {
        return 8;
    }

    private static List<Metric> columnMetrics(StorageMetrics metrics) {
        List<Metric> activeMetrics = new ArrayList<>();
        for (Metric metric : metrics.types()) {
            if (metric.max() > 0 || metric.totalTypes() > 0) {
                activeMetrics.add(metric);
            }
        }
        List<Metric> source = activeMetrics.isEmpty() ? metrics.types() : activeMetrics;
        return source;
    }

    private int metricColumnStartX(List<Metric> metrics) {
        int contentWidth = metricContentWidth(metrics);
        if (contentWidth <= COLUMN_VIEW_W) {
            return COLUMN_VIEW_X + (COLUMN_VIEW_W - contentWidth) / 2;
        }
        return COLUMN_VIEW_X - (int) Math.round(metricScrollPixels);
    }

    private static int metricContentWidth(List<Metric> metrics) {
        int count = metrics.size();
        return count <= 0 ? 0 : count * COLUMN_W + (count - 1) * COLUMN_GAP;
    }

    private static double maxMetricScrollPixels(List<Metric> metrics) {
        return Math.max(0.0D, metricContentWidth(metrics) - COLUMN_VIEW_W);
    }

    private void updateMetricScrollFromMouse(double mouseX, List<Metric> metrics) {
        int contentWidth = metricContentWidth(metrics);
        if (contentWidth <= COLUMN_VIEW_W) {
            metricScrollTargetPixels = 0.0D;
            metricScrollPixels = 0.0D;
            rememberScrollState();
            return;
        }
        int thumbWidth = Math.max(12, COLUMN_VIEW_W * COLUMN_VIEW_W / contentWidth);
        int travel = COLUMN_VIEW_W - thumbWidth;
        double relativeX = mouseX - absX(COLUMN_VIEW_X) - thumbWidth / 2.0D;
        metricScrollTargetPixels = Mth.clamp(
                relativeX * maxMetricScrollPixels(metrics) / Math.max(1, travel), 0.0D, maxMetricScrollPixels(metrics));
        metricScrollPixels = metricScrollTargetPixels;
        rememberScrollState();
    }

    private void restoreScrollState() {
        scrollKey().map(SCROLL_MEMORY::get).ifPresent(snapshot -> {
            leftScrollPixels = snapshot.leftScrollPixels();
            metricScrollPixels = snapshot.metricScrollPixels();
            metricScrollTargetPixels = snapshot.metricScrollPixels();
            matrixScrollPixels = snapshot.matrixScrollPixels();
            matrixScrollTargetPixels = snapshot.matrixScrollPixels();
        });
    }

    private void rememberScrollState() {
        scrollKey()
                .ifPresent(key -> SCROLL_MEMORY.put(
                        key,
                        new ScrollSnapshot(
                                leftScrollPixels,
                                metricScrollbarDragging ? metricScrollPixels : metricScrollTargetPixels,
                                matrixScrollbarDragging ? matrixScrollPixels : matrixScrollTargetPixels)));
    }

    private Optional<ScrollKey> scrollKey() {
        if (storage.getLevel() == null) {
            return Optional.empty();
        }
        return Optional.of(new ScrollKey(
                player.getUUID(),
                storage.getLevel().dimension().location(),
                storage.getBlockPos().immutable()));
    }

    private static NEStorageUiTypeState findTypeState(List<NEStorageUiTypeState> types, String needle) {
        String lowerNeedle = needle.toLowerCase(Locale.ROOT);
        String pluralNeedle = lowerNeedle + "s";
        for (NEStorageUiTypeState state : types) {
            String path = state.typeId().getPath().toLowerCase(Locale.ROOT);
            if (path.equals(lowerNeedle) || path.equals(pluralNeedle)) {
                return state;
            }
        }
        for (NEStorageUiTypeState state : types) {
            String path = state.typeId().getPath().toLowerCase(Locale.ROOT);
            String name = state.displayName().toLowerCase(Locale.ROOT);
            if (path.contains(lowerNeedle) || name.contains(lowerNeedle)) {
                return state;
            }
        }
        return null;
    }

    private static boolean matchesTypeState(NEStorageUiTypeState state, String needle) {
        String lowerNeedle = needle.toLowerCase(Locale.ROOT);
        String pluralNeedle = lowerNeedle + "s";
        String path = state.typeId().getPath().toLowerCase(Locale.ROOT);
        return path.equals(lowerNeedle) || path.equals(pluralNeedle);
    }

    private static int typeAccentColor(NEStorageUiTypeState state, int index) {
        String path = state.typeId().getPath().toLowerCase(Locale.ROOT);
        String name = state.displayName().toLowerCase(Locale.ROOT);
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

    private static double animateTo(double current, double target) {
        double start = current < 0.0D ? 0.0D : current;
        return Mth.lerp(ANIMATION_SPEED, start, Mth.clamp(target, 0.0D, 1.0D));
    }

    private record StorageMetrics(Metric energy, List<Metric> types) {}

    private record ScrollKey(UUID playerId, ResourceLocation dimension, BlockPos pos) {}

    private record ScrollSnapshot(double leftScrollPixels, double metricScrollPixels, double matrixScrollPixels) {}

    private record Metric(
            String key, Component label, long used, long max, long usedTypes, long totalTypes, int accentColor) {
        private double percent() {
            return NEStorageControllerWidget.percent(used, max);
        }
    }
}
