package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import appeng.api.config.CpuSelectionMode;
import appeng.api.config.Settings;
import appeng.client.gui.widgets.ITooltip;
import appeng.client.gui.widgets.SettingToggleButton;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEComputationControllerMenu;
import cn.dancingsnow.neoecoae.network.NEComputationUiState;
import cn.dancingsnow.neoecoae.network.NENetwork;
import java.text.NumberFormat;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Screen for the ECO Computation Controller with live read-only status.
 * <p>
 * Primary display path: S2C {@link NEComputationUiState} pushed from the server
 * menu tick. Before the first packet arrives the screen shows a brief fallback
 * read from the client-side BE (opening-time snapshot, not live).
 * </p>
 */
public class NEComputationControllerScreen extends NEBaseMachineScreen<NEComputationControllerMenu> {

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    // ── Dark panel colours (shared with Storage / Crafting) ──
    private static final int DARK_TEXT_PRIMARY = 0xFFD6D0E0;
    private static final int DARK_TEXT_VALUE = 0xFF8377FF;
    private static final int DARK_TEXT_MUTED = 0xFFAAA4B2;
    private static final int DARK_TEXT_SUCCESS = 0xFF6CFFA0;
    private static final int DARK_TEXT_ERROR = 0xFFFF6A75;

    private static final int PANEL_MARGIN = 7;
    private static final int MAIN_PANEL_X = PANEL_MARGIN;
    private static final int MAIN_PANEL_Y = 24;
    private static final int MAIN_PANEL_W = 286;
    private static final int MAIN_PANEL_H = 112;

    private static final int FORMED_BAR_H = 25;
    private static final int FORMED_BAR_BOTTOM_GAP = 7;

    // ── Left toolbar (CPU auto-selection mode button) ──
    private static final int TOOLBAR_BUTTON_X_OFFSET = -22;
    private static final int TOOLBAR_BUTTON_Y_OFFSET = 8;

    private boolean hasComputationState;
    private NEComputationUiState computationState;
    private SettingToggleButton<CpuSelectionMode> cpuSelectionModeButton;

    public NEComputationControllerScreen(NEComputationControllerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, NEMachineScreenConfig.COMPUTATION_CONTROLLER);
        this.imageWidth = 300;
        this.imageHeight = 170;
        this.computationState = NEComputationUiState.empty(menu.getMachinePos());
    }

    /**
     * Called from the network thread via
     * {@link cn.dancingsnow.neoecoae.client.NEClientUiPacketHandlers}.
     */
    public void setComputationUiState(NEComputationUiState state) {
        this.hasComputationState = true;
        this.computationState = state;
        if (cpuSelectionModeButton != null) {
            cpuSelectionModeButton.set(state.cpuSelectionMode());
        }
    }

    @Override
    protected void init() {
        super.init();

        // CPU auto-selection mode button (left toolbar, AE2 native SettingToggleButton)
        cpuSelectionModeButton = new SettingToggleButton<>(
                Settings.CPU_SELECTION_MODE,
                CpuSelectionMode.ANY,
                (btn, forward) -> NENetwork.CHANNEL.sendToServer(
                        new NENetwork.NEComputationCpuSelectionModePacket(menu.getMachinePos())));
        cpuSelectionModeButton.setX(leftPos + TOOLBAR_BUTTON_X_OFFSET);
        cpuSelectionModeButton.setY(topPos + TOOLBAR_BUTTON_Y_OFFSET);
        addRenderableWidget(cpuSelectionModeButton);

        // Apply current state if already available
        if (hasComputationState) {
            cpuSelectionModeButton.set(computationState.cpuSelectionMode());
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        // Render AE2 ITooltip tooltips for SettingToggleButton (etc.)
        for (var widget : renderables) {
            if (widget instanceof ITooltip tooltip && tooltip.isTooltipAreaVisible()) {
                var area = tooltip.getTooltipArea();
                if (area.contains(mouseX, mouseY)) {
                    guiGraphics.renderTooltip(
                            font, tooltip.getTooltipMessage(), java.util.Optional.empty(), mouseX, mouseY);
                }
            }
        }
    }

    @Override
    protected void renderAdditionalLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        NEComputationUiState s;

        if (hasComputationState) {
            s = this.computationState;
        } else {
            // Opening-time fallback: read client BE once while waiting for the
            // first S2C packet. Not used for live refresh.
            ECOComputationSystemBlockEntity be = getComputationBE();
            if (be != null) {
                s = new NEComputationUiState(
                        menu.getMachinePos(),
                        be.isFormed(),
                        false, // active not reliably readable from client BE
                        be.getUsedThread(),
                        be.getTotalThread(),
                        be.getAvailableBytes(),
                        be.getTotalBytes(),
                        be.getParallelCount(),
                        be.getAcceleratorCount(),
                        be.getCpuSelectionMode());
            } else {
                s = this.computationState;
            }
        }

        // ── Main dark panel ──
        drawDarkInsetRect(guiGraphics, MAIN_PANEL_X, MAIN_PANEL_Y, MAIN_PANEL_W, MAIN_PANEL_H);

        int x = MAIN_PANEL_X + 8;
        int y = MAIN_PANEL_Y + 8;
        int line = 12;

        drawPairLine(guiGraphics, "已用线程: ", s.usedThreads(), s.maxThreads(), "", x, y);
        y += line;
        drawLine(guiGraphics, "并行数: " + fmt(s.parallelCount()), x, y, DARK_TEXT_PRIMARY);
        y += line;

        y += line;

        drawPairLine(guiGraphics, "已用存储: ", s.availableStorage(), s.totalStorage(), "", x, y);
        y += line;
        drawLine(guiGraphics, "加速器: " + fmt(s.accelerators()), x, y, DARK_TEXT_PRIMARY);
        y += line;
        drawBooleanLine(guiGraphics, "活动: ", s.active(), x, y);

        // ── Formed status bar ──
        drawFormedStatusBar(guiGraphics, s.formed(), imageWidth, imageHeight);
    }

    private ECOComputationSystemBlockEntity getComputationBE() {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        BlockEntity be = minecraft.level.getBlockEntity(menu.getMachinePos());
        if (be instanceof ECOComputationSystemBlockEntity comp) {
            return comp;
        }
        return null;
    }

    // ── Shared drawing helpers ──

    private void drawDarkInsetRect(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFFCBCCD4);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF0D0D11);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF85818D);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFF0D0D11);
        g.fill(x + 4, y + 4, x + w - 4, y + h - 4, 0xFF47434F);
        g.fill(x + 5, y + 5, x + w - 5, y + h - 5, 0xFF605A66);
    }

    private void drawFormedStatusBar(GuiGraphics g, boolean formed, int imageWidth, int imageHeight) {
        int x = PANEL_MARGIN;
        int y = imageHeight - FORMED_BAR_BOTTOM_GAP - FORMED_BAR_H;
        int w = imageWidth - PANEL_MARGIN * 2;
        int h = FORMED_BAR_H;

        drawDarkInsetRect(g, x, y, w, h);

        Component label = Component.translatable("gui.neoecoae.machine.formed").append(": ");
        Component value = boolText(formed);

        int textW = font.width(label) + font.width(value);
        int textX = x + (w - textW) / 2;
        int textY = y + (h - font.lineHeight) / 2;

        g.drawString(font, label, textX, textY, DARK_TEXT_PRIMARY, false);
        g.drawString(
                font, value, textX + font.width(label), textY, formed ? DARK_TEXT_SUCCESS : DARK_TEXT_ERROR, false);
    }

    private void drawLine(GuiGraphics g, String text, int x, int y, int color) {
        g.drawString(font, Component.literal(text), x, y, color, false);
    }

    private void drawPairLine(GuiGraphics g, String prefix, long current, long max, String suffix, int x, int y) {
        int cursor = drawSegment(g, prefix, x, y, DARK_TEXT_MUTED);
        cursor += drawSegment(g, fmt(current), x + cursor, y, DARK_TEXT_SUCCESS);
        cursor += drawSegment(g, " / ", x + cursor, y, DARK_TEXT_MUTED);
        cursor += drawSegment(g, fmt(max), x + cursor, y, DARK_TEXT_VALUE);
        if (!suffix.isEmpty()) {
            drawSegment(g, suffix, x + cursor, y, DARK_TEXT_MUTED);
        }
    }

    private void drawBooleanLine(GuiGraphics g, String prefix, boolean value, int x, int y) {
        int cursor = drawSegment(g, prefix, x, y, DARK_TEXT_MUTED);
        drawSegment(g, value ? "是" : "否", x + cursor, y, value ? DARK_TEXT_SUCCESS : DARK_TEXT_ERROR);
    }

    private int drawSegment(GuiGraphics g, String text, int x, int y, int color) {
        g.drawString(font, Component.literal(text), x, y, color, false);
        return font.width(text);
    }

    private static String fmt(long value) {
        return NUMBER_FORMAT.format(value);
    }

    public NEComputationControllerMenu getMenu() {
        return menu;
    }
}
