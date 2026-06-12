package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import appeng.api.config.CpuSelectionMode;
import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEComputationUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibAe2StyleRenderer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStateCodecs;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class NEComputationControllerWidget extends NELDLibSyncedStateWidget<NEComputationUiState> {
    public static final int UI_WIDTH = 300;
    public static final int UI_HEIGHT = 170;
    private static final int PANEL_MARGIN = 7;
    private static final int MAIN_PANEL_X = PANEL_MARGIN;
    private static final int MAIN_PANEL_Y = 24;
    private static final int MAIN_PANEL_W = 286;
    private static final int MAIN_PANEL_H = 112;
    private static final int FORMED_BAR_H = 25;
    private static final int FORMED_BAR_BOTTOM_GAP = 7;
    private static final int TOOLBAR_BUTTON_X = UI_WIDTH - PANEL_MARGIN - 18;
    private static final int TOOLBAR_BUTTON_Y = 4;
    private static final int TOOLBAR_BUTTON_W = 18;
    private static final int TOOLBAR_BUTTON_H = 20;

    private static final int THREAD_BAR_X = MAIN_PANEL_X + 98;
    private static final int THREAD_BAR_Y = MAIN_PANEL_Y + 20;
    private static final int THREAD_BAR_W = 166;
    private static final int THREAD_BAR_H = 9;
    private static final int STORAGE_BAR_X = THREAD_BAR_X;
    private static final int STORAGE_BAR_Y = MAIN_PANEL_Y + 67;
    private static final int STORAGE_BAR_W = THREAD_BAR_W;
    private static final int STORAGE_BAR_H = 9;

    private final ECOComputationSystemBlockEntity computation;

    public NEComputationControllerWidget(ECOComputationSystemBlockEntity computation) {
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
    }

    @Override
    protected boolean shouldAddTitleWidget() {
        return false;
    }

    @Override
    protected void initLdWidgets() {
        addWidget(new ButtonWidget(
                TOOLBAR_BUTTON_X,
                TOOLBAR_BUTTON_Y,
                TOOLBAR_BUTTON_W,
                TOOLBAR_BUTTON_H,
                NELDLibStyle.aeToolbarButton(),
                click -> {
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
    }

    @Override
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int ox = getPositionX();
        int oy = getPositionY();
        NELDLibStyle.drawDarkInsetRect(graphics, ox + MAIN_PANEL_X, oy + MAIN_PANEL_Y, MAIN_PANEL_W, MAIN_PANEL_H);
        NELDLibStyle.drawDarkInsetRect(
                graphics,
                ox + PANEL_MARGIN,
                oy + height - FORMED_BAR_BOTTOM_GAP - FORMED_BAR_H,
                width - PANEL_MARGIN * 2,
                FORMED_BAR_H);

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
        drawCpuModeIcon(graphics);
        drawMainPanelText(graphics, currentState());
        drawFormedBar(graphics, currentState());
    }

    @Override
    protected void drawMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
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
        NELDLibStyle.drawSegment(
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
        NELDLibStyle.drawSegment(
                g,
                font(),
                Component.translatable(
                        "gui.neoecoae.computation.accelerators", NELDLibText.number(state.accelerators())),
                x,
                y,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        y += line;
        drawBooleanLine(
                g, Component.translatable("gui.neoecoae.machine.active").getString() + ": ", state.active(), x, y);
    }

    private void drawFormedBar(GuiGraphics g, NEComputationUiState state) {
        int x = absX(PANEL_MARGIN);
        int y = absY(height - FORMED_BAR_BOTTOM_GAP - FORMED_BAR_H);
        int w = width - PANEL_MARGIN * 2;
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
        int textX = x + (w - textW) / 2;
        int textY = y + (FORMED_BAR_H - font().lineHeight) / 2;
        g.drawString(font(), formedLabel, textX, textY, NELDLibStyle.DARK_TEXT_PRIMARY, false);
        textX += font().width(formedLabel);
        g.drawString(
                font(),
                formedValue,
                textX,
                textY,
                state.formed() ? NELDLibStyle.DARK_TEXT_SUCCESS : NELDLibStyle.DARK_TEXT_ERROR,
                false);
        textX += font().width(formedValue);
        g.drawString(font(), activeLabel, textX, textY, NELDLibStyle.DARK_TEXT_PRIMARY, false);
        textX += font().width(activeLabel);
        g.drawString(
                font(),
                activeValue,
                textX,
                textY,
                state.active() ? NELDLibStyle.DARK_TEXT_SUCCESS : NELDLibStyle.DARK_TEXT_MUTED,
                false);
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
        NELDLibStyle.drawTinyInsetRect(g, x, y, w, h, 0xFF201E27);
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

    private void drawModeLine(GuiGraphics g, NEComputationUiState state, int x, int y) {
        int cursor = NELDLibStyle.drawSegment(
                g,
                font(),
                Component.translatable("gui.neoecoae.computation.cpu_selection_mode.short")
                                .getString() + ": ",
                x,
                y,
                NELDLibStyle.DARK_TEXT_MUTED);
        NELDLibStyle.drawSegment(
                g, font(), cpuModeShortLabel(state.cpuSelectionMode()), x + cursor, y, NELDLibStyle.DARK_TEXT_VALUE);
    }

    private void drawPairLine(GuiGraphics g, String prefix, long current, long max, String suffix, int x, int y) {
        drawPairTextLine(g, prefix, NELDLibText.number(current), NELDLibText.number(max), x, y);
        if (!suffix.isEmpty()) {
            NELDLibStyle.drawSegment(
                    g, font(), " " + suffix, x + font().width(prefix), y, NELDLibStyle.DARK_TEXT_MUTED);
        }
    }

    private void drawPairTextLine(GuiGraphics g, String prefix, String current, String max, int x, int y) {
        int cursor = NELDLibStyle.drawSegment(g, font(), prefix, x, y, NELDLibStyle.DARK_TEXT_MUTED);
        cursor += NELDLibStyle.drawSegment(g, font(), current, x + cursor, y, NELDLibStyle.DARK_TEXT_SUCCESS);
        cursor += NELDLibStyle.drawSegment(g, font(), " / ", x + cursor, y, NELDLibStyle.DARK_TEXT_MUTED);
        NELDLibStyle.drawSegment(g, font(), max, x + cursor, y, NELDLibStyle.DARK_TEXT_VALUE);
    }

    private void drawBooleanLine(GuiGraphics g, String prefix, boolean value, int x, int y) {
        int cursor = NELDLibStyle.drawSegment(g, font(), prefix, x, y, NELDLibStyle.DARK_TEXT_MUTED);
        NELDLibStyle.drawSegment(
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
}
