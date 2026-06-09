package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStateCodecs;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEStructureTerminalConfigState;
import cn.dancingsnow.neoecoae.items.StructureTerminalItem;
import cn.dancingsnow.neoecoae.multiblock.NEStructureTerminalUiState;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalHostType;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalMode;
import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectAndBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public class NEStructureTerminalWidget extends NELDLibSyncedStateWidget<NEStructureTerminalConfigState> {
    public static final int WIDTH = 358;
    public static final int HEIGHT = 140;

    private static final int PANEL_X = 7;
    private static final int PANEL_Y = 24;
    private static final int PANEL_GAP = 7;
    private static final int PANEL_H = HEIGHT - PANEL_Y - 7;
    private static final int CONTROL_X = PANEL_X;
    private static final int CONTROL_Y = PANEL_Y;
    private static final int CONTROL_W = 122;
    private static final int CONTROL_H = PANEL_H;
    private static final int MATERIAL_X = CONTROL_X + CONTROL_W + PANEL_GAP;
    private static final int MATERIAL_Y = PANEL_Y;
    private static final int MATERIAL_W = WIDTH - MATERIAL_X - PANEL_X;
    private static final int MATERIAL_H = PANEL_H;

    private static final int BUTTON_H = 18;
    private static final int LENGTH_BUTTON_W = 22;
    private static final int CONTROL_ROW_W = 107;
    private static final int CONTROL_ROW_X = CONTROL_X + (CONTROL_W - CONTROL_ROW_W) / 2;
    private static final int LENGTH_VALUE_W = CONTROL_ROW_W - LENGTH_BUTTON_W * 2 - 14;
    private static final int LENGTH_ROW_Y = CONTROL_Y + 35;
    private static final int RESET_ROW_Y = LENGTH_ROW_Y + BUTTON_H + 7;
    private static final int MODE_ROW_Y = RESET_ROW_Y + BUTTON_H + 7;
    private static final int MODE_BUTTON_W = 31;
    private static final int MODE_GAP = 7;

    private static final int TARGET_BUTTON_W = 52;
    private static final int TARGET_BUTTON_H = 18;
    private static final int TARGET_BUTTON_GAP = 4;
    private static final int TARGET_BUTTON_Y = MATERIAL_Y + 20;

    private static final int MATERIAL_COLS = 10;
    private static final int MATERIAL_ROWS = 2;
    private static final int MATERIAL_SLOT_SIZE = 18;
    private static final int MATERIAL_GRID_Y = MATERIAL_Y + 54;

    private static final IGuiTexture BUTTON_TEXTURE =
            new GuiTextureGroup(new ColorRectAndBorderTexture(0xFFECEEF3, 0xFF8A96A8, 1.0F));
    private static final IGuiTexture BUTTON_HOVER_TEXTURE =
            new GuiTextureGroup(new ColorRectAndBorderTexture(0x00000000, 0xFF4F7FB6, 1.0F));

    private final HeldItemUIFactory.HeldItemHolder holder;
    private int materialScrollOffset;

    public NEStructureTerminalWidget(HeldItemUIFactory.HeldItemHolder holder) {
        super(
                Component.translatable("item.neoecoae.structure_terminal"),
                WIDTH,
                HEIGHT,
                NEStructureTerminalConfigState.empty(),
                () -> NEStructureTerminalConfigState.fromStack(holder.getPlayer(), holder.getHeld()),
                NELDLibStateCodecs::writeStructureTerminal,
                NELDLibStateCodecs::readStructureTerminal,
                20);
        this.holder = holder;
    }

    @Override
    protected void initLdWidgets() {
        addActionButton(CONTROL_ROW_X, LENGTH_ROW_Y, LENGTH_BUTTON_W, Component.literal("-"), Action.DECREASE);
        addActionButton(
                CONTROL_ROW_X + LENGTH_BUTTON_W + 7 + LENGTH_VALUE_W + 7,
                LENGTH_ROW_Y,
                LENGTH_BUTTON_W,
                Component.literal("+"),
                Action.INCREASE);
        addActionButton(
                CONTROL_ROW_X,
                RESET_ROW_Y,
                CONTROL_ROW_W,
                Component.translatable("gui.neoecoae.structure_terminal.reset"),
                Action.RESET);

        addModeButton(
                StructureTerminalMode.BUILD,
                0,
                Component.translatable("gui.neoecoae.structure_terminal.mode.build"),
                Action.SELECT_BUILD_MODE);
        addModeButton(
                StructureTerminalMode.MIRRORED_BUILD,
                1,
                Component.translatable("gui.neoecoae.structure_terminal.mode.mirrored_build"),
                Action.SELECT_MIRRORED_BUILD_MODE);
        addModeButton(
                StructureTerminalMode.DISMANTLE,
                2,
                Component.translatable("gui.neoecoae.structure_terminal.mode.dismantle"),
                Action.SELECT_DISMANTLE_MODE);

        addTargetButton(
                StructureTerminalHostType.CRAFTING,
                0,
                Component.translatable("gui.neoecoae.structure_terminal.target.crafting"),
                Action.SELECT_CRAFTING);
        addTargetButton(
                StructureTerminalHostType.STORAGE,
                1,
                Component.translatable("gui.neoecoae.structure_terminal.target.storage"),
                Action.SELECT_STORAGE);
        addTargetButton(
                StructureTerminalHostType.COMPUTATION,
                2,
                Component.translatable("gui.neoecoae.structure_terminal.target.computation"),
                Action.SELECT_COMPUTATION);
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (id == 2) {
            applyAction(buffer.readEnum(Action.class));
            syncStateNow();
            return;
        }
        super.handleClientAction(id, buffer);
    }

    @Override
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (isInMaterialGrid(mouseX, mouseY) && currentState().materials().size() > visibleMaterialSlots()) {
            int next = materialScrollOffset + (wheelDelta < 0 ? MATERIAL_COLS : -MATERIAL_COLS);
            materialScrollOffset = clampMaterialScroll(next);
            return true;
        }
        return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
    }

    @Override
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        drawPanel(graphics, CONTROL_X, CONTROL_Y, CONTROL_W, CONTROL_H);
        drawPanel(graphics, MATERIAL_X, MATERIAL_Y, MATERIAL_W, MATERIAL_H);
        drawInsetValueLocal(graphics, CONTROL_ROW_X + LENGTH_BUTTON_W + 7, LENGTH_ROW_Y, LENGTH_VALUE_W, BUTTON_H);
        drawMaterialSlotGrid(graphics);
    }

    @Override
    protected void drawMachineForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        NEStructureTerminalConfigState state = currentState();
        materialScrollOffset = clampMaterialScroll(materialScrollOffset);
        drawCenteredFitted(
                graphics,
                Component.literal("Length: " + state.length()),
                CONTROL_X + 6,
                CONTROL_Y + 7,
                CONTROL_W - 12,
                TEXT_PRIMARY);
        drawCenteredFitted(
                graphics,
                Component.literal("Min: " + state.minLength() + "  Max: " + state.maxLength()),
                CONTROL_X + 6,
                CONTROL_Y + 19,
                CONTROL_W - 12,
                TEXT_MUTED);
        drawCenteredFitted(
                graphics,
                Component.literal(Integer.toString(state.length())),
                CONTROL_ROW_X + LENGTH_BUTTON_W + 7,
                LENGTH_ROW_Y + 5,
                LENGTH_VALUE_W,
                TEXT_VALUE);
        drawLocalString(
                graphics, Component.literal("Required Materials"), MATERIAL_X + 10, MATERIAL_Y + 8, TEXT_PRIMARY);
        if (state.materials().isEmpty()) {
            drawCenteredFitted(
                    graphics,
                    Component.literal("No materials"),
                    MATERIAL_X,
                    MATERIAL_GRID_Y + 13,
                    MATERIAL_W,
                    TEXT_MUTED);
        }
        drawMaterialItems(graphics, state.materials());
        drawMaterialPageText(graphics, state.materials());
    }

    @Override
    protected void drawMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (renderControlTooltip(graphics, mouseX, mouseY)) {
            return;
        }
        renderMaterialTooltip(graphics, mouseX, mouseY);
    }

    private void addModeButton(StructureTerminalMode mode, int index, Component label, Action action) {
        addActionButton(
                CONTROL_ROW_X + index * (MODE_BUTTON_W + MODE_GAP),
                MODE_ROW_Y,
                MODE_BUTTON_W,
                label,
                action,
                () -> currentState().operationMode() == mode);
    }

    private void addTargetButton(StructureTerminalHostType target, int index, Component label, Action action) {
        int totalW = TARGET_BUTTON_W * 3 + TARGET_BUTTON_GAP * 2;
        int x = MATERIAL_X + (MATERIAL_W - totalW) / 2 + index * (TARGET_BUTTON_W + TARGET_BUTTON_GAP);
        addActionButton(
                x,
                TARGET_BUTTON_Y,
                TARGET_BUTTON_W,
                TARGET_BUTTON_H,
                label,
                action,
                () -> currentState().hostType() == target);
    }

    private void addActionButton(int x, int y, int w, Component label, Action action) {
        addActionButton(x, y, w, BUTTON_H, label, action, () -> false);
    }

    private void addActionButton(
            int x, int y, int w, Component label, Action action, BooleanSupplier selectedSupplier) {
        addActionButton(x, y, w, BUTTON_H, label, action, selectedSupplier);
    }

    private void addActionButton(
            int x, int y, int w, int h, Component label, Action action, BooleanSupplier selectedSupplier) {
        ButtonWidget button = (ButtonWidget) new ButtonWidget(x, y, w, h, BUTTON_TEXTURE, click -> {
                    if (click.isRemote) {
                        writeClientAction(2, buf -> buf.writeEnum(action));
                    }
                })
                .setHoverTexture(BUTTON_HOVER_TEXTURE);
        addWidget(button);
        addText(
                x,
                y + (h - font().lineHeight) / 2,
                w,
                font().lineHeight,
                () -> label,
                selectedSupplier.getAsBoolean() ? TEXT_SUCCESS : TEXT_VALUE,
                com.lowdragmc.lowdraglib.gui.texture.TextTexture.TextType.NORMAL);
    }

    private void applyAction(Action action) {
        ItemStack stack = holder.getHeld();
        if (stack.isEmpty() || !(stack.getItem() instanceof StructureTerminalItem)) {
            return;
        }
        int current = StructureTerminalItem.getBuildLength(stack);
        switch (action) {
            case SELECT_CRAFTING -> StructureTerminalItem.setHostType(stack, StructureTerminalHostType.CRAFTING);
            case SELECT_STORAGE -> StructureTerminalItem.setHostType(stack, StructureTerminalHostType.STORAGE);
            case SELECT_COMPUTATION -> StructureTerminalItem.setHostType(stack, StructureTerminalHostType.COMPUTATION);
            case SELECT_BUILD_MODE -> StructureTerminalItem.setOperationMode(stack, StructureTerminalMode.BUILD);
            case SELECT_MIRRORED_BUILD_MODE -> StructureTerminalItem.setOperationMode(
                    stack, StructureTerminalMode.MIRRORED_BUILD);
            case SELECT_DISMANTLE_MODE -> StructureTerminalItem.setOperationMode(
                    stack, StructureTerminalMode.DISMANTLE);
            case INCREASE -> StructureTerminalItem.setBuildLength(stack, current + 1);
            case DECREASE -> StructureTerminalItem.setBuildLength(stack, current - 1);
            case RESET -> StructureTerminalItem.setBuildLength(stack, StructureTerminalItem.DEFAULT_BUILD_LENGTH);
        }
        holder.markAsDirty();
    }

    private void drawInsetValueLocal(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(absX(x), absY(y), absX(x + w), absY(y + h), 0xFF8A96A8);
        graphics.fill(absX(x + 1), absY(y + 1), absX(x + w - 1), absY(y + h - 1), 0xFFF5F6F8);
        graphics.fill(absX(x + 2), absY(y + 2), absX(x + w - 2), absY(y + h - 2), 0xFFD9DEE7);
    }

    private void drawMaterialSlotGrid(GuiGraphics graphics) {
        for (int i = 0; i < visibleMaterialSlots(); i++) {
            drawInventorySlot(graphics, materialSlotX(i), materialSlotY(i));
        }
    }

    private void drawInventorySlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(absX(x), absY(y), absX(x + MATERIAL_SLOT_SIZE), absY(y + MATERIAL_SLOT_SIZE), 0xFF9AA0AA);
        graphics.fill(
                absX(x + 1),
                absY(y + 1),
                absX(x + MATERIAL_SLOT_SIZE - 1),
                absY(y + MATERIAL_SLOT_SIZE - 1),
                0xFFECEEF3);
        graphics.fill(
                absX(x + 2),
                absY(y + 2),
                absX(x + MATERIAL_SLOT_SIZE - 2),
                absY(y + MATERIAL_SLOT_SIZE - 2),
                0xFFD7DAE2);
    }

    private void drawMaterialItems(
            GuiGraphics graphics, List<NEStructureTerminalUiState.BuildMaterialEntry> materials) {
        int count = Math.min(visibleMaterialSlots(), Math.max(0, materials.size() - materialScrollOffset));
        for (int i = 0; i < count; i++) {
            NEStructureTerminalUiState.BuildMaterialEntry entry = materials.get(materialScrollOffset + i);
            int x = absX(materialSlotX(i));
            int y = absY(materialSlotY(i));
            graphics.renderItem(entry.item(), x + 1, y + 1);
            String text = "x" + formatCompactCount(entry.required());
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.drawString(
                    font(),
                    Component.literal(text),
                    x + 17 - font().width(text),
                    y + 10,
                    materialCountColor(entry),
                    true);
            graphics.pose().popPose();
        }
    }

    private void drawMaterialPageText(
            GuiGraphics graphics, List<NEStructureTerminalUiState.BuildMaterialEntry> materials) {
        if (materials.size() <= visibleMaterialSlots()) {
            return;
        }
        int start = materialScrollOffset + 1;
        int end = Math.min(materialScrollOffset + visibleMaterialSlots(), materials.size());
        String pageText = start + "-" + end + "/" + materials.size();
        drawLocalString(
                graphics,
                Component.literal(pageText),
                MATERIAL_X + MATERIAL_W - 10 - font().width(pageText),
                MATERIAL_Y + MATERIAL_H - 13,
                TEXT_MUTED);
    }

    private boolean renderControlTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isMouseIn(CONTROL_ROW_X, MODE_ROW_Y, MODE_BUTTON_W, BUTTON_H, mouseX, mouseY)) {
            graphics.renderComponentTooltip(
                    font(),
                    List.of(Component.translatable("gui.neoecoae.structure_terminal.mode.build.tooltip")),
                    mouseX,
                    mouseY);
            return true;
        }
        if (isMouseIn(CONTROL_ROW_X + MODE_BUTTON_W + MODE_GAP, MODE_ROW_Y, MODE_BUTTON_W, BUTTON_H, mouseX, mouseY)) {
            graphics.renderComponentTooltip(
                    font(),
                    List.of(Component.translatable("gui.neoecoae.structure_terminal.mode.mirrored_build.tooltip")),
                    mouseX,
                    mouseY);
            return true;
        }
        if (isMouseIn(
                CONTROL_ROW_X + (MODE_BUTTON_W + MODE_GAP) * 2, MODE_ROW_Y, MODE_BUTTON_W, BUTTON_H, mouseX, mouseY)) {
            graphics.renderComponentTooltip(
                    font(),
                    List.of(Component.translatable("gui.neoecoae.structure_terminal.mode.dismantle.tooltip")),
                    mouseX,
                    mouseY);
            return true;
        }

        int totalW = TARGET_BUTTON_W * 3 + TARGET_BUTTON_GAP * 2;
        int startX = MATERIAL_X + (MATERIAL_W - totalW) / 2;
        if (isMouseIn(startX, TARGET_BUTTON_Y, TARGET_BUTTON_W, TARGET_BUTTON_H, mouseX, mouseY)) {
            graphics.renderComponentTooltip(
                    font(),
                    List.of(Component.translatable("gui.neoecoae.structure_terminal.target.crafting.tooltip")),
                    mouseX,
                    mouseY);
            return true;
        }
        if (isMouseIn(
                startX + TARGET_BUTTON_W + TARGET_BUTTON_GAP,
                TARGET_BUTTON_Y,
                TARGET_BUTTON_W,
                TARGET_BUTTON_H,
                mouseX,
                mouseY)) {
            graphics.renderComponentTooltip(
                    font(),
                    List.of(Component.translatable("gui.neoecoae.structure_terminal.target.storage.tooltip")),
                    mouseX,
                    mouseY);
            return true;
        }
        if (isMouseIn(
                startX + (TARGET_BUTTON_W + TARGET_BUTTON_GAP) * 2,
                TARGET_BUTTON_Y,
                TARGET_BUTTON_W,
                TARGET_BUTTON_H,
                mouseX,
                mouseY)) {
            graphics.renderComponentTooltip(
                    font(),
                    List.of(Component.translatable("gui.neoecoae.structure_terminal.target.computation.tooltip")),
                    mouseX,
                    mouseY);
            return true;
        }
        return false;
    }

    private void renderMaterialTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int index = materialIndexAt(mouseX, mouseY);
        List<NEStructureTerminalUiState.BuildMaterialEntry> materials =
                currentState().materials();
        if (index < 0 || index >= materials.size()) {
            return;
        }

        NEStructureTerminalUiState.BuildMaterialEntry entry = materials.get(index);
        graphics.renderTooltip(
                font(),
                List.of(
                        entry.item().getHoverName(),
                        Component.literal("Required: " + entry.required()),
                        Component.literal("Available: " + entry.available()),
                        Component.literal("Missing: " + entry.missing())),
                Optional.empty(),
                mouseX,
                mouseY);
    }

    private void drawCenteredFitted(GuiGraphics graphics, Component text, int x, int y, int w, int color) {
        int textWidth = font().width(text);
        int maxWidth = Math.max(1, w - 4);
        if (textWidth <= maxWidth) {
            drawCenteredLocalString(graphics, text, x, y, w, color);
            return;
        }

        float scale = Math.max(0.55F, (float) maxWidth / (float) textWidth);
        graphics.pose().pushPose();
        graphics.pose().translate(absX(x) + w / 2.0F, absY(y), 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font(), text, -textWidth / 2, 0, color, false);
        graphics.pose().popPose();
    }

    private int materialCountColor(NEStructureTerminalUiState.BuildMaterialEntry entry) {
        if (entry.available() >= entry.required()) {
            return TEXT_SUCCESS;
        }
        if (entry.available() > 0) {
            return TEXT_WARNING;
        }
        return TEXT_ERROR;
    }

    private boolean isInMaterialGrid(double mouseX, double mouseY) {
        int x = absX(MATERIAL_X);
        int y = absY(MATERIAL_GRID_Y);
        return mouseX >= x && mouseX < x + MATERIAL_W && mouseY >= y && mouseY < y + MATERIAL_ROWS * MATERIAL_SLOT_SIZE;
    }

    private int materialIndexAt(double mouseX, double mouseY) {
        int slot = materialVisibleSlotAt(mouseX, mouseY);
        return slot < 0 ? -1 : materialScrollOffset + slot;
    }

    private int materialVisibleSlotAt(double mouseX, double mouseY) {
        for (int i = 0; i < visibleMaterialSlots(); i++) {
            int x = absX(materialSlotX(i));
            int y = absY(materialSlotY(i));
            if (mouseX >= x && mouseX < x + MATERIAL_SLOT_SIZE && mouseY >= y && mouseY < y + MATERIAL_SLOT_SIZE) {
                return i;
            }
        }
        return -1;
    }

    private int materialSlotX(int visibleIndex) {
        int gridW = MATERIAL_COLS * MATERIAL_SLOT_SIZE;
        int startX = MATERIAL_X + (MATERIAL_W - gridW) / 2;
        return startX + (visibleIndex % MATERIAL_COLS) * MATERIAL_SLOT_SIZE;
    }

    private int materialSlotY(int visibleIndex) {
        return MATERIAL_GRID_Y + (visibleIndex / MATERIAL_COLS) * MATERIAL_SLOT_SIZE;
    }

    private int visibleMaterialSlots() {
        return MATERIAL_COLS * MATERIAL_ROWS;
    }

    private int clampMaterialScroll(int value) {
        int max = Math.max(0, currentState().materials().size() - visibleMaterialSlots());
        return Mth.clamp(value, 0, max);
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

    private enum Action {
        INCREASE,
        DECREASE,
        RESET,
        SELECT_CRAFTING,
        SELECT_STORAGE,
        SELECT_COMPUTATION,
        SELECT_BUILD_MODE,
        SELECT_MIRRORED_BUILD_MODE,
        SELECT_DISMANTLE_MODE
    }
}
