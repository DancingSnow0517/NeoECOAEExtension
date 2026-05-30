package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.gui.nativeui.NENativeUiConstants;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEStructureTerminalMenu;
import cn.dancingsnow.neoecoae.gui.nativeui.widget.NEAe2TextButton;
import cn.dancingsnow.neoecoae.network.NENetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Structure Terminal configuration UI.
 * <p>
 * Layout: large preview area (top), length controls + buttons (bottom-left),
 * material list placeholder (bottom-right).
 * </p>
 */
public class NEStructureTerminalScreen extends AbstractContainerScreen<NEStructureTerminalMenu> {

    // ── Colours ──

    private static final int DARK_PANEL_OUTER = 0xFF17141E;
    private static final int DARK_PANEL_MIDDLE = 0xFF2B2834;
    private static final int DARK_PANEL_INNER = 0xFF665F6D;
    private static final int DARK_PANEL_LIGHT_EDGE = 0xFFC9C3D6;

    private static final int DARK_TEXT_PRIMARY = 0xFFD6D0E0;
    private static final int DARK_TEXT_VALUE = 0xFF8377FF;
    private static final int DARK_TEXT_USED = 0xFF00FC00;
    private static final int DARK_TEXT_MUTED = 0xFFAAA4B2;
    private static final int DARK_TEXT_SUCCESS = 0xFF6CFFA0;

    // ── Layout constants (uniform 7 px margin) ──

    private static final int PANEL_MARGIN = 7;
    private static final int PANEL_GAP = 7;

    private static final int PREVIEW_X = PANEL_MARGIN;
    private static final int PREVIEW_Y = 24;
    private static final int PREVIEW_W = 358 - PANEL_MARGIN * 2;
    private static final int PREVIEW_H = 101;

    private static final int LOWER_Y = PREVIEW_Y + PREVIEW_H + PANEL_GAP;
    private static final int LOWER_H = 220 - LOWER_Y - PANEL_MARGIN;

    private static final int CONTROL_X = PANEL_MARGIN;
    private static final int CONTROL_Y = LOWER_Y;
    private static final int CONTROL_W = 158;
    private static final int CONTROL_H = LOWER_H;

    private static final int MATERIAL_X = CONTROL_X + CONTROL_W + PANEL_GAP;
    private static final int MATERIAL_Y = LOWER_Y;
    private static final int MATERIAL_W = 358 - MATERIAL_X - PANEL_MARGIN;
    private static final int MATERIAL_H = LOWER_H;

    private int displayBuildLength;
    private int minLength = 1;
    private int maxLength = 12;

    public NEStructureTerminalScreen(NEStructureTerminalMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 358;
        this.imageHeight = 220;
        this.displayBuildLength = menu.getBuildLength();
    }

    public void setBuildLength(int length, int min, int max) {
        this.displayBuildLength = length;
        this.minLength = min;
        this.maxLength = max;
    }

    @Override
    protected void init() {
        super.init();

        int btnH = 20;
        int smallW = 24;
        int mediumW = 44;

        int baseX = leftPos + CONTROL_X + 10;
        int baseY = topPos + CONTROL_Y + 25;

        // Row 1: length adjustment
        addRenderableWidget(new NEAe2TextButton(baseX, baseY, smallW, btnH,
            Component.literal("-"),
            btn -> NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalConfigActionPacket(
                NENetwork.NEStructureTerminalConfigActionPacket.Action.DECREASE))));

        addRenderableWidget(new NEAe2TextButton(baseX + 98, baseY, smallW, btnH,
            Component.literal("+"),
            btn -> NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalConfigActionPacket(
                NENetwork.NEStructureTerminalConfigActionPacket.Action.INCREASE))));

        // Row 2: reset, preview, build
        addRenderableWidget(new NEAe2TextButton(baseX, baseY + 27, mediumW, btnH,
            Component.translatable("gui.neoecoae.structure_terminal.reset"),
            btn -> NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalConfigActionPacket(
                NENetwork.NEStructureTerminalConfigActionPacket.Action.RESET))));

        addRenderableWidget(new NEAe2TextButton(baseX + 49, baseY + 27, mediumW, btnH,
            Component.literal("预览"),
            btn -> {}));

        addRenderableWidget(new NEAe2TextButton(baseX + 98, baseY + 27, mediumW, btnH,
            Component.literal("构建"),
            btn -> {}));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        NENativeAe2StyleRenderer.drawAeMainPanel(guiGraphics, leftPos, topPos, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Title — use machine-native colour (not dark-panel text)
        guiGraphics.drawString(font, title,
            NENativeUiConstants.TITLE_X, NENativeUiConstants.TITLE_Y,
            NENativeUiConstants.MACHINE_TEXT_PRIMARY, false);

        // ── Panels ──
        drawDarkInsetRect(guiGraphics, PREVIEW_X, PREVIEW_Y, PREVIEW_W, PREVIEW_H);
        drawDarkInsetRect(guiGraphics, CONTROL_X, CONTROL_Y, CONTROL_W, CONTROL_H);
        drawDarkInsetRect(guiGraphics, MATERIAL_X, MATERIAL_Y, MATERIAL_W, MATERIAL_H);

        // ── Preview area ──
        guiGraphics.drawString(font, Component.literal("结构预览区域"),
            PREVIEW_X + 10, PREVIEW_Y + 8, DARK_TEXT_PRIMARY, false);

        Component placeholder = Component.literal("3D Preview Placeholder");
        guiGraphics.drawString(font, placeholder,
            PREVIEW_X + (PREVIEW_W - font.width(placeholder)) / 2,
            PREVIEW_Y + PREVIEW_H / 2 - 4,
            DARK_TEXT_MUTED, false);

        Component lengthText = Component.literal("长度: " + displayBuildLength + " / " + maxLength);
        guiGraphics.drawString(font, lengthText,
            PREVIEW_X + 10, PREVIEW_Y + PREVIEW_H - 16,
            DARK_TEXT_MUTED, false);

        // ── Control panel ──
        guiGraphics.drawString(font, Component.literal("结构长度"),
            CONTROL_X + 10, CONTROL_Y + 8, DARK_TEXT_PRIMARY, false);

        String lengthValue = String.valueOf(displayBuildLength);
        int valueX = CONTROL_X + 10 + 24 + (74 - font.width(lengthValue)) / 2;
        int valueY = CONTROL_Y + 31;
        guiGraphics.drawString(font, Component.literal(lengthValue), valueX, valueY, DARK_TEXT_VALUE, false);

        // ── Material panel ──
        drawMaterialListPlaceholder(guiGraphics);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    // ── Helpers ──

    private void drawDarkInsetRect(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFFCBCCD4);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF0D0D11);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF85818D);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFF0D0D11);
        g.fill(x + 4, y + 4, x + w - 4, y + h - 4, 0xFF47434F);
        g.fill(x + 5, y + 5, x + w - 5, y + h - 5, 0xFF605A66);
    }

    private void drawMaterialListPlaceholder(GuiGraphics g) {
        int x = MATERIAL_X + 10;
        int y = MATERIAL_Y + 8;

        g.drawString(font, Component.literal("所需方块"), x, y, DARK_TEXT_PRIMARY, false);

        int listX = MATERIAL_X + 8;
        int listY = MATERIAL_Y + 22;
        int listW = MATERIAL_W - 16;
        int rowH = 12;
        int rows = 4;

        for (int i = 0; i < rows; i++) {
            int rowY = listY + i * rowH;

            // Row separator line
            g.fill(listX, rowY + rowH - 1, listX + listW, rowY + rowH, 0x447A7482);

            // Icon placeholder square
            g.fill(listX, rowY + 1, listX + 10, rowY + 11, 0x4017141E);
            g.fill(listX + 1, rowY + 2, listX + 9, rowY + 10, 0x40665F6D);
        }

        Component empty = Component.literal("暂无材料数据");
        int emptyX = listX + 16;
        int emptyY = listY + 3;
        g.drawString(font, empty, emptyX, emptyY, DARK_TEXT_MUTED, false);
    }
}
