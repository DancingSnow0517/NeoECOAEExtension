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
 * material list placeholder (bottom-right), status bar (bottom).
 * </p>
 */
public class NEStructureTerminalScreen extends AbstractContainerScreen<NEStructureTerminalMenu> {

    // ── Layout constants ──

    private static final int DARK_PANEL_OUTER = 0xFF17141E;
    private static final int DARK_PANEL_MIDDLE = 0xFF2B2834;
    private static final int DARK_PANEL_INNER = 0xFF665F6D;
    private static final int DARK_PANEL_LIGHT_EDGE = 0xFFC9C3D6;

    private static final int DARK_TEXT_PRIMARY = 0xFFD6D0E0;
    private static final int DARK_TEXT_VALUE = 0xFF8377FF;
    private static final int DARK_TEXT_USED = 0xFF00FC00;
    private static final int DARK_TEXT_MUTED = 0xFFAAA4B2;
    private static final int DARK_TEXT_SUCCESS = 0xFF6CFFA0;

    private static final int PREVIEW_X = 9;
    private static final int PREVIEW_Y = 24;
    private static final int PREVIEW_W = 340;
    private static final int PREVIEW_H = 96;

    private static final int CONTROL_X = 9;
    private static final int CONTROL_Y = 128;
    private static final int CONTROL_W = 160;
    private static final int CONTROL_H = 59;

    private static final int MATERIAL_X = 178;
    private static final int MATERIAL_Y = 128;
    private static final int MATERIAL_W = 171;
    private static final int MATERIAL_H = 59;

    private static final int BOTTOM_BAR_X = 9;
    private static final int BOTTOM_BAR_Y = 197;
    private static final int BOTTOM_BAR_W = 340;
    private static final int BOTTOM_BAR_H = 16;

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

        int x = leftPos + CONTROL_X + 10;
        int y = topPos + CONTROL_Y + 9;

        // Row 1: length adjustment
        addRenderableWidget(new NEAe2TextButton(x, y, smallW, btnH,
            Component.literal("-"),
            btn -> NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalConfigActionPacket(
                NENetwork.NEStructureTerminalConfigActionPacket.Action.DECREASE))));

        addRenderableWidget(new NEAe2TextButton(x + 98, y, smallW, btnH,
            Component.literal("+"),
            btn -> NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalConfigActionPacket(
                NENetwork.NEStructureTerminalConfigActionPacket.Action.INCREASE))));

        // Row 2: reset, preview, build
        addRenderableWidget(new NEAe2TextButton(x, y + 27, mediumW, btnH,
            Component.translatable("gui.neoecoae.structure_terminal.reset"),
            btn -> NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalConfigActionPacket(
                NENetwork.NEStructureTerminalConfigActionPacket.Action.RESET))));

        addRenderableWidget(new NEAe2TextButton(x + 49, y + 27, mediumW, btnH,
            Component.literal("预览"),
            btn -> {}));

        addRenderableWidget(new NEAe2TextButton(x + 98, y + 27, mediumW, btnH,
            Component.literal("构建"),
            btn -> {}));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        NENativeAe2StyleRenderer.drawAeMainPanel(guiGraphics, leftPos, topPos, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Title
        guiGraphics.drawString(font, title,
            NENativeUiConstants.TITLE_X, NENativeUiConstants.TITLE_Y,
            NENativeUiConstants.MACHINE_TEXT_PRIMARY);

        // ── Panels ──
        drawDarkInsetRect(guiGraphics, PREVIEW_X, PREVIEW_Y, PREVIEW_W, PREVIEW_H);
        drawDarkInsetRect(guiGraphics, CONTROL_X, CONTROL_Y, CONTROL_W, CONTROL_H);
        drawDarkInsetRect(guiGraphics, MATERIAL_X, MATERIAL_Y, MATERIAL_W, MATERIAL_H);
        drawDarkInsetRect(guiGraphics, BOTTOM_BAR_X, BOTTOM_BAR_Y, BOTTOM_BAR_W, BOTTOM_BAR_H);

        // ── Preview area ──
        guiGraphics.drawString(font, Component.literal("结构预览区域"),
            PREVIEW_X + 10, PREVIEW_Y + 8, DARK_TEXT_PRIMARY, false);

        Component lengthText = Component.literal("长度: " + displayBuildLength + " / " + maxLength);
        guiGraphics.drawString(font, lengthText,
            PREVIEW_X + 10, PREVIEW_Y + PREVIEW_H - 16, DARK_TEXT_MUTED, false);

        Component placeholder = Component.literal("3D Preview Placeholder");
        int placeholderX = PREVIEW_X + (PREVIEW_W - font.width(placeholder)) / 2;
        int placeholderY = PREVIEW_Y + PREVIEW_H / 2 - 4;
        guiGraphics.drawString(font, placeholder, placeholderX, placeholderY, DARK_TEXT_MUTED, false);

        // ── Control panel ──
        guiGraphics.drawString(font, Component.literal("结构长度"),
            CONTROL_X + 10, CONTROL_Y + 8, DARK_TEXT_PRIMARY, false);

        String lengthValue = String.valueOf(displayBuildLength);
        int valueX = CONTROL_X + 10 + 24 + (74 - font.width(lengthValue)) / 2;
        int valueY = CONTROL_Y + 16;
        guiGraphics.drawString(font, Component.literal(lengthValue), valueX, valueY, DARK_TEXT_VALUE, false);

        Component range = Component.literal("范围: " + minLength + " - " + maxLength);
        guiGraphics.drawString(font, range,
            CONTROL_X + 10, CONTROL_Y + CONTROL_H - 13, DARK_TEXT_MUTED, false);

        // ── Material panel ──
        guiGraphics.drawString(font, Component.literal("所需方块"),
            MATERIAL_X + 10, MATERIAL_Y + 8, DARK_TEXT_PRIMARY, false);

        guiGraphics.drawString(font, Component.literal("等待结构预览数据"),
            MATERIAL_X + 10, MATERIAL_Y + 24, DARK_TEXT_MUTED, false);

        guiGraphics.drawString(font, Component.literal("- 暂无材料列表"),
            MATERIAL_X + 10, MATERIAL_Y + 38, DARK_TEXT_MUTED, false);

        // ── Bottom status bar ──
        Component bottom = Component.literal("调整长度后可预览并构建结构");
        int bottomX = BOTTOM_BAR_X + (BOTTOM_BAR_W - font.width(bottom)) / 2;
        int bottomY = BOTTOM_BAR_Y + (BOTTOM_BAR_H - font.lineHeight) / 2;
        guiGraphics.drawString(font, bottom, bottomX, bottomY, DARK_TEXT_PRIMARY, false);
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
}
