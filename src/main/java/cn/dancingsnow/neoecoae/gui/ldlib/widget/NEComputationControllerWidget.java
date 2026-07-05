package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import appeng.api.config.CpuSelectionMode;
import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.client.gui.ldlib.NELDLibClientStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEComputationUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingRecipeUiEntry;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStateCodecs;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibTaskCards;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibTextRender;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEPlayerInventoryWidgets;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

public class NEComputationControllerWidget extends NELDLibSyncedStateWidget<NEComputationUiState> {
    public static final int UI_WIDTH = 344;
    public static final int UI_HEIGHT = 252;
    private static final int PANEL_MARGIN = 7;
    private static final int MAIN_PANEL_X = PANEL_MARGIN;
    private static final int MAIN_PANEL_Y = 24;
    private static final int SLOT_SIZE = 18;
    private static final int MAIN_PANEL_W = 164;
    private static final int MAIN_PANEL_H = 132;
    private static final int TOOLBAR_BUTTON_X = UI_WIDTH - PANEL_MARGIN - 16;
    private static final int TOOLBAR_BUTTON_Y = 4;
    private static final int TOOLBAR_BUTTON_W = 16;
    private static final int TOOLBAR_BUTTON_H = 16;
    private static final int HEADER_STATUS_RIGHT = TOOLBAR_BUTTON_X - 4;

    private static final int STAT_X = MAIN_PANEL_X + 8;
    private static final int STAT_TEXT_MAX_W = MAIN_PANEL_W - 16;
    private static final int STAT_BAR_X = MAIN_PANEL_X + 12;
    private static final int STAT_BAR_W = MAIN_PANEL_W - 24;
    private static final int STAT_BAR_H = 8;
    private static final int THREAD_BAR_Y = MAIN_PANEL_Y + 21;
    private static final int STORAGE_TEXT_Y = MAIN_PANEL_Y + 78;
    private static final int STORAGE_BAR_Y = MAIN_PANEL_Y + 93;
    private static final int PARALLEL_CORES_Y = MAIN_PANEL_Y + 108;
    private static final int PLAYER_INV_X = 8;
    private static final int PLAYER_INV_LABEL_Y = 159;
    private static final int PLAYER_INV_Y = 171;
    private static final int PLAYER_HOTBAR_Y = 229;
    private static final int TASK_PANEL_GAP = 8;
    private static final int TASK_PANEL_X = MAIN_PANEL_X + MAIN_PANEL_W + TASK_PANEL_GAP;
    private static final int TASK_PANEL_Y = MAIN_PANEL_Y;
    private static final int TASK_PANEL_W = UI_WIDTH - TASK_PANEL_X - PANEL_MARGIN;
    private static final int TASK_PANEL_H = PLAYER_HOTBAR_Y + SLOT_SIZE - TASK_PANEL_Y;
    private static final int TASK_CARD_X = TASK_PANEL_X + 8;
    private static final int TASK_CARD_Y = TASK_PANEL_Y + 19;
    private static final int TASK_CARD_W = TASK_PANEL_W - 16;
    private static final int TASK_CARD_H = 24;
    private static final int TASK_CARD_STRIDE = 26;
    private static final int TASK_LIST_BOTTOM_Y = TASK_PANEL_Y + TASK_PANEL_H - 3;
    private static final int TASK_SCROLLBAR_W = 3;

    private final ECOComputationSystemBlockEntity computation;
    private final Inventory playerInventory;
    private int taskScrollOffset;
    private NEAe2IconButtonWidget cpuModeButton;

    public NEComputationControllerWidget(ECOComputationSystemBlockEntity computation, Player player) {
        super(
                computation.getBlockState().getBlock().getName(),
                UI_WIDTH,
                UI_HEIGHT,
                NEComputationUiState.empty(computation.getBlockPos()),
                computation::createComputationUiState,
                NELDLibStateCodecs::writeComputation,
                NELDLibStateCodecs::readComputation,
                20);
        this.computation = computation;
        this.playerInventory = player.getInventory();
    }

    @Override
    protected boolean shouldAddTitleWidget() {
        return false;
    }

    @Override
    protected void initLdWidgets() {
        cpuModeButton = new NEAe2IconButtonWidget(
                TOOLBAR_BUTTON_X, TOOLBAR_BUTTON_Y, TOOLBAR_BUTTON_W, TOOLBAR_BUTTON_H, cpuModeIcon(), click -> {
                    if (!click.isRemote) {
                        NEComputationCluster cluster = computation.getCluster();
                        if (cluster != null) {
                            cluster.cycleSelectionMode();
                            computation.markComputationStatsDirty();
                            computation.updateInfos();
                            syncStateNow();
                        }
                    }
                });
        addWidget(cpuModeButton);
        addPlayerInventorySlots();
    }

    @Override
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int ox = getPositionX();
        int oy = getPositionY();
        cpuModeButton.setIcon(cpuModeIcon());
        NELDLibClientStyle.drawDarkInsetRect(
                graphics, ox + MAIN_PANEL_X, oy + MAIN_PANEL_Y, MAIN_PANEL_W, MAIN_PANEL_H);
        NELDLibClientStyle.drawDarkInsetRect(
                graphics, ox + TASK_PANEL_X, oy + TASK_PANEL_Y, TASK_PANEL_W, TASK_PANEL_H);
        drawPlayerInventorySlots(graphics);

        NEComputationUiState state = currentState();
        drawHorizontalUsageBar(
                graphics,
                ox + STAT_BAR_X,
                oy + THREAD_BAR_Y,
                STAT_BAR_W,
                STAT_BAR_H,
                state.usedThreads(),
                state.maxThreads(),
                NELDLibStyle.DARK_TEXT_SUCCESS);
        long usedStorage = Math.max(0L, state.totalStorage() - state.availableStorage());
        drawHorizontalUsageBar(
                graphics,
                ox + STAT_BAR_X,
                oy + STORAGE_BAR_Y,
                STAT_BAR_W,
                STAT_BAR_H,
                usedStorage,
                state.totalStorage(),
                NELDLibStyle.DARK_TEXT_BLUE);
    }

    @Override
    protected void drawMachineForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        drawLocalString(graphics, title, 8, 8, TEXT_PRIMARY);
        drawHeaderMachineStatus(graphics, currentState());
        drawMainPanelText(graphics, currentState());
        drawLocalString(
                graphics,
                Component.translatable("gui.neoecoae.common.inventory"),
                PLAYER_INV_X,
                PLAYER_INV_LABEL_Y,
                TEXT_MUTED);
        drawTaskPanel(graphics, currentState());
    }

    @Override
    protected void drawMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (renderTaskTooltip(graphics, mouseX, mouseY)) {
            return;
        }
        if (isMouseIn(TOOLBAR_BUTTON_X, TOOLBAR_BUTTON_Y, TOOLBAR_BUTTON_W, TOOLBAR_BUTTON_H, mouseX, mouseY)) {
            CpuSelectionMode mode = currentState().cpuSelectionMode();
            graphics.renderTooltip(
                    font(),
                    List.of(
                            Component.translatable("gui.neoecoae.computation.cpu_selection_mode"),
                            cpuModeTooltip(mode),
                            Component.translatable("gui.neoecoae.computation.cpu_selection_mode.click")),
                    Optional.empty(),
                    mouseX,
                    mouseY);
            return;
        }
        if (isMouseIn(STAT_BAR_X, THREAD_BAR_Y, STAT_BAR_W, STAT_BAR_H, mouseX, mouseY)) {
            graphics.renderTooltip(
                    font(),
                    List.of(
                            Component.translatable("gui.neoecoae.computation.threads"),
                            Component.literal(NELDLibText.usedTotal(
                                    currentState().usedThreads(), currentState().maxThreads()))),
                    Optional.empty(),
                    mouseX,
                    mouseY);
            return;
        }
        if (isMouseIn(STAT_BAR_X, STORAGE_BAR_Y, STAT_BAR_W, STAT_BAR_H, mouseX, mouseY)) {
            long usedStorage =
                    Math.max(0L, currentState().totalStorage() - currentState().availableStorage());
            graphics.renderTooltip(
                    font(),
                    List.of(
                            Component.translatable("gui.neoecoae.computation.available_storage"),
                            Component.literal(NELDLibText.usedTotal(
                                            usedStorage, currentState().totalStorage()) + " bytes")),
                    Optional.empty(),
                    mouseX,
                    mouseY);
        }
    }

    @Override
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        int total = currentState().recipeEntries().size();
        int visible = visibleTaskCardCount();
        if (isMouseIn(TASK_PANEL_X, TASK_PANEL_Y, TASK_PANEL_W, TASK_PANEL_H, (int) mouseX, (int) mouseY)
                && total > visible) {
            taskScrollOffset = clampTaskScrollOffset(taskScrollOffset + (wheelDelta < 0 ? 1 : -1), total);
            return true;
        }
        return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
    }

    private void addPlayerInventorySlots() {
        NEPlayerInventoryWidgets.addPlayerInventorySlots(
                this, playerInventory, PLAYER_INV_X, PLAYER_INV_Y, PLAYER_HOTBAR_Y);
    }

    private void drawPlayerInventorySlots(GuiGraphics graphics) {
        NEPlayerInventoryWidgets.drawPlayerInventorySlots(
                graphics, this::absX, this::absY, PLAYER_INV_X, PLAYER_INV_Y, PLAYER_HOTBAR_Y);
    }

    private void drawMainPanelText(GuiGraphics g, NEComputationUiState state) {
        int x = absX(STAT_X);
        int y = absY(MAIN_PANEL_Y + 8);
        int line = 12;

        drawPairLine(
                g,
                Component.translatable("gui.neoecoae.computation.threads").getString() + ": ",
                state.usedThreads(),
                state.maxThreads(),
                "",
                x,
                y);
        y = absY(MAIN_PANEL_Y + 43);
        NELDLibClientStyle.drawSegment(
                g,
                font(),
                Component.translatable(
                        "gui.neoecoae.computation.parallel_count", NELDLibText.number(state.parallelCount())),
                x,
                y,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        y += line;
        drawModeLine(g, state, x, y);

        long usedStorage = Math.max(0L, state.totalStorage() - state.availableStorage());
        drawStorageUsedLine(g, usedStorage, state.totalStorage(), x, absY(STORAGE_TEXT_Y));
        NELDLibClientStyle.drawSegment(
                g,
                font(),
                Component.translatable(
                        "gui.neoecoae.computation.accelerators", NELDLibText.number(state.accelerators())),
                x,
                absY(PARALLEL_CORES_Y),
                NELDLibStyle.DARK_TEXT_PRIMARY);
    }

    private void drawHeaderMachineStatus(GuiGraphics g, NEComputationUiState state) {
        Component formedLabel =
                Component.translatable("gui.neoecoae.machine.formed").append(": ");
        Component formedValue = boolText(state.formed());
        Component activeLabel = Component.literal("    ")
                .append(Component.translatable("gui.neoecoae.machine.active"))
                .append(": ");
        Component activeValue = boolText(state.active());
        int textW = font().width(formedLabel)
                + font().width(formedValue)
                + font().width(activeLabel)
                + font().width(activeValue);
        int textX = absX(HEADER_STATUS_RIGHT - textW);
        int textY = absY(8);
        g.drawString(font(), formedLabel, textX, textY, 0xFF4A4A4A, false);
        textX += font().width(formedLabel);
        g.drawString(
                font(),
                formedValue,
                textX,
                textY,
                state.formed() ? NELDLibStyle.DARK_TEXT_SUCCESS : NELDLibStyle.DARK_TEXT_ERROR,
                false);
        textX += font().width(formedValue);
        g.drawString(font(), activeLabel, textX, textY, 0xFF4A4A4A, false);
        textX += font().width(activeLabel);
        g.drawString(
                font(),
                activeValue,
                textX,
                textY,
                state.active() ? NELDLibStyle.DARK_TEXT_SUCCESS : NELDLibStyle.DARK_TEXT_MUTED,
                false);
    }

    private void drawTaskPanel(GuiGraphics g, NEComputationUiState state) {
        drawLocalString(
                g,
                Component.translatable("gui.neoecoae.crafting.tasks"),
                TASK_PANEL_X + 8,
                TASK_PANEL_Y + 6,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        drawRightLocalString(
                g,
                Component.literal(NELDLibText.number(state.recipeEntries().size())),
                TASK_PANEL_X + TASK_PANEL_W - 8,
                TASK_PANEL_Y + 6,
                NELDLibStyle.DARK_TEXT_VALUE);

        taskScrollOffset =
                clampTaskScrollOffset(taskScrollOffset, state.recipeEntries().size());
        if (state.recipeEntries().isEmpty()) {
            NELDLibClientStyle.drawCentered(
                    g,
                    font(),
                    Component.translatable("gui.neoecoae.crafting.no_tasks"),
                    absX(TASK_PANEL_X),
                    absY(TASK_PANEL_Y + TASK_PANEL_H / 2 - 4),
                    TASK_PANEL_W,
                    NELDLibStyle.DARK_TEXT_MUTED);
            return;
        }

        int clipLeft = absX(TASK_PANEL_X + 4);
        int clipTop = absY(TASK_CARD_Y);
        int clipRight = absX(TASK_PANEL_X + TASK_PANEL_W - 4);
        int clipBottom = absY(TASK_LIST_BOTTOM_Y + 1);
        g.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        int visible = Math.min(visibleTaskCardCount(), state.recipeEntries().size() - taskScrollOffset);
        for (int i = 0; i < visible; i++) {
            NECraftingRecipeUiEntry entry = state.recipeEntries().get(taskScrollOffset + i);
            drawTaskCard(g, entry, TASK_CARD_Y + i * TASK_CARD_STRIDE);
        }
        g.disableScissor();
        drawTaskScrollbar(g, state.recipeEntries().size(), visibleTaskCardCount());
    }

    private Icon cpuModeIcon() {
        return switch (currentState().cpuSelectionMode()) {
            case PLAYER_ONLY -> Icon.CRAFT_HAMMER;
            case MACHINE_ONLY -> Icon.BACKGROUND_WIRELESS_TERM;
            case ANY -> Icon.TYPE_FILTER_ALL;
        };
    }

    private void drawHorizontalUsageBar(GuiGraphics g, int x, int y, int w, int h, long used, long max, int color) {
        NELDLibClientStyle.drawTinyInsetRect(g, x, y, w, h, 0xFF201E27);
        int ix = x + 3;
        int iy = y + 3;
        int iw = Math.max(0, w - 6);
        int ih = Math.max(0, h - 6);
        if (iw <= 0 || ih <= 0) {
            return;
        }
        int fillW = ratioWidth(used, max, iw);
        g.fill(ix, iy, ix + iw, iy + ih, 0xAA17141E);
        if (fillW > 0) {
            g.fill(ix, iy, ix + fillW, iy + ih, color);
            g.fill(ix, iy, ix + fillW, iy + 1, 0x70FFFFFF);
        }
    }

    private void drawTaskCard(GuiGraphics g, NECraftingRecipeUiEntry entry, int y) {
        NELDLibTaskListPanel.drawCard(
                g,
                font(),
                entry,
                absX(TASK_CARD_X),
                absY(y),
                TASK_CARD_W,
                TASK_CARD_H,
                new NELDLibTaskListPanel.CardStyle(
                        4, 4, 24, 4, 11, 5, 24, TASK_CARD_H - 5, 29, 2, 34, 1.0F, 1.0F, 0.0F));
    }

    private void drawTaskScrollbar(GuiGraphics g, int total, int visible) {
        NELDLibTaskListPanel.drawScrollbar(
                g,
                absX(TASK_PANEL_X + TASK_PANEL_W - 5),
                absY(TASK_CARD_Y),
                TASK_SCROLLBAR_W,
                Math.max(1, TASK_LIST_BOTTOM_Y - TASK_CARD_Y),
                total,
                visible,
                taskScrollOffset);
    }

    private boolean renderTaskTooltip(GuiGraphics g, int mouseX, int mouseY) {
        List<NECraftingRecipeUiEntry> entries = currentState().recipeEntries();
        taskScrollOffset = clampTaskScrollOffset(taskScrollOffset, entries.size());
        int visible = Math.min(visibleTaskCardCount(), entries.size() - taskScrollOffset);
        for (int i = 0; i < visible; i++) {
            int y = TASK_CARD_Y + i * TASK_CARD_STRIDE;
            if (!isMouseIn(TASK_CARD_X, y, TASK_CARD_W, TASK_CARD_H, mouseX, mouseY)) {
                continue;
            }
            NECraftingRecipeUiEntry entry = entries.get(taskScrollOffset + i);
            List<Component> lines = NELDLibTaskListPanel.tooltipLines(entry, false, true, null);
            g.renderTooltip(font(), lines, entry.output().getTooltipImage(), entry.output(), mouseX, mouseY);
            return true;
        }
        return false;
    }

    private int visibleTaskCardCount() {
        return NELDLibTaskListPanel.visibleCardCount(TASK_CARD_Y, TASK_LIST_BOTTOM_Y, TASK_CARD_H, TASK_CARD_STRIDE);
    }

    private int clampTaskScrollOffset(int value, int total) {
        return NELDLibTaskListPanel.clampScrollOffset(
                value, total, TASK_CARD_Y, TASK_LIST_BOTTOM_Y, TASK_CARD_H, TASK_CARD_STRIDE);
    }

    private void drawModeLine(GuiGraphics g, NEComputationUiState state, int x, int y) {
        int cursor = NELDLibClientStyle.drawSegment(
                g,
                font(),
                Component.translatable("gui.neoecoae.computation.cpu_selection_mode.short")
                                .getString() + ": ",
                x,
                y,
                NELDLibStyle.DARK_TEXT_MUTED);
        NELDLibClientStyle.drawSegment(
                g, font(), cpuModeShortLabel(state.cpuSelectionMode()), x + cursor, y, NELDLibStyle.DARK_TEXT_VALUE);
    }

    private void drawPairLine(GuiGraphics g, String prefix, long current, long max, String suffix, int x, int y) {
        drawPairTextLine(g, prefix, NELDLibText.number(current), NELDLibText.number(max), x, y);
        if (!suffix.isEmpty()) {
            NELDLibClientStyle.drawSegment(
                    g, font(), " " + suffix, x + font().width(prefix), y, NELDLibStyle.DARK_TEXT_MUTED);
        }
    }

    private void drawPairTextLine(GuiGraphics g, String prefix, String current, String max, int x, int y) {
        int cursor = NELDLibClientStyle.drawSegment(g, font(), prefix, x, y, NELDLibStyle.DARK_TEXT_MUTED);
        cursor += NELDLibClientStyle.drawSegment(g, font(), current, x + cursor, y, NELDLibStyle.DARK_TEXT_SUCCESS);
        cursor += NELDLibClientStyle.drawSegment(g, font(), " / ", x + cursor, y, NELDLibStyle.DARK_TEXT_MUTED);
        NELDLibClientStyle.drawSegment(g, font(), max, x + cursor, y, NELDLibStyle.DARK_TEXT_VALUE);
    }

    private void drawStorageUsedLine(GuiGraphics g, long usedStorage, long totalStorage, int x, int y) {
        String prefix =
                Component.translatable("gui.neoecoae.computation.storage_used").getString() + ": ";
        String usedText = NELDLibText.storageBytes(usedStorage);
        String totalText = NELDLibText.storageBytes(totalStorage);
        if (pairLineWidth(prefix, usedText, totalText) > STAT_TEXT_MAX_W) {
            usedText = NELDLibText.storageBytesCompact(usedStorage);
            totalText = NELDLibText.storageBytesCompact(totalStorage);
        }
        drawPairTextLine(g, prefix, usedText, totalText, x, y);
    }

    private int pairLineWidth(String prefix, String current, String max) {
        return font().width(prefix + current + " / " + max);
    }

    private static int ratioWidth(long current, long max, int fullWidth) {
        return NELDLibTaskCards.ratioWidth(current, max, fullWidth);
    }

    private static Component cpuModeShortLabel(CpuSelectionMode mode) {
        return switch (mode) {
            case PLAYER_ONLY -> Component.translatable("gui.neoecoae.computation.cpu_selection_mode.short.player");
            case MACHINE_ONLY -> Component.translatable("gui.neoecoae.computation.cpu_selection_mode.short.machine");
            case ANY -> Component.translatable("gui.neoecoae.computation.cpu_selection_mode.short.any");
        };
    }

    private static Component cpuModeTooltip(CpuSelectionMode mode) {
        return switch (mode) {
            case PLAYER_ONLY -> Component.translatable("gui.neoecoae.computation.cpu_selection_mode.player_only");
            case MACHINE_ONLY -> Component.translatable("gui.neoecoae.computation.cpu_selection_mode.machine_only");
            case ANY -> Component.translatable("gui.neoecoae.computation.cpu_selection_mode.any");
        };
    }

    private String fitText(String text, int maxWidth) {
        return NELDLibTextRender.fitWithEllipsis(font(), text, maxWidth);
    }
}
