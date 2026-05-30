package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.nativeui.NENativeUiConstants;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NECraftingControllerMenu;
import cn.dancingsnow.neoecoae.network.NECraftingUiState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Screen for the ECO Crafting Controller — machine running status only.
 * <p>
 * Building operations (preview, auto-build, length selection) have been
 * migrated to the {@link NEStructureTerminalScreen}, accessed via the
 * Structure Terminal item.
 * </p>
 */
public class NECraftingControllerScreen extends NEBaseMachineScreen<NECraftingControllerMenu> {

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    // ── Dark panel colours (shared with Storage / Computation) ──
    private static final int DARK_PANEL_OUTER = 0xFF17141E;
    private static final int DARK_PANEL_MIDDLE = 0xFF2B2834;
    private static final int DARK_PANEL_INNER = 0xFF665F6D;
    private static final int DARK_PANEL_LIGHT_EDGE = 0xFFC9C3D6;

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

    private static final int FORMED_BAR_X = PANEL_MARGIN;
    private static final int FORMED_BAR_H = 16;
    private static final int FORMED_BAR_BOTTOM_GAP = 7;

    private boolean hasCraftingState;
    private NECraftingUiState craftingState;

    public NECraftingControllerScreen(NECraftingControllerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, NEMachineScreenConfig.CRAFTING_CONTROLLER);
        this.imageWidth = 300;
        this.imageHeight = 170;
        this.craftingState = NECraftingUiState.empty(menu.getMachinePos());
    }

    /**
     * Called from the network thread via
     * {@link cn.dancingsnow.neoecoae.client.NEClientUiPacketHandlers}.
     */
    public void setCraftingUiState(NECraftingUiState state) {
        this.hasCraftingState = true;
        this.craftingState = state;
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected void renderAdditionalLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        NECraftingUiState s;

        if (hasCraftingState) {
            s = this.craftingState;
        } else {
            ECOCraftingSystemBlockEntity be = getCraftingBE();
            if (be != null) {
                s = be.createCraftingUiState();
            } else {
                s = this.craftingState;
            }
        }

        // ── Main dark panel ──
        drawDarkInsetRect(guiGraphics, MAIN_PANEL_X, MAIN_PANEL_Y, MAIN_PANEL_W, MAIN_PANEL_H);

        int x = MAIN_PANEL_X + 8;
        int y = MAIN_PANEL_Y + 8;
        int line = 12;

        drawLine(guiGraphics, "样板总线数量: " + fmt(s.patternBusCount()), x, y, DARK_TEXT_PRIMARY);
        y += line;
        drawLine(guiGraphics, "并行核心数量: " + fmt(s.parallelCount()), x, y, DARK_TEXT_PRIMARY);
        y += line;
        drawLine(guiGraphics, "工作核心数量: " + fmt(s.workerCount()), x, y, DARK_TEXT_PRIMARY);
        y += line;

        y += line;

        drawPairLine(guiGraphics, "工作线程: ", s.runningThreadCount(), s.threadCount(), " (0%)", x, y);
        y += line;
        drawLine(guiGraphics, "总并行数: " + fmt(s.parallelCount()), x, y, DARK_TEXT_PRIMARY);
        y += line;
        drawBooleanLine(guiGraphics, "超频: ", s.overclocked(), x, y);
        y += line;
        drawBooleanLine(guiGraphics, "主动冷却: ", s.activeCooling(), x, y);

        // ── Formed status bar ──
        drawFormedStatusBar(guiGraphics, s.formed(), imageWidth, imageHeight);
    }

    private ECOCraftingSystemBlockEntity getCraftingBE() {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        BlockEntity be = minecraft.level.getBlockEntity(menu.getMachinePos());
        if (be instanceof ECOCraftingSystemBlockEntity crafting) {
            return crafting;
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
        g.drawString(font, value, textX + font.width(label), textY,
                formed ? DARK_TEXT_SUCCESS : DARK_TEXT_ERROR, false);
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

    public NECraftingControllerMenu getMenu() {
        return menu;
    }
}
