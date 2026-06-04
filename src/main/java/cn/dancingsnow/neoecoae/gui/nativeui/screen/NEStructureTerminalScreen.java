package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.gui.nativeui.NENativeUiConstants;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEStructureTerminalMenu;
import cn.dancingsnow.neoecoae.multiblock.NEStructureTerminalUiState;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalHostType;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalMode;
import cn.dancingsnow.neoecoae.network.NENetwork;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

// NEStructureTerminalScreen 是结构终端的 GUI 界面类，负责显示和交互结构终端的相关信息和功能。
// 这个界面分为两个主要区域：左侧的控制区和右侧的材料区。控制区显示当前的建造长度、模式和目标选择，并提供相应的按钮进行调整。
// 材料区显示建造所需的方块列表，并支持滚动查看。
public class NEStructureTerminalScreen extends AbstractContainerScreen<NEStructureTerminalMenu> {

    // 颜色

    private static final int DARK_PANEL_OUTER = 0xFF17141E;
    private static final int DARK_PANEL_MIDDLE = 0xFF2B2834;
    private static final int DARK_PANEL_INNER = 0xFF665F6D;
    private static final int DARK_PANEL_LIGHT_EDGE = 0xFFC9C3D6;

    private static final int DARK_TEXT_PRIMARY = 0xFFD6D0E0;
    private static final int DARK_TEXT_VALUE = 0xFF8377FF;
    private static final int DARK_TEXT_USED = 0xFF00FC00;
    private static final int DARK_TEXT_MUTED = 0xFFAAA4B2;
    private static final int DARK_TEXT_SUCCESS = 0xFF6CFFA0;

    // 布局常量

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

    // 控制区按钮相关常量

    private static final int CONTROL_BUTTON_GAP = 7;
    private static final int CONTROL_BUTTON_H = 18;

    // 三行按钮整体宽度。31 * 3 + 7 * 2 = 107。
    // 第三行"建造 / 镜像建造 / 拆除"可以等宽排列，并且左右整体对齐。
    private static final int CONTROL_BUTTON_ROW_W = 107;
    private static final int CONTROL_BUTTON_ROW_X = CONTROL_X + (CONTROL_W - CONTROL_BUTTON_ROW_W) / 2;

    // 按钮区第一行 Y。这里避开上面的"可变长度"和"最大长度"两行文字。
    private static final int CONTROL_BUTTON_ROW0_Y = CONTROL_Y + 34;
    private static final int CONTROL_BUTTON_ROW1_Y = CONTROL_BUTTON_ROW0_Y + CONTROL_BUTTON_H + CONTROL_BUTTON_GAP;
    private static final int CONTROL_BUTTON_ROW2_Y = CONTROL_BUTTON_ROW1_Y + CONTROL_BUTTON_H + CONTROL_BUTTON_GAP;

    private static final int LENGTH_BUTTON_W = 22;
    private static final int LENGTH_VALUE_W = CONTROL_BUTTON_ROW_W - LENGTH_BUTTON_W * 2 - CONTROL_BUTTON_GAP * 2;

    private static final int MODE_BUTTON_W = 31;

    private int displayBuildLength;
    private int minLength = 1;
    private int maxLength = 12;
    private StructureTerminalHostType selectedTarget;
    private StructureTerminalMode selectedMode;
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
        this.selectedMode = menu.getOperationMode();
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
            StructureTerminalMode mode,
            List<NEStructureTerminalUiState.BuildMaterialEntry> materials) {
        boolean resetScroll = target != selectedTarget || this.materials.size() != materials.size();
        this.displayBuildLength = length;
        this.minLength = min;
        this.maxLength = max;
        this.selectedTarget = target;
        this.selectedMode = mode;
        this.hasActiveTarget = target != null;
        this.materials = List.copyOf(materials);
        this.menu.setClientConfig(length, min, max, target, mode, materials);
        if (resetScroll) {
            this.materialScrollOffset = 0;
        } else {
            this.materialScrollOffset = clampMaterialScroll(this.materialScrollOffset);
        }
    }

    @Override
    protected void init() {
        super.init();

        int rowX = leftPos + CONTROL_BUTTON_ROW_X;

        int row0Y = topPos + CONTROL_BUTTON_ROW0_Y;
        int row1Y = topPos + CONTROL_BUTTON_ROW1_Y;
        int row2Y = topPos + CONTROL_BUTTON_ROW2_Y;

        int minusX = rowX;
        int valueBoxX = minusX + LENGTH_BUTTON_W + CONTROL_BUTTON_GAP;
        int plusX = valueBoxX + LENGTH_VALUE_W + CONTROL_BUTTON_GAP;

        // 第一行：- / x / +
        addRenderableWidget(new NEInsetTextButton(
                minusX,
                row0Y,
                LENGTH_BUTTON_W,
                CONTROL_BUTTON_H,
                Component.literal("-"),
                btn -> NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalConfigActionPacket(
                        NENetwork.NEStructureTerminalConfigActionPacket.Action.DECREASE))));

        addRenderableWidget(new NEInsetTextButton(
                plusX,
                row0Y,
                LENGTH_BUTTON_W,
                CONTROL_BUTTON_H,
                Component.literal("+"),
                btn -> NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalConfigActionPacket(
                        NENetwork.NEStructureTerminalConfigActionPacket.Action.INCREASE))));

        // 第二行：重置，整行居中并与第一行、第三行左右对齐
        addRenderableWidget(new NEInsetTextButton(
                rowX,
                row1Y,
                CONTROL_BUTTON_ROW_W,
                CONTROL_BUTTON_H,
                Component.translatable("gui.neoecoae.structure_terminal.reset"),
                btn -> NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalConfigActionPacket(
                        NENetwork.NEStructureTerminalConfigActionPacket.Action.RESET))));

        // 第三行：建造 / 镜像建造 / 拆除
        addModeButton(
                StructureTerminalMode.BUILD,
                0,
                Component.translatable("gui.neoecoae.structure_terminal.mode.build"),
                Component.translatable("gui.neoecoae.structure_terminal.mode.build.tooltip"),
                NENetwork.NEStructureTerminalConfigActionPacket.Action.SELECT_BUILD_MODE);
        addModeButton(
                StructureTerminalMode.MIRRORED_BUILD,
                1,
                Component.translatable("gui.neoecoae.structure_terminal.mode.mirrored_build"),
                Component.translatable("gui.neoecoae.structure_terminal.mode.mirrored_build.tooltip"),
                NENetwork.NEStructureTerminalConfigActionPacket.Action.SELECT_MIRRORED_BUILD_MODE);
        addModeButton(
                StructureTerminalMode.DISMANTLE,
                2,
                Component.translatable("gui.neoecoae.structure_terminal.mode.dismantle"),
                Component.translatable("gui.neoecoae.structure_terminal.mode.dismantle.tooltip"),
                NENetwork.NEStructureTerminalConfigActionPacket.Action.SELECT_DISMANTLE_MODE);

        // 目标选择按钮，居中于材料区，三者等宽排列
        addTargetButton(
                StructureTerminalHostType.CRAFTING,
                0,
                Component.translatable("gui.neoecoae.structure_terminal.target.crafting"),
                Component.translatable("gui.neoecoae.structure_terminal.target.crafting.tooltip"),
                NENetwork.NEStructureTerminalConfigActionPacket.Action.SELECT_CRAFTING);
        addTargetButton(
                StructureTerminalHostType.STORAGE,
                1,
                Component.translatable("gui.neoecoae.structure_terminal.target.storage"),
                Component.translatable("gui.neoecoae.structure_terminal.target.storage.tooltip"),
                NENetwork.NEStructureTerminalConfigActionPacket.Action.SELECT_STORAGE);
        addTargetButton(
                StructureTerminalHostType.COMPUTATION,
                2,
                Component.translatable("gui.neoecoae.structure_terminal.target.computation"),
                Component.translatable("gui.neoecoae.structure_terminal.target.computation.tooltip"),
                NENetwork.NEStructureTerminalConfigActionPacket.Action.SELECT_COMPUTATION);
    }
    // 帮助去除重复代码的函数：添加模式选择按钮和目标选择按钮的函数。它们的实现非常相似，都是根据传入的参数创建一个 NEToggleTextButton，并设置相应的点击事件和工具提示。
    private void addModeButton(
            StructureTerminalMode mode,
            int index,
            Component label,
            Component tooltip,
            NENetwork.NEStructureTerminalConfigActionPacket.Action action) {
        int x = leftPos + CONTROL_BUTTON_ROW_X + index * (MODE_BUTTON_W + CONTROL_BUTTON_GAP);
        int y = topPos + CONTROL_BUTTON_ROW2_Y;

        NEToggleTextButton button = new NEToggleTextButton(
                x, y, MODE_BUTTON_W, CONTROL_BUTTON_H, label, () -> selectedMode == mode, btn -> {
                    this.selectedMode = mode;
                    NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalConfigActionPacket(action));
                });
        button.setTooltip(Tooltip.create(tooltip));
        addRenderableWidget(button);
    }

    private void addTargetButton(
            StructureTerminalHostType target,
            int index,
            Component label,
            Component tooltip,
            NENetwork.NEStructureTerminalConfigActionPacket.Action action) {
        int buttonW = 52;
        int buttonH = 18;
        int gap = 4;
        int totalW = buttonW * 3 + gap * 2;
        int x = leftPos + MATERIAL_X + (MATERIAL_W - totalW) / 2 + index * (buttonW + gap);
        int y = topPos + MATERIAL_Y + 20;
        NEToggleTextButton button = new NEToggleTextButton(
                x, y, buttonW, buttonH, label, () -> hasActiveTarget && selectedTarget == target, btn -> {
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

        // 绘制控制区和材料区的内嵌面板
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(leftPos, topPos, 0);

        drawDarkInsetRect(guiGraphics, CONTROL_X, CONTROL_Y, CONTROL_W, CONTROL_H);
        drawDarkInsetRect(guiGraphics, MATERIAL_X, MATERIAL_Y, MATERIAL_W, MATERIAL_H);
        drawMaterialSlotGrid(guiGraphics);

        guiGraphics.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 标题
        guiGraphics.drawString(
                font,
                title,
                NENativeUiConstants.TITLE_X,
                NENativeUiConstants.TITLE_Y,
                NENativeUiConstants.MACHINE_TEXT_PRIMARY,
                false);

        // 控制区标题和数值
        Component lengthLabel = Component.literal("可变长度: " + displayBuildLength);
        guiGraphics.drawString(
                font,
                lengthLabel,
                CONTROL_X + (CONTROL_W - font.width(lengthLabel)) / 2,
                CONTROL_Y + 8,
                DARK_TEXT_PRIMARY,
                false);

        // 最大长度提示
        Component maxLenLabel = Component.literal("(最大长度:" + maxLength + ")");
        guiGraphics.drawString(
                font,
                maxLenLabel,
                CONTROL_X + (CONTROL_W - font.width(maxLenLabel)) / 2,
                CONTROL_Y + 8 + font.lineHeight + 1,
                DARK_TEXT_MUTED,
                false);

        // 中间的数值显示框
        String lengthValue = String.valueOf(displayBuildLength);
        int valueBoxX = CONTROL_BUTTON_ROW_X + LENGTH_BUTTON_W + CONTROL_BUTTON_GAP;
        int valueX = valueBoxX + (LENGTH_VALUE_W - font.width(lengthValue)) / 2;
        int valueY = CONTROL_BUTTON_ROW0_Y + (CONTROL_BUTTON_H - font.lineHeight) / 2;
        guiGraphics.drawString(font, Component.literal(lengthValue), valueX, valueY, DARK_TEXT_VALUE, false);

        // 所需方块标题
        guiGraphics.drawString(
                font, Component.literal("所需方块"), MATERIAL_X + 10, MATERIAL_Y + 8, DARK_TEXT_PRIMARY, false);
        if (materials.size() > visibleMaterialSlots()) {
            int start = materialScrollOffset + 1;
            int end = Math.min(materialScrollOffset + visibleMaterialSlots(), materials.size());
            String pageText = start + "-" + end + "/" + materials.size();
            guiGraphics.drawString(
                    font,
                    Component.literal(pageText),
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

    // 内嵌框绘制函数，模仿 Minecraft 的 GUI 样式，使用多层矩形来营造凹陷的效果。

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

    // Tooltip
    private void renderMaterialTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int index = materialIndexAt(mouseX, mouseY);
        if (index < 0 || index >= materials.size()) {
            return;
        }
        NEStructureTerminalUiState.BuildMaterialEntry entry = materials.get(index);
        int missing = Math.max(0, entry.required() - entry.available());
        g.renderTooltip(
                font,
                List.of(
                        entry.item().getHoverName().getVisualOrderText(),
                        Component.literal("需要: " + entry.required()).getVisualOrderText(),
                        Component.literal("拥有: " + entry.available()).getVisualOrderText(),
                        Component.literal("缺少: " + missing).getVisualOrderText()),
                mouseX,
                mouseY);
    }

    // 鼠标滚轮滚动时，如果鼠标在材料区内且材料数量超过可见格数，则调整滚动偏移。
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

    // 根据材料的可用数量和所需数量返回不同的颜色：绿色表示充足，黄色表示部分缺失，红色表示完全缺失。
    private int materialCountColor(NEStructureTerminalUiState.BuildMaterialEntry entry) {
        if (entry.available() >= entry.required()) {
            return DARK_TEXT_SUCCESS;
        }
        if (entry.available() > 0) {
            return 0xFFFFC857;
        }
        return 0xFFFF6A75;
    }

    // 根据鼠标位置计算对应的材料索引。如果鼠标不在任何一个可见的材料格子上，则返回 -1。
    private int materialIndexAt(double mouseX, double mouseY) {
        int slot = materialVisibleSlotAt(mouseX, mouseY);
        return slot < 0 ? -1 : materialScrollOffset + slot;
    }

    // 计算鼠标位置对应的可见材料格子索引。遍历所有可见的材料格子，检查鼠标是否在其中，如果是则返回该格子的索引，否则返回 -1。
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
        return mouseX >= x && mouseX < x + MATERIAL_W && mouseY >= y && mouseY < y + MATERIAL_ROWS * MATERIAL_SLOT_SIZE;
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

    private void drawInsetButton(
            GuiGraphics g, int x, int y, int w, int h, boolean hover, boolean pressed, boolean selected) {
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

        private NEToggleTextButton(
                int x, int y, int w, int h, Component message, BooleanSupplier selectedSupplier, OnPress onPress) {
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
}
