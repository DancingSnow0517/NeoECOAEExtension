package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import appeng.api.config.CpuSelectionMode;
import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.client.gui.ldlib.NELDLibClientStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEComputationUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingRecipeUiEntry;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibAe2StyleRenderer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibGuiRenderState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibScrollBar;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStateCodecs;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibTextRender;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEPlayerInventoryWidgets;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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
    private static final int MAIN_PANEL_W = SLOT_SIZE * 9 + 2;
    private static final int MAIN_PANEL_H = 132;
    private static final int TOOLBAR_BUTTON_X = UI_WIDTH - PANEL_MARGIN - 18;
    private static final int TOOLBAR_BUTTON_Y = 4;
    private static final int TOOLBAR_BUTTON_W = 18;
    private static final int TOOLBAR_BUTTON_H = 20;
    private static final int HEADER_STATUS_RIGHT = TOOLBAR_BUTTON_X - 4;

    private static final int THREAD_BAR_X = MAIN_PANEL_X + 78;
    private static final int THREAD_BAR_Y = MAIN_PANEL_Y + 20;
    private static final int THREAD_BAR_W = MAIN_PANEL_X + MAIN_PANEL_W - THREAD_BAR_X - 12;
    private static final int THREAD_BAR_H = 9;
    private static final int STORAGE_BAR_X = THREAD_BAR_X;
    private static final int STORAGE_BAR_Y = MAIN_PANEL_Y + 67;
    private static final int STORAGE_BAR_W = THREAD_BAR_W;
    private static final int STORAGE_BAR_H = 9;
    private static final int PLAYER_INV_X = MAIN_PANEL_X + 1;
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
    private static final int TASK_CARD_H = 18;
    private static final int TASK_CARD_STRIDE = 20;
    private static final int TASK_LIST_BOTTOM_Y = TASK_PANEL_Y + TASK_PANEL_H - 3;
    private static final int TASK_SCROLLBAR_W = 3;

    private final ECOComputationSystemBlockEntity computation;
    private final Inventory playerInventory;
    private int taskScrollOffset;

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
        addWidget(new ButtonWidget(
                TOOLBAR_BUTTON_X, TOOLBAR_BUTTON_Y, TOOLBAR_BUTTON_W, TOOLBAR_BUTTON_H, IGuiTexture.EMPTY, click -> {
                    if (!click.isRemote) {
                        NEComputationCluster cluster = computation.getCluster();
                        if (cluster != null) {
                            cluster.cycleSelectionMode();
                            computation.markComputationStatsDirty();
                            computation.updateInfos();
                            syncStateNow();
                        }
                    }
                }));
        addPlayerInventorySlots();
    }

    @Override
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int ox = getPositionX();
        int oy = getPositionY();
        NELDLibClientStyle.drawDarkInsetRect(
                graphics, ox + MAIN_PANEL_X, oy + MAIN_PANEL_Y, MAIN_PANEL_W, MAIN_PANEL_H);
        NELDLibClientStyle.drawDarkInsetRect(
                graphics, ox + TASK_PANEL_X, oy + TASK_PANEL_Y, TASK_PANEL_W, TASK_PANEL_H);
        NELDLibClientStyle.drawAeToolbarButton(
                graphics,
                mouseX,
                mouseY,
                ox + TOOLBAR_BUTTON_X,
                oy + TOOLBAR_BUTTON_Y,
                TOOLBAR_BUTTON_W,
                TOOLBAR_BUTTON_H,
                false);
        drawPlayerInventorySlots(graphics);

        NEComputationUiState state = currentState();
        drawHorizontalUsageBar(
                graphics,
                ox + THREAD_BAR_X,
                oy + THREAD_BAR_Y,
                THREAD_BAR_W,
                THREAD_BAR_H,
                state.usedThreads(),
                state.maxThreads(),
                NELDLibStyle.DARK_TEXT_SUCCESS);
        long usedStorage = Math.max(0L, state.totalStorage() - state.availableStorage());
        drawHorizontalUsageBar(
                graphics,
                ox + STORAGE_BAR_X,
                oy + STORAGE_BAR_Y,
                STORAGE_BAR_W,
                STORAGE_BAR_H,
                usedStorage,
                state.totalStorage(),
                NELDLibStyle.DARK_TEXT_BLUE);
    }

    @Override
    protected void drawMachineForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        drawLocalString(graphics, title, 8, 8, TEXT_PRIMARY);
        drawHeaderMachineStatus(graphics, currentState());
        drawCpuModeIcon(graphics);
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
        if (isMouseIn(THREAD_BAR_X, THREAD_BAR_Y, THREAD_BAR_W, THREAD_BAR_H, mouseX, mouseY)) {
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
        if (isMouseIn(STORAGE_BAR_X, STORAGE_BAR_Y, STORAGE_BAR_W, STORAGE_BAR_H, mouseX, mouseY)) {
            long usedStorage =
                    Math.max(0L, currentState().totalStorage() - currentState().availableStorage());
            graphics.renderTooltip(
                    font(),
                    List.of(
                            Component.translatable("gui.neoecoae.computation.available_storage"),
                            Component.literal(NELDLibText.usedTotalFull(
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
        int x = absX(MAIN_PANEL_X + 8);
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
        y += line;
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
        y += line * 2;

        long usedStorage = Math.max(0L, state.totalStorage() - state.availableStorage());
        drawPairTextLine(
                g,
                Component.translatable("gui.neoecoae.computation.storage_used").getString() + ": ",
                NELDLibText.storageBytes(usedStorage),
                NELDLibText.storageBytes(state.totalStorage()),
                x,
                y);
        y += line;
        NELDLibClientStyle.drawSegment(
                g,
                font(),
                Component.translatable(
                        "gui.neoecoae.computation.accelerators", NELDLibText.number(state.accelerators())),
                x,
                y,
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

    private void drawCpuModeIcon(GuiGraphics graphics) {
        Icon icon =
                switch (currentState().cpuSelectionMode()) {
                    case PLAYER_ONLY -> Icon.CRAFT_HAMMER;
                    case MACHINE_ONLY -> Icon.BACKGROUND_WIRELESS_TERM;
                    case ANY -> Icon.TYPE_FILTER_ALL;
                };
        NELDLibAe2StyleRenderer.drawAeIcon(
                graphics,
                icon,
                absX(TOOLBAR_BUTTON_X + (TOOLBAR_BUTTON_W - icon.width) / 2),
                absY(TOOLBAR_BUTTON_Y + (TOOLBAR_BUTTON_H - icon.height) / 2));
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
        int x = TASK_CARD_X;
        int absX = absX(x);
        int absY = absY(y);
        drawTaskCardRect(g, absX, absY, TASK_CARD_W, TASK_CARD_H, taskStatusColor(entry.status()));
        if (!entry.output().isEmpty()) {
            NELDLibGuiRenderState.beginVanillaGuiItemBatch(g);
            try {
                NELDLibGuiRenderState.renderVanillaSlotItem(g, font(), entry.output(), absX + 1, absY + 1, "");
            } finally {
                NELDLibGuiRenderState.endVanillaGuiItemBatch(g);
            }
        }

        int textX = x + 21;
        int textY = y + 5;
        String amountText = "x" + formatTaskAmount(entry.outputAmount());
        int amountW = font().width(amountText);
        int maxNameW = Math.max(16, TASK_CARD_W - 31 - amountW);
        String name = fitText(entry.output().getHoverName().getString(), maxNameW);
        g.drawString(font(), name, absX(textX), absY(textY), NELDLibStyle.DARK_TEXT_PRIMARY, false);
        NELDLibClientStyle.drawRight(
                g,
                font(),
                Component.literal(amountText),
                absX(TASK_CARD_X + TASK_CARD_W - 5),
                absY(textY),
                NELDLibStyle.DARK_TEXT_VALUE);
        drawTaskProgressBar(g, absX + 21, absY + TASK_CARD_H - 4, TASK_CARD_W - 26, 2, entry);
    }

    private void drawTaskCardRect(GuiGraphics g, int x, int y, int w, int h, int accentColor) {
        g.fill(x, y, x + w, y + h, 0xFFD8D3E4);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF121016);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF4D4855);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFF2C2735);
        g.fill(x + 3, y + h - 3, x + w - 3, y + h - 2, accentColor);
    }

    private void drawTaskProgressBar(GuiGraphics g, int x, int y, int w, int h, NECraftingRecipeUiEntry entry) {
        g.fill(x, y, x + w, y + h, 0xAA17141E);
        int fillW = ratioWidth(Math.max(0L, entry.totalTicks() - entry.remainingTicks()), entry.totalTicks(), w);
        if (entry.status() == NECraftingRecipeUiEntry.Status.WAITING_OUTPUT) {
            fillW = w;
        } else if (entry.status() == NECraftingRecipeUiEntry.Status.QUEUED) {
            fillW = 1;
        }
        if (fillW > 0) {
            g.fill(x, y, x + fillW, y + h, taskStatusColor(entry.status()));
        }
    }

    private void drawTaskScrollbar(GuiGraphics g, int total, int visible) {
        if (total <= visible) {
            return;
        }
        NELDLibScrollBar.drawVertical(
                g,
                absX(TASK_PANEL_X + TASK_PANEL_W - 5),
                absY(TASK_CARD_Y),
                TASK_SCROLLBAR_W,
                Math.max(1, TASK_LIST_BOTTOM_Y - TASK_CARD_Y),
                total,
                visible,
                taskScrollOffset,
                0xAA17141E,
                0xFF8B83A0,
                10);
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
            List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), entry.output()));
            lines.add(Component.translatable(taskStatusKey(entry.status())));
            lines.add(Component.translatable(
                    "gui.neoecoae.crafting.task.amount", formatTaskAmount(entry.outputAmount())));
            if (entry.totalTicks() > 0L) {
                long done = Math.max(0L, entry.totalTicks() - entry.remainingTicks());
                lines.add(Component.literal(NELDLibText.percentOrNA(done, entry.totalTicks())));
            }
            g.renderTooltip(font(), lines, entry.output().getTooltipImage(), entry.output(), mouseX, mouseY);
            return true;
        }
        return false;
    }

    private int visibleTaskCardCount() {
        int space = TASK_LIST_BOTTOM_Y - TASK_CARD_Y;
        if (space < TASK_CARD_H) {
            return 1;
        }
        return Math.max(1, 1 + (space - TASK_CARD_H) / TASK_CARD_STRIDE);
    }

    private int clampTaskScrollOffset(int value, int total) {
        return Mth.clamp(value, 0, Math.max(0, total - visibleTaskCardCount()));
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

    private void drawBooleanLine(GuiGraphics g, String prefix, boolean value, int x, int y) {
        int cursor = NELDLibClientStyle.drawSegment(g, font(), prefix, x, y, NELDLibStyle.DARK_TEXT_MUTED);
        NELDLibClientStyle.drawSegment(
                g,
                font(),
                boolText(value),
                x + cursor,
                y,
                value ? NELDLibStyle.DARK_TEXT_SUCCESS : NELDLibStyle.DARK_TEXT_ERROR);
    }

    private static int ratioWidth(long current, long max, int fullWidth) {
        if (fullWidth <= 0 || max <= 0 || current <= 0) {
            return 0;
        }
        long clamped = Math.max(0L, Math.min(current, max));
        return (int) Math.max(1L, Math.min(fullWidth, clamped * fullWidth / max));
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

    private static int taskStatusColor(NECraftingRecipeUiEntry.Status status) {
        return switch (status) {
            case RUNNING -> NELDLibStyle.DARK_TEXT_SUCCESS;
            case QUEUED -> NELDLibStyle.DARK_TEXT_WARNING;
            case WAITING_OUTPUT -> NELDLibStyle.DARK_TEXT_BLUE;
        };
    }

    private static String taskStatusKey(NECraftingRecipeUiEntry.Status status) {
        return switch (status) {
            case RUNNING -> "gui.neoecoae.crafting.task.status.running";
            case QUEUED -> "gui.neoecoae.crafting.task.status.queued";
            case WAITING_OUTPUT -> "gui.neoecoae.crafting.task.status.waiting_output";
        };
    }

    private String fitText(String text, int maxWidth) {
        return NELDLibTextRender.fitWithEllipsis(font(), text, maxWidth);
    }

    private static String formatTaskAmount(long value) {
        long safe = Math.max(0L, value);
        if (safe < 1_000L) {
            return Long.toString(safe);
        }
        if (safe < 1_000_000L) {
            return compactDecimal(safe, 1_000L, "K");
        }
        if (safe < 1_000_000_000L) {
            return compactDecimal(safe, 1_000_000L, "M");
        }
        if (safe < 1_000_000_000_000L) {
            return compactDecimal(safe, 1_000_000_000L, "G");
        }
        return compactDecimal(safe, 1_000_000_000_000L, "T");
    }

    private static String compactDecimal(long value, long unit, String suffix) {
        double scaled = (double) value / (double) unit;
        if (scaled >= 100.0D || Math.abs(scaled - Math.rint(scaled)) < 0.05D) {
            return String.format(Locale.US, "%.0f%s", scaled, suffix);
        }
        return String.format(Locale.US, "%.1f%s", scaled, suffix);
    }
}
