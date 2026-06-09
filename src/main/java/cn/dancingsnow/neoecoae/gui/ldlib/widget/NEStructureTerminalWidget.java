package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.client.multiblock.preview.MultiblockPreviewContext;
import cn.dancingsnow.neoecoae.client.multiblock.preview.MultiblockPreviewScene;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibGuiRenderState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStateCodecs;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEStructureTerminalConfigState;
import cn.dancingsnow.neoecoae.items.StructureTerminalItem;
import cn.dancingsnow.neoecoae.multiblock.NEStructureTerminalUiState;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalHostType;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalMode;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public class NEStructureTerminalWidget extends NELDLibSyncedStateWidget<NEStructureTerminalConfigState> {
    public static final int WIDTH = 358;
    public static final int HEIGHT = 236;

    private static final int PANEL_X = 7;
    private static final int PANEL_Y = 24;
    private static final int PANEL_GAP = 7;
    private static final int PREVIEW_X = PANEL_X;
    private static final int PREVIEW_Y = PANEL_Y;
    private static final int PREVIEW_W = WIDTH - PANEL_X * 2;
    private static final int PREVIEW_H = 94;
    private static final int LOWER_Y = PREVIEW_Y + PREVIEW_H + PANEL_GAP;
    private static final int LOWER_H = HEIGHT - LOWER_Y - PANEL_X;
    private static final int CONTROL_X = PANEL_X;
    private static final int CONTROL_Y = LOWER_Y;
    private static final int CONTROL_W = 122;
    private static final int CONTROL_H = LOWER_H;
    private static final int MATERIAL_X = CONTROL_X + CONTROL_W + PANEL_GAP;
    private static final int MATERIAL_Y = LOWER_Y;
    private static final int MATERIAL_W = WIDTH - MATERIAL_X - PANEL_X;
    private static final int MATERIAL_PANEL_H = LOWER_H;

    private static final int BUTTON_H = 18;
    private static final int LENGTH_BUTTON_W = 22;
    private static final int CONTROL_ROW_W = 107;
    private static final int CONTROL_ROW_X = CONTROL_X + (CONTROL_W - CONTROL_ROW_W) / 2;
    private static final int LENGTH_VALUE_W = CONTROL_ROW_W - LENGTH_BUTTON_W * 2 - 14;
    private static final int CONTROL_BOTTOM_PAD = 7;
    private static final int MODE_ROW_Y = CONTROL_Y + CONTROL_H - CONTROL_BOTTOM_PAD - BUTTON_H;
    private static final int RESET_ROW_Y = MODE_ROW_Y - BUTTON_H - 7;
    private static final int LENGTH_ROW_Y = RESET_ROW_Y - BUTTON_H - 7;
    private static final int MODE_BUTTON_W = 31;
    private static final int MODE_GAP = 7;

    private static final int TIER_BUTTON_X = PREVIEW_X + 10;
    private static final int TIER_BUTTON_Y = PREVIEW_Y + 26;
    private static final int TIER_BUTTON_W = 27;
    private static final int TIER_BUTTON_H = 16;
    private static final int TIER_BUTTON_GAP = 4;
    private static final int FORMED_BUTTON_W = 38;
    private static final int FORMED_BUTTON_H = 16;
    private static final int FORMED_BUTTON_X = PREVIEW_X + PREVIEW_W - FORMED_BUTTON_W - 10;
    private static final int FORMED_BUTTON_Y = PREVIEW_Y + (PREVIEW_H - FORMED_BUTTON_H) / 2;

    private static final int TARGET_BUTTON_W = 52;
    private static final int TARGET_BUTTON_H = 18;
    private static final int TARGET_BUTTON_GAP = 4;
    private static final int TARGET_BUTTON_Y = MATERIAL_Y + 19;

    private static final int MATERIAL_COLS = 10;
    private static final int MATERIAL_ROWS = 2;
    private static final int MATERIAL_SLOT_SIZE = 18;
    private static final int PREVIEW_SCENE_X = PREVIEW_X + 42;
    private static final int PREVIEW_SCENE_Y = PREVIEW_Y + 21;
    private static final int PREVIEW_SCENE_W = FORMED_BUTTON_X - PREVIEW_SCENE_X - 5;
    private static final int PREVIEW_SCENE_H = PREVIEW_H - 26;
    private static final int MATERIAL_BOTTOM_PAD = 7;
    private static final int MATERIAL_GRID_Y =
            MATERIAL_Y + MATERIAL_PANEL_H - MATERIAL_BOTTOM_PAD - MATERIAL_ROWS * MATERIAL_SLOT_SIZE;
    private static final int MATERIAL_TITLE_Y = MATERIAL_GRID_Y - 13;
    private final HeldItemUIFactory.HeldItemHolder holder;
    private final List<ActionButtonLabel> actionButtonLabels = new ArrayList<>();
    private int materialScrollOffset;
    private boolean formedPreview;

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
        actionButtonLabels.clear();
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
        addTierButton(1, 0);
        addTierButton(2, 1);
        addTierButton(3, 2);
        addFormedPreviewButton();

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
        addWidget(new NELDLibMultiblockSceneWidget(
                PREVIEW_SCENE_X, PREVIEW_SCENE_Y, PREVIEW_SCENE_W, PREVIEW_SCENE_H, this::createCurrentPreviewScene));
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
        NELDLibStyle.drawDarkInsetRect(graphics, absX(PREVIEW_X), absY(PREVIEW_Y), PREVIEW_W, PREVIEW_H);
        NELDLibStyle.drawDarkInsetRect(graphics, absX(CONTROL_X), absY(CONTROL_Y), CONTROL_W, CONTROL_H);
        NELDLibStyle.drawDarkInsetRect(graphics, absX(MATERIAL_X), absY(MATERIAL_Y), MATERIAL_W, MATERIAL_PANEL_H);
        drawInsetValueLocal(graphics, CONTROL_ROW_X + LENGTH_BUTTON_W + 7, LENGTH_ROW_Y, LENGTH_VALUE_W, BUTTON_H);
        drawMaterialSlotGrid(graphics);
    }

    @Override
    protected void drawMachineForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        NEStructureTerminalConfigState state = currentState();
        materialScrollOffset = clampMaterialScroll(materialScrollOffset);
        drawCenteredFitted(
                graphics,
                Component.translatable("gui.neoecoae.structure_terminal.length", NELDLibText.number(state.length())),
                CONTROL_X + 6,
                CONTROL_Y + 6,
                CONTROL_W - 12,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        drawCenteredFitted(
                graphics,
                Component.translatable(
                        "gui.neoecoae.structure_terminal.length_range",
                        NELDLibText.number(state.minLength()),
                        NELDLibText.number(state.maxLength())),
                CONTROL_X + 6,
                CONTROL_Y + 17,
                CONTROL_W - 12,
                NELDLibStyle.DARK_TEXT_MUTED);
        drawCenteredFitted(
                graphics,
                Component.literal(NELDLibText.number(state.length())),
                CONTROL_ROW_X + LENGTH_BUTTON_W + 7,
                LENGTH_ROW_Y + 5,
                LENGTH_VALUE_W,
                NELDLibStyle.DARK_TEXT_VALUE);
        drawLocalString(
                graphics,
                Component.translatable("gui.neoecoae.crafting.module_preview"),
                PREVIEW_X + 10,
                PREVIEW_Y + 8,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        drawLocalString(
                graphics,
                Component.translatable("gui.neoecoae.structure_terminal.host_selection"),
                MATERIAL_X + 10,
                MATERIAL_Y + 8,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        drawLocalString(
                graphics,
                Component.translatable("gui.neoecoae.structure_terminal.required_materials"),
                MATERIAL_X + 10,
                MATERIAL_TITLE_Y,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        drawActionButtonLabels(graphics);
        if (state.materials().isEmpty()) {
            drawCenteredFitted(
                    graphics,
                    Component.translatable("gui.neoecoae.structure_terminal.no_materials"),
                    MATERIAL_X,
                    MATERIAL_GRID_Y + 13,
                    MATERIAL_W,
                    NELDLibStyle.DARK_TEXT_MUTED);
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

    private void addTierButton(int tier, int index) {
        addActionButton(
                TIER_BUTTON_X,
                TIER_BUTTON_Y + index * (TIER_BUTTON_H + TIER_BUTTON_GAP),
                TIER_BUTTON_W,
                TIER_BUTTON_H,
                () -> Component.literal(tierLabel(currentState().hostType(), tier)),
                tierAction(tier),
                () -> currentState().tier() == tier);
    }

    private void addFormedPreviewButton() {
        addLocalActionButton(
                FORMED_BUTTON_X,
                FORMED_BUTTON_Y,
                FORMED_BUTTON_W,
                FORMED_BUTTON_H,
                () -> Component.translatable(
                        formedPreview
                                ? "gui.neoecoae.structure_terminal.preview_formed"
                                : "gui.neoecoae.structure_terminal.preview_unformed"),
                () -> formedPreview,
                () -> formedPreview = !formedPreview);
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
        addActionButton(x, y, w, h, () -> label, action, selectedSupplier, NELDLibStyle.DARK_TEXT_MUTED);
    }

    private void addActionButton(
            int x,
            int y,
            int w,
            int h,
            Supplier<Component> labelSupplier,
            Action action,
            BooleanSupplier selectedSupplier) {
        addActionButton(x, y, w, h, labelSupplier, action, selectedSupplier, NELDLibStyle.DARK_TEXT_MUTED);
    }

    private void addActionButton(
            int x,
            int y,
            int w,
            int h,
            Supplier<Component> labelSupplier,
            Action action,
            BooleanSupplier selectedSupplier,
            int inactiveColor) {
        ButtonWidget button =
                (ButtonWidget) new ButtonWidget(x, y, w, h, NELDLibStyle.darkInsetButton(selectedSupplier), click -> {
                    if (click.isRemote) {
                        writeClientAction(2, buf -> buf.writeEnum(action));
                    }
                });
        addWidget(button);
        actionButtonLabels.add(new ActionButtonLabel(x, y, w, h, labelSupplier, selectedSupplier, inactiveColor));
    }

    private void addLocalActionButton(
            int x,
            int y,
            int w,
            int h,
            Supplier<Component> labelSupplier,
            BooleanSupplier selectedSupplier,
            Runnable action) {
        ButtonWidget button =
                (ButtonWidget) new ButtonWidget(x, y, w, h, NELDLibStyle.darkInsetButton(selectedSupplier), click -> {
                    if (click.isRemote) {
                        action.run();
                    }
                });
        addWidget(button);
        actionButtonLabels.add(
                new ActionButtonLabel(x, y, w, h, labelSupplier, selectedSupplier, NELDLibStyle.DARK_TEXT_MUTED));
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
            case SELECT_TIER_1 -> StructureTerminalItem.setHostTarget(
                    stack, StructureTerminalItem.getHostType(stack), 1);
            case SELECT_TIER_2 -> StructureTerminalItem.setHostTarget(
                    stack, StructureTerminalItem.getHostType(stack), 2);
            case SELECT_TIER_3 -> StructureTerminalItem.setHostTarget(
                    stack, StructureTerminalItem.getHostType(stack), 3);
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
        NELDLibStyle.drawTinyInsetRect(graphics, absX(x), absY(y), w, h, 0xFF201E27);
    }

    private void drawActionButtonLabels(GuiGraphics graphics) {
        for (ActionButtonLabel label : actionButtonLabels) {
            int color =
                    label.selectedSupplier().getAsBoolean() ? NELDLibStyle.DARK_TEXT_SUCCESS : label.inactiveColor();
            drawCenteredFitted(
                    graphics,
                    label.labelSupplier().get(),
                    label.x(),
                    label.y() + (label.h() - font().lineHeight) / 2,
                    label.w(),
                    color);
        }
    }

    private void drawMaterialSlotGrid(GuiGraphics graphics) {
        for (int i = 0; i < visibleMaterialSlots(); i++) {
            drawInventorySlot(graphics, materialSlotX(i), materialSlotY(i));
        }
    }

    private void drawInventorySlot(GuiGraphics graphics, int x, int y) {
        NELDLibStyle.drawDarkSlot(graphics, absX(x), absY(y), MATERIAL_SLOT_SIZE);
    }

    private void drawMaterialItems(
            GuiGraphics graphics, List<NEStructureTerminalUiState.BuildMaterialEntry> materials) {
        int count = Math.min(visibleMaterialSlots(), Math.max(0, materials.size() - materialScrollOffset));
        NELDLibGuiRenderState.beginVanillaGuiItemBatch(graphics);
        for (int i = 0; i < count; i++) {
            NEStructureTerminalUiState.BuildMaterialEntry entry = materials.get(materialScrollOffset + i);
            int x = absX(materialSlotX(i));
            int y = absY(materialSlotY(i));
            ItemStack displayStack = entry.item().copy();
            if (!displayStack.isEmpty()) {
                displayStack.setCount(1);
                drawMaterialItem(graphics, displayStack, x, y, "x" + NELDLibText.compactCount(entry.required()));
            }
        }
        NELDLibGuiRenderState.endVanillaGuiItemBatch(graphics);
    }

    private void drawMaterialItem(GuiGraphics graphics, ItemStack stack, int x, int y, String countLabel) {
        NELDLibGuiRenderState.renderVanillaSlotItem(graphics, font(), stack, x + 1, y + 1, countLabel);
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
                MATERIAL_TITLE_Y,
                NELDLibStyle.DARK_TEXT_MUTED);
    }

    private boolean renderControlTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isMouseIn(FORMED_BUTTON_X, FORMED_BUTTON_Y, FORMED_BUTTON_W, FORMED_BUTTON_H, mouseX, mouseY)) {
            graphics.renderComponentTooltip(
                    font(),
                    List.of(Component.translatable(
                            formedPreview
                                    ? "emi.neoecoae.multiblock.show_unformed"
                                    : "emi.neoecoae.multiblock.show_formed")),
                    mouseX,
                    mouseY);
            return true;
        }

        for (int i = 0; i < StructureTerminalHostType.MAX_TIER; i++) {
            int tier = i + 1;
            if (isMouseIn(
                    TIER_BUTTON_X,
                    TIER_BUTTON_Y + i * (TIER_BUTTON_H + TIER_BUTTON_GAP),
                    TIER_BUTTON_W,
                    TIER_BUTTON_H,
                    mouseX,
                    mouseY)) {
                graphics.renderComponentTooltip(
                        font(),
                        List.of(Component.literal(tierLabel(currentState().hostType(), tier))),
                        mouseX,
                        mouseY);
                return true;
            }
        }

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
        Component itemName = entry.item().isEmpty()
                ? Component.translatable("gui.neoecoae.structure_terminal.unknown_material")
                : entry.item().getHoverName();
        graphics.renderTooltip(
                font(),
                List.of(
                        itemName,
                        Component.translatable(
                                "gui.neoecoae.structure_terminal.required", NELDLibText.number(entry.required())),
                        Component.translatable(
                                "gui.neoecoae.structure_terminal.available", NELDLibText.number(entry.available())),
                        Component.translatable(
                                "gui.neoecoae.structure_terminal.missing", NELDLibText.number(entry.missing()))),
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

    private Action tierAction(int tier) {
        return switch (tier) {
            case 2 -> Action.SELECT_TIER_2;
            case 3 -> Action.SELECT_TIER_3;
            default -> Action.SELECT_TIER_1;
        };
    }

    private String tierLabel(StructureTerminalHostType hostType, int tier) {
        int level =
                switch (StructureTerminalHostType.clampTier(tier)) {
                    case 2 -> 6;
                    case 3 -> 9;
                    default -> 4;
                };
        return tierPrefix(hostType) + level;
    }

    private String tierPrefix(StructureTerminalHostType hostType) {
        return switch (hostType) {
            case STORAGE -> "L";
            case COMPUTATION -> "C";
            case CRAFTING -> "F";
        };
    }

    private MultiblockPreviewScene createCurrentPreviewScene() {
        NEStructureTerminalConfigState state = currentState();
        if (state == null) {
            return null;
        }
        MultiBlockDefinition definition = state.hostType().definitionForTier(state.tier());
        if (definition == null) {
            return null;
        }
        int length = Mth.clamp(state.length(), state.minLength(), state.maxLength());
        return MultiblockPreviewContext.createScene(definition, length, formedPreview);
    }

    private enum Action {
        INCREASE,
        DECREASE,
        RESET,
        SELECT_CRAFTING,
        SELECT_STORAGE,
        SELECT_COMPUTATION,
        SELECT_TIER_1,
        SELECT_TIER_2,
        SELECT_TIER_3,
        SELECT_BUILD_MODE,
        SELECT_MIRRORED_BUILD_MODE,
        SELECT_DISMANTLE_MODE
    }

    private record ActionButtonLabel(
            int x,
            int y,
            int w,
            int h,
            Supplier<Component> labelSupplier,
            BooleanSupplier selectedSupplier,
            int inactiveColor) {}
}
