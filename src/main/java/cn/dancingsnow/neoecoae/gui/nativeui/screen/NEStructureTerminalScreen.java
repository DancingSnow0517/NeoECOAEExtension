package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.gui.nativeui.NENativeUiConstants;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEStructureTerminalMenu;
import cn.dancingsnow.neoecoae.multiblock.NEStructureTerminalUiState;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalHostType;
import cn.dancingsnow.neoecoae.network.NENetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.fml.ModList;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Screen for the Structure Terminal configuration UI.
 * <p>
 * Layout: large preview area (top), length controls + toggle buttons
 * (bottom-left), 2×10 material slot grid (bottom-right).
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
    private static final int CONTENT_H = 140 - CONTENT_Y - PANEL_MARGIN;

    private static final int CONTROL_X = PANEL_MARGIN;
    private static final int CONTROL_Y = CONTENT_Y;
    private static final int CONTROL_W = 122;
    private static final int CONTROL_H = CONTENT_H;

    private static final int MATERIAL_X = CONTROL_X + CONTROL_W + PANEL_GAP;
    private static final int MATERIAL_Y = CONTENT_Y;
    private static final int MATERIAL_W = 358 - MATERIAL_X - PANEL_MARGIN;
    private static final int MATERIAL_H = CONTENT_H;
    private static final int MATERIAL_COLS = 10;
    private static final int MATERIAL_ROWS = 2;
    private static final int MATERIAL_SLOT_SIZE = 18;

    private int displayBuildLength;
    private int minLength = 1;
    private int maxLength = 12;
    private StructureTerminalHostType selectedTarget;
    private List<NEStructureTerminalUiState.BuildMaterialEntry> materials;
    private int materialScrollOffset;
    /**
     * True once the user has explicitly selected a target or received a server
     * config.
     */
    private boolean hasActiveTarget;

    public NEStructureTerminalScreen(NEStructureTerminalMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 358;
        this.imageHeight = 140;
        this.displayBuildLength = menu.getBuildLength();
        this.minLength = menu.getMinLength();
        this.maxLength = menu.getMaxLength();
        this.selectedTarget = menu.getHostType();
        this.materials = menu.getMaterials();
    }

    public void setBuildLength(int length, int min, int max) {
        this.displayBuildLength = length;
        this.minLength = min;
        this.maxLength = max;
    }

    public void setConfig(
            int length,
            int min,
            int max,
            StructureTerminalHostType target,
            List<NEStructureTerminalUiState.BuildMaterialEntry> materials) {
        boolean resetScroll = target != selectedTarget || this.materials.size() != materials.size();
        this.displayBuildLength = length;
        this.minLength = min;
        this.maxLength = max;
        this.selectedTarget = target;
        this.hasActiveTarget = target != null;
        this.materials = List.copyOf(materials);
        this.menu.setClientConfig(length, min, max, target, materials);
        if (resetScroll) {
            this.materialScrollOffset = 0;
        } else {
            this.materialScrollOffset = clampMaterialScroll(this.materialScrollOffset);
        }
    }

    @Override
    protected void init() {
        super.init();

        int buttonH = 18;
        int rowGap = 3;

        int innerY = topPos + CONTROL_Y + 24;

        int smallW = 22;
        int valueW = 35;
        int leftGroupW = smallW + valueW + smallW; // 79
        int innerX = leftPos + CONTROL_X + (CONTROL_W - leftGroupW) / 2;

        int row0Y = innerY;
        int row1Y = row0Y + buttonH + rowGap;
        int row2Y = row1Y + buttonH + rowGap;

        // Row 1 (row1Y): - / value / + --- dismantle
        addRenderableWidget(new NEInsetTextButton(innerX, row1Y, smallW, buttonH,
                Component.literal("-"),
                btn -> NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalConfigActionPacket(
                        NENetwork.NEStructureTerminalConfigActionPacket.Action.DECREASE))));

        addRenderableWidget(new NEInsetTextButton(innerX + smallW + valueW, row1Y, smallW, buttonH,
                Component.literal("+"),
                btn -> NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalConfigActionPacket(
                        NENetwork.NEStructureTerminalConfigActionPacket.Action.INCREASE))));

        // Row 2 (row2Y): reset --- expansion
        addRenderableWidget(new NEInsetTextButton(innerX, row2Y, leftGroupW, buttonH,
                Component.translatable("gui.neoecoae.structure_terminal.reset"),
                btn -> NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalConfigActionPacket(
                        NENetwork.NEStructureTerminalConfigActionPacket.Action.RESET))));

        addTargetButton(StructureTerminalHostType.CRAFTING, 0,
                Component.translatable("gui.neoecoae.structure_terminal.target.crafting"),
                Component.translatable("gui.neoecoae.structure_terminal.target.crafting.tooltip"),
                NENetwork.NEStructureTerminalConfigActionPacket.Action.SELECT_CRAFTING);
        addTargetButton(StructureTerminalHostType.STORAGE, 1,
                Component.translatable("gui.neoecoae.structure_terminal.target.storage"),
                Component.translatable("gui.neoecoae.structure_terminal.target.storage.tooltip"),
                NENetwork.NEStructureTerminalConfigActionPacket.Action.SELECT_STORAGE);
        addTargetButton(StructureTerminalHostType.COMPUTATION, 2,
                Component.translatable("gui.neoecoae.structure_terminal.target.computation"),
                Component.translatable("gui.neoecoae.structure_terminal.target.computation.tooltip"),
                NENetwork.NEStructureTerminalConfigActionPacket.Action.SELECT_COMPUTATION);
    }

    private void addTargetButton(StructureTerminalHostType target, int index, Component label, Component tooltip,
            NENetwork.NEStructureTerminalConfigActionPacket.Action action) {
        int buttonW = 52;
        int buttonH = 18;
        int gap = 4;
        int totalW = buttonW * 3 + gap * 2;
        int x = leftPos + MATERIAL_X + (MATERIAL_W - totalW) / 2 + index * (buttonW + gap);
        int y = topPos + MATERIAL_Y + 20;
        NEToggleTextButton button = new NEToggleTextButton(x, y, buttonW, buttonH,
                label,
                () -> hasActiveTarget && selectedTarget == target,
                btn -> {
                    this.hasActiveTarget = true;
                    this.selectedTarget = target;
                    NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalConfigActionPacket(action));
                });
        button.setTooltip(Tooltip.create(tooltip));
        addRenderableWidget(button);
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
        Component lengthLabel = Component.literal("可变长度: " + displayBuildLength);
        guiGraphics.drawString(font, lengthLabel,
                CONTROL_X + (CONTROL_W - font.width(lengthLabel)) / 2, CONTROL_Y + 8, DARK_TEXT_PRIMARY, false);

        Component maxLenLabel = Component.literal("(最大长度:" + maxLength + ")");
        guiGraphics.drawString(font, maxLenLabel,
                CONTROL_X + (CONTROL_W - font.width(maxLenLabel)) / 2, CONTROL_Y + 8 + font.lineHeight + 1,
                DARK_TEXT_MUTED, false);

        int cButtonH = 18;
        int cRowGap = 3;
        int cSmallW = 22;
        int cValueW = 35;
        int cGroupW = cSmallW + cValueW + cSmallW;

        int cInnerX = CONTROL_X + (CONTROL_W - cGroupW) / 2;
        int cInnerY = CONTROL_Y + 24;
        int cRow1Y = cInnerY + cButtonH + cRowGap;

        String lengthValue = String.valueOf(displayBuildLength);
        int valueBoxX = cInnerX + cSmallW;
        int valueX = valueBoxX + (cValueW - font.width(lengthValue)) / 2;
        int valueY = cRow1Y + (cButtonH - font.lineHeight) / 2;
        guiGraphics.drawString(font, Component.literal(lengthValue), valueX, valueY, DARK_TEXT_VALUE, false);

        // ── Material panel ──
        guiGraphics.drawString(font, Component.literal("所需方块"),
                MATERIAL_X + 10, MATERIAL_Y + 8, DARK_TEXT_PRIMARY, false);
        if (materials.size() > visibleMaterialSlots()) {
            int start = materialScrollOffset + 1;
            int end = Math.min(materialScrollOffset + visibleMaterialSlots(), materials.size());
            String pageText = start + "-" + end + "/" + materials.size();
            guiGraphics.drawString(font, Component.literal(pageText),
                    MATERIAL_X + MATERIAL_W - 10 - font.width(pageText),
                    MATERIAL_Y + MATERIAL_H - 13,
                    DARK_TEXT_MUTED,
                    false);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderMaterialItems(guiGraphics);
        renderTooltip(guiGraphics, mouseX, mouseY);
        renderMaterialTooltip(guiGraphics, mouseX, mouseY);
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
        int gridW = MATERIAL_COLS * MATERIAL_SLOT_SIZE;

        int startX = MATERIAL_X + (MATERIAL_W - gridW) / 2;
        int startY = materialGridY();

        for (int row = 0; row < MATERIAL_ROWS; row++) {
            for (int col = 0; col < MATERIAL_COLS; col++) {
                int x = startX + col * MATERIAL_SLOT_SIZE;
                int y = startY + row * MATERIAL_SLOT_SIZE;
                drawInventorySlot(g, x, y);
            }
        }
    }

    private void renderMaterialItems(GuiGraphics g) {
        int count = Math.min(visibleMaterialSlots(), Math.max(0, materials.size() - materialScrollOffset));
        for (int i = 0; i < count; i++) {
            NEStructureTerminalUiState.BuildMaterialEntry entry = materials.get(materialScrollOffset + i);
            int x = leftPos + materialSlotX(i);
            int y = topPos + materialSlotY(i);
            g.renderItem(entry.item(), x + 1, y + 1);
            String text = "x" + formatCompactCount(entry.required());
            int color = materialCountColor(entry);
            int textX = x + 17 - font.width(text);
            int textY = y + 10;
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.drawString(font, Component.literal(text), textX, textY, color, true);
            g.pose().popPose();
        }
    }

    private void renderMaterialTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int index = materialIndexAt(mouseX, mouseY);
        if (index < 0 || index >= materials.size()) {
            return;
        }
        NEStructureTerminalUiState.BuildMaterialEntry entry = materials.get(index);
        int missing = Math.max(0, entry.required() - entry.available());
        g.renderTooltip(font, List.of(
                entry.item().getHoverName().getVisualOrderText(),
                Component.literal("需要: " + entry.required()).getVisualOrderText(),
                Component.literal("拥有: " + entry.available()).getVisualOrderText(),
                Component.literal("缺少: " + missing).getVisualOrderText()),
                mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isInMaterialGrid(mouseX, mouseY) && materials.size() > visibleMaterialSlots()) {
            int step = MATERIAL_COLS;
            int next = materialScrollOffset + (delta < 0 ? step : -step);
            materialScrollOffset = clampMaterialScroll(next);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private int materialCountColor(NEStructureTerminalUiState.BuildMaterialEntry entry) {
        if (entry.available() >= entry.required()) {
            return DARK_TEXT_SUCCESS;
        }
        if (entry.available() > 0) {
            return 0xFFFFC857;
        }
        return 0xFFFF6A75;
    }

    private int materialIndexAt(double mouseX, double mouseY) {
        int slot = materialVisibleSlotAt(mouseX, mouseY);
        return slot < 0 ? -1 : materialScrollOffset + slot;
    }

    private int materialVisibleSlotAt(double mouseX, double mouseY) {
        for (int i = 0; i < visibleMaterialSlots(); i++) {
            int x = leftPos + materialSlotX(i);
            int y = topPos + materialSlotY(i);
            if (mouseX >= x && mouseX < x + MATERIAL_SLOT_SIZE && mouseY >= y && mouseY < y + MATERIAL_SLOT_SIZE) {
                return i;
            }
        }
        return -1;
    }

    private boolean isInMaterialGrid(double mouseX, double mouseY) {
        int x = leftPos + MATERIAL_X;
        int y = topPos + materialGridY();
        return mouseX >= x && mouseX < x + MATERIAL_W
                && mouseY >= y && mouseY < y + MATERIAL_ROWS * MATERIAL_SLOT_SIZE;
    }

    private int materialSlotX(int visibleIndex) {
        int gridW = MATERIAL_COLS * MATERIAL_SLOT_SIZE;
        int startX = MATERIAL_X + (MATERIAL_W - gridW) / 2;
        return startX + (visibleIndex % MATERIAL_COLS) * MATERIAL_SLOT_SIZE;
    }

    private int materialSlotY(int visibleIndex) {
        return materialGridY() + (visibleIndex / MATERIAL_COLS) * MATERIAL_SLOT_SIZE;
    }

    private int materialGridY() {
        return MATERIAL_Y + 54;
    }

    private int visibleMaterialSlots() {
        return MATERIAL_COLS * MATERIAL_ROWS;
    }

    private static String formatCompactCount(int value) {
        if (value < 1000) {
            return Integer.toString(value);
        }
        if (value < 1_000_000) {
            return (value / 1000) + "K";
        }
        return (value / 1_000_000) + "M";
    }

    private int clampMaterialScroll(int value) {
        int max = Math.max(0, materials.size() - visibleMaterialSlots());
        return Mth.clamp(value, 0, max);
    }

    private void drawInventorySlot(GuiGraphics g, int x, int y) {
        // Tight inventory-style 18×18 slot
        g.fill(x, y, x + 18, y + 18, 0xFF2B2834);

        // Top-left shadow
        g.fill(x, y, x + 18, y + 1, 0xFF0D0D11);
        g.fill(x, y, x + 1, y + 18, 0xFF0D0D11);

        // Bottom-right highlight
        g.fill(x, y + 17, x + 18, y + 18, 0xFFC9C3D6);
        g.fill(x + 17, y, x + 18, y + 18, 0xFFC9C3D6);

        // Inner face
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF4B4653);

        // Subtle recess
        g.fill(x + 2, y + 2, x + 16, y + 16, 0xFF5A5460);
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
