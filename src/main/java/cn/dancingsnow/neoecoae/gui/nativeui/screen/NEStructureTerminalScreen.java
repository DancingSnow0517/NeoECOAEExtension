package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.gui.nativeui.NENativeUiConstants;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEStructureTerminalMenu;
import cn.dancingsnow.neoecoae.network.NENetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.fml.ModList;

import java.util.function.BooleanSupplier;

/**
 * Screen for the Structure Terminal configuration UI.
 * <p>
 * Layout: large preview area (top), length controls + toggle buttons
 * (bottom-left), 2×9 material slot grid (bottom-right).
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

    private static final int CONTENT_Y = 24;
    private static final int CONTENT_H = 220 - CONTENT_Y - PANEL_MARGIN;

    private static final int CONTROL_X = PANEL_MARGIN;
    private static final int CONTROL_Y = CONTENT_Y;
    private static final int CONTROL_W = 154;
    private static final int CONTROL_H = CONTENT_H;

    private static final int MATERIAL_X = CONTROL_X + CONTROL_W + PANEL_GAP;
    private static final int MATERIAL_Y = CONTENT_Y;
    private static final int MATERIAL_W = 358 - MATERIAL_X - PANEL_MARGIN;
    private static final int MATERIAL_H = CONTENT_H;

    private int displayBuildLength;
    private int minLength = 1;
    private int maxLength = 12;

    // Toggle states (client-only, not synced)
    private boolean buildMode = true;
    private boolean dismantleMode = false;
    private boolean expansionMode = false;

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

        int buttonH = 18;
        int smallW = 22;
        int valueW = 46;
        int resetW = smallW * 2 + valueW; // 90
        int modeW = 48;

        int baseX = leftPos + CONTROL_X + 10;
        int baseY = topPos + CONTROL_Y + 24;
        int modeX = leftPos + CONTROL_X + CONTROL_W - modeW - 10;
        int rowGap = 3;

        int row0Y = baseY;
        int row1Y = row0Y + buttonH + rowGap;
        int row2Y = row1Y + buttonH + rowGap;

        // Row 1 (row1Y): - / value / + --- dismantle
        addRenderableWidget(new NEInsetTextButton(baseX, row1Y, smallW, buttonH,
                Component.literal("-"),
                btn -> NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalConfigActionPacket(
                        NENetwork.NEStructureTerminalConfigActionPacket.Action.DECREASE))));

        addRenderableWidget(new NEInsetTextButton(baseX + smallW + valueW, row1Y, smallW, buttonH,
                Component.literal("+"),
                btn -> NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalConfigActionPacket(
                        NENetwork.NEStructureTerminalConfigActionPacket.Action.INCREASE))));

        // Row 2 (row2Y): reset --- expansion
        addRenderableWidget(new NEInsetTextButton(baseX, row2Y, resetW, buttonH,
                Component.translatable("gui.neoecoae.structure_terminal.reset"),
                btn -> NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalConfigActionPacket(
                        NENetwork.NEStructureTerminalConfigActionPacket.Action.RESET))));

        // Row 0 (row0Y): build mode toggle
        addRenderableWidget(new NEToggleTextButton(modeX, row0Y, modeW, buttonH,
                Component.literal("搭建"),
                () -> buildMode,
                btn -> {
                    buildMode = true;
                    dismantleMode = false;
                    expansionMode = false;
                }));

        // Row 1 (row1Y): dismantle mode toggle
        addRenderableWidget(new NEToggleTextButton(modeX, row1Y, modeW, buttonH,
                Component.literal("拆除"),
                () -> dismantleMode,
                btn -> {
                    buildMode = false;
                    dismantleMode = true;
                    expansionMode = false;
                }));

        // Row 2 (row2Y): expansion mode toggle
        addRenderableWidget(new NEToggleTextButton(modeX, row2Y, modeW, buttonH,
                Component.literal("扩建"),
                () -> expansionMode,
                btn -> {
                    buildMode = false;
                    dismantleMode = false;
                    expansionMode = true;
                }));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        NENativeAe2StyleRenderer.drawAeMainPanel(guiGraphics, leftPos, topPos, imageWidth, imageHeight);

        // Draw dark panels and slots in bg layer so buttons render on top.
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(leftPos, topPos, 0);

        drawDarkInsetRect(guiGraphics, CONTROL_X, CONTROL_Y, CONTROL_W, CONTROL_H);
        drawDarkInsetRect(guiGraphics, MATERIAL_X, MATERIAL_Y, MATERIAL_W, MATERIAL_H);
        drawMaterialSlotGrid(guiGraphics);

        guiGraphics.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Title — use machine-native colour (not dark-panel text)
        guiGraphics.drawString(font, title,
                NENativeUiConstants.TITLE_X, NENativeUiConstants.TITLE_Y,
                NENativeUiConstants.MACHINE_TEXT_PRIMARY, false);

        // ── Control panel ──
        int cLabelX = CONTROL_X + 10;
        int cLabelY = CONTROL_Y + 8;
        guiGraphics.drawString(font, Component.literal("结构长度"),
                cLabelX, cLabelY, DARK_TEXT_PRIMARY, false);

        int cSmallW = 22;
        int cValueW = 46;
        int cButtonH = 18;
        int cRowGap = 3;
        int cBaseX = CONTROL_X + 10;
        int cBaseY = CONTROL_Y + 24;
        int cRow1Y = cBaseY + cButtonH + cRowGap;

        String lengthValue = String.valueOf(displayBuildLength);
        int valueBoxX = cBaseX + cSmallW;
        int valueX = valueBoxX + (cValueW - font.width(lengthValue)) / 2;
        int valueY = cRow1Y + (cButtonH - font.lineHeight) / 2;
        guiGraphics.drawString(font, Component.literal(lengthValue), valueX, valueY, DARK_TEXT_VALUE, false);

        // ── Material panel ──
        guiGraphics.drawString(font, Component.literal("所需方块"),
                MATERIAL_X + 10, MATERIAL_Y + 8, DARK_TEXT_PRIMARY, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    // ── Panel / slot drawing ──

    private void drawDarkInsetRect(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFFCBCCD4);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF0D0D11);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF85818D);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFF0D0D11);
        g.fill(x + 4, y + 4, x + w - 4, y + h - 4, 0xFF47434F);
        g.fill(x + 5, y + 5, x + w - 5, y + h - 5, 0xFF605A66);
    }

    private void drawMaterialSlotGrid(GuiGraphics g) {
        int slotSize = 18;
        int cols = 9;
        int rows = 2;

        int gridW = cols * slotSize;
        int gridH = rows * slotSize;

        int startX = MATERIAL_X + (MATERIAL_W - gridW) / 2;
        int startY = MATERIAL_Y + 28;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int x = startX + col * slotSize;
                int y = startY + row * slotSize;
                drawInventorySlot(g, x, y);
            }
        }
    }

    private void drawInventorySlot(GuiGraphics g, int x, int y) {
        // 18×18, tight inventory-style slot
        g.fill(x, y, x + 18, y + 18, 0xFF0D0D11);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF85818D);
        g.fill(x + 2, y + 2, x + 16, y + 16, 0xFF47434F);
        g.fill(x + 3, y + 3, x + 15, y + 15, 0xFF5A5460);

        // Top-left shadow
        g.fill(x + 3, y + 3, x + 15, y + 4, 0xAA17141E);
        g.fill(x + 3, y + 3, x + 4, y + 15, 0xAA17141E);

        // Bottom-right highlight
        g.fill(x + 3, y + 14, x + 15, y + 15, 0x55C9C3D6);
        g.fill(x + 14, y + 3, x + 15, y + 15, 0x55C9C3D6);
    }

    // ── Inset button drawing ──

    private void drawInsetButton(GuiGraphics g, int x, int y, int w, int h,
            boolean hover, boolean pressed, boolean selected) {
        int outer = 0xFF0D0D11;
        int edge = hover ? 0xFFDAD5E8 : 0xFFC9C3D6;
        int mid = selected ? 0xFF3B3445 : 0xFF47434F;
        int inner = selected ? 0xFF282232 : 0xFF5A5460;

        if (pressed) {
            inner = 0xFF211C29;
            mid = 0xFF302A38;
        }

        g.fill(x, y, x + w, y + h, edge);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, outer);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, mid);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, inner);

        if (!pressed) {
            g.fill(x + 3, y + 3, x + w - 3, y + 4, 0x55FFFFFF);
            g.fill(x + 3, y + h - 4, x + w - 3, y + h - 3, 0x99000000);
        } else {
            g.fill(x + 3, y + 3, x + w - 3, y + 4, 0x99000000);
        }

        if (selected) {
            g.fill(x + 3, y + h - 4, x + w - 3, y + h - 3, DARK_TEXT_SUCCESS);
        }
    }

    // ── Inner button classes ──

    private class NEInsetTextButton extends Button {
        private boolean pressed;

        private NEInsetTextButton(int x, int y, int w, int h, Component message, OnPress onPress) {
            super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            boolean result = super.mouseClicked(mouseX, mouseY, button);
            if (result) {
                pressed = true;
            }
            return result;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            pressed = false;
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean hover = isHoveredOrFocused();
            drawInsetButton(g, getX(), getY(), width, height, hover, pressed, false);

            int color = active ? DARK_TEXT_PRIMARY : DARK_TEXT_MUTED;
            int tx = getX() + (width - font.width(getMessage())) / 2;
            int ty = getY() + (height - font.lineHeight) / 2 + (pressed ? 1 : 0);
            g.drawString(font, getMessage(), tx, ty, color, false);
        }
    }

    private class NEToggleTextButton extends Button {
        private final BooleanSupplier selectedSupplier;
        private boolean pressed;

        private NEToggleTextButton(int x, int y, int w, int h, Component message,
                BooleanSupplier selectedSupplier, OnPress onPress) {
            super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
            this.selectedSupplier = selectedSupplier;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            boolean result = super.mouseClicked(mouseX, mouseY, button);
            if (result) {
                pressed = true;
            }
            return result;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            pressed = false;
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean selected = selectedSupplier.getAsBoolean();
            boolean hover = isHoveredOrFocused();
            drawInsetButton(g, getX(), getY(), width, height, hover, pressed, selected);

            int color = selected ? DARK_TEXT_SUCCESS : DARK_TEXT_MUTED;
            int tx = getX() + (width - font.width(getMessage())) / 2;
            int ty = getY() + (height - font.lineHeight) / 2 + (pressed ? 1 : 0);
            g.drawString(font, getMessage(), tx, ty, color, false);
        }
    }

    // ── LDLib1 optional preview detection ──

    private static boolean hasLDLib1() {
        return ModList.get().isLoaded("ldlib");
    }
}
