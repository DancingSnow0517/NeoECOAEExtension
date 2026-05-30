package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NECraftingControllerMenu;
import cn.dancingsnow.neoecoae.network.NECraftingUiState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

    private static final int DARK_PANEL_OUTER = 0xFF17141E;
    private static final int DARK_PANEL_MIDDLE = 0xFF2B2834;
    private static final int DARK_PANEL_INNER = 0xFF665A66;
    private static final int DARK_PANEL_LIGHT_EDGE = 0xFFC9C3D6;

    private static final int DARK_TEXT_PRIMARY = 0xFFD6D0E0;
    private static final int DARK_TEXT_VALUE = 0xFF8377FF;
    private static final int DARK_TEXT_MUTED = 0xFFAAA4B2;
    private static final int DARK_TEXT_USED = 0xFF00FC00;
    private static final int DARK_TEXT_SUCCESS = 0xFF6CFFA0;
    private static final int DARK_TEXT_ERROR = 0xFFFF6A75;

    private static final int PANEL_MARGIN = 7;
    private static final int MAIN_PANEL_X = PANEL_MARGIN;
    private static final int MAIN_PANEL_Y = 24;
    private static final int MAIN_PANEL_W = 286;
    private static final int MAIN_PANEL_H = 112;

    private static final int FORMED_BAR_H = 16;
    private static final int FORMED_BAR_BOTTOM_GAP = 7;

    private static final int SIDE_BUTTON_SIZE = 22;
    private static final int SIDE_BUTTON_GAP = 4;
    private static final int SIDE_BUTTON_RIGHT_GAP = 5;
    private static final int SIDE_BUTTON_TOP_OFFSET = 6;

    private boolean hasCraftingState;
    private NECraftingUiState craftingState;

    public NECraftingControllerScreen(NECraftingControllerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, NEMachineScreenConfig.CRAFTING_CONTROLLER);
        this.imageWidth = 300;
        this.imageHeight = 170;
        this.craftingState = NECraftingUiState.empty(menu.getMachinePos());
    }

    /** Called from the network thread via {@link cn.dancingsnow.neoecoae.client.NEClientUiPacketHandlers}. */
    public void setCraftingUiState(NECraftingUiState state) {
        this.hasCraftingState = true;
        this.craftingState = state;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos - SIDE_BUTTON_SIZE - SIDE_BUTTON_RIGHT_GAP;
        int y = topPos + SIDE_BUTTON_TOP_OFFSET;
        addRenderableWidget(new IconButton(x, y, new ItemStack(Items.REDSTONE_TORCH),
            Component.literal("切换超频"), btn -> {
                // Server-side toggle packet is intentionally not wired in this UI pass.
            }));
        y += SIDE_BUTTON_SIZE + SIDE_BUTTON_GAP;
        addRenderableWidget(new IconButton(x, y, new ItemStack(Items.BLUE_ICE),
            Component.literal("切换主动冷却"), btn -> {
                // Server-side toggle packet is intentionally not wired in this UI pass.
            }));
        y += SIDE_BUTTON_SIZE + SIDE_BUTTON_GAP;
        addRenderableWidget(new IconButton(x, y, new ItemStack(Items.BARRIER),
            Component.literal("清空输入槽"), btn -> {
                // Server-side clear packet is intentionally not wired in this UI pass.
            }));
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

        drawDarkInsetRect(guiGraphics, MAIN_PANEL_X, MAIN_PANEL_Y, MAIN_PANEL_W, MAIN_PANEL_H);

        int x = MAIN_PANEL_X + 8;
        int y = MAIN_PANEL_Y + 8;
        int line = 12;

        drawLine(guiGraphics, "样板总线数量: " + fmt(s.patternBusCount()), x, y, DARK_TEXT_PRIMARY);
        y += line;
        drawLine(guiGraphics, "并行核心数量: " + fmt(s.parallelCount()), x, y, DARK_TEXT_PRIMARY);
        y += line;
        drawLine(guiGraphics, "工作核心数量: " + fmt(s.workerCount()), x, y, DARK_TEXT_PRIMARY);
        y += line * 2;

        drawPairLine(guiGraphics, "工作线程: ", s.runningThreadCount(), s.threadCount(), " (0%)", x, y);
        y += line;
        drawLine(guiGraphics, "总并行数: " + fmt(s.parallelCount()), x, y, DARK_TEXT_PRIMARY);
        y += line;
        drawBooleanLine(guiGraphics, "超频: ", s.overclocked(), x, y);
        y += line;
        drawBooleanLine(guiGraphics, "主动冷却: ", s.activeCooling(), x, y);

        drawFormedStatusBar(guiGraphics, s.formed(), imageWidth, imageHeight);
    }

    private void drawDarkInsetRect(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFFCBCCD4);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF0D0D11);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF85818D);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFF0D0D11);
        g.fill(x + 4, y + 4, x + w - 4, y + h - 4, 0xFF47434F);
        g.fill(x + 5, y + 5, x + w - 5, y + h - 5, 0xFF605A66);
    }

    private void drawTinyInsetRect(GuiGraphics g, int x, int y, int w, int h, int innerColor) {
        g.fill(x, y, x + w, y + h, DARK_PANEL_LIGHT_EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, DARK_PANEL_OUTER);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, innerColor);
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

    private void drawPairLine(GuiGraphics g, String prefix, long used, long max, String suffix, int x, int y) {
        int cursor = drawSegment(g, prefix, x, y, DARK_TEXT_MUTED);
        cursor += drawSegment(g, fmt(used), x + cursor, y, DARK_TEXT_USED);
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

    private static String fmt(long value) {
        return NUMBER_FORMAT.format(value);
    }

    public NECraftingControllerMenu getMenu() {
        return menu;
    }

    private class IconButton extends Button {
        private final ItemStack icon;

        private IconButton(int x, int y, ItemStack icon, Component tooltip, OnPress onPress) {
            super(x, y, SIDE_BUTTON_SIZE, SIDE_BUTTON_SIZE, tooltip, onPress, DEFAULT_NARRATION);
            this.icon = icon;
            setTooltip(Tooltip.create(tooltip));
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int bg = isHoveredOrFocused() ? DARK_PANEL_MIDDLE : 0xFF201E27;
            drawTinyInsetRect(g, getX(), getY(), width, height, bg);
            g.renderItem(icon, getX() + (width - 16) / 2, getY() + (height - 16) / 2);
        }
    }
}
