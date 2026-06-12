package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibGuiRenderState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStateCodecs;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEStructureTerminalConfigState;
import cn.dancingsnow.neoecoae.items.StructureTerminalItem;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalHostType;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalMode;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.preview.MultiblockPatternPreviewService;
import cn.dancingsnow.neoecoae.multiblock.preview.MultiblockPatternSnapshot;
import cn.dancingsnow.neoecoae.multiblock.preview.PatternBlockEntry;
import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
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
    public static final int WIDTH = 390;
    public static final int HEIGHT = 252;

    private static final int TAB_Y = 5;
    private static final int TAB_H = 16;
    private static final int PATTERN_TAB_X = 307;
    private static final int PATTERN_TAB_W = 76;

    private static final int CONTROL_Y = 25;
    private static final int CONTROL_H = 18;
    private static final int HOST_X = 7;
    private static final int HOST_W = 42;
    private static final int HOST_GAP = 3;
    private static final int TIER_X = 148;
    private static final int TIER_W = 25;
    private static final int TIER_GAP = 3;
    private static final int LENGTH_X = 231;
    private static final int LENGTH_BUTTON_W = 18;
    private static final int LENGTH_VALUE_W = 43;
    private static final int MIRROR_X = 318;
    private static final int MIRROR_W = 65;

    private static final int PATTERN_PANEL_X = 7;
    private static final int PATTERN_PANEL_Y = 50;
    private static final int PATTERN_PANEL_W = 246;
    private static final int PATTERN_PANEL_H = 166;
    private static final int SCENE_X = PATTERN_PANEL_X + 5;
    private static final int SCENE_Y = PATTERN_PANEL_Y + 23;
    private static final int SCENE_W = PATTERN_PANEL_W - 10;
    private static final int SCENE_H = PATTERN_PANEL_H - 28;
    private static final int LAYER_PREV_X = PATTERN_PANEL_X + PATTERN_PANEL_W - 72;
    private static final int LAYER_NEXT_X = PATTERN_PANEL_X + PATTERN_PANEL_W - 20;
    private static final int LAYER_BUTTON_W = 18;
    private static final int LAYER_LABEL_X = LAYER_PREV_X + LAYER_BUTTON_W;
    private static final int LAYER_LABEL_W = LAYER_NEXT_X - LAYER_LABEL_X;

    private static final int INFO_PANEL_X = 260;
    private static final int INFO_PANEL_Y = PATTERN_PANEL_Y;
    private static final int INFO_PANEL_W = 123;
    private static final int INFO_PANEL_H = PATTERN_PANEL_H;
    private static final int PATTERN_MATERIAL_Y = INFO_PANEL_Y + 61;
    private static final int PATTERN_MATERIAL_COLS = 6;
    private static final int PATTERN_MATERIAL_ROWS = 5;

    private static final int SLOT_SIZE = 18;
    private static final int FOOTER_Y = 224;
    private static final int FOOTER_BUTTON_W = 42;
    private static final int FOOTER_MIRROR_BUTTON_W = 58;
    private static final int FOOTER_BUTTON_GAP = 4;
    private static final int FOOTER_BUTTON_Y = FOOTER_Y - 1;
    private static final int FOOTER_HINT_X =
            7 + FOOTER_BUTTON_W * 2 + FOOTER_MIRROR_BUTTON_W + FOOTER_BUTTON_GAP * 2 + 7;
    private static final int FORMED_PREVIEW_W = 65;
    private static final int FORMED_PREVIEW_X = PATTERN_PANEL_X + 7;
    private static final int FORMED_PREVIEW_Y = PATTERN_PANEL_Y + PATTERN_PANEL_H - CONTROL_H - 7;

    private final HeldItemUIFactory.HeldItemHolder holder;
    private final List<RenderedButton> renderedButtons = new ArrayList<>();
    private final List<Widget> patternWidgets = new ArrayList<>();

    private NEMultiblockPatternViewerWidget patternViewer;
    private int patternMaterialScroll;

    public NEStructureTerminalWidget(HeldItemUIFactory.HeldItemHolder holder) {
        super(
                Component.translatable("item.neoecoae.structure_terminal"),
                WIDTH,
                HEIGHT,
                NEStructureTerminalConfigState.empty(),
                () -> NEStructureTerminalConfigState.fromStack(holder.getPlayer(), holder.getHeld()),
                NELDLibStateCodecs::writeStructureTerminal,
                NELDLibStateCodecs::readStructureTerminal,
                10);
        this.holder = holder;
    }

    @Override
    protected void initLdWidgets() {
        renderedButtons.clear();
        patternWidgets.clear();

        addLocalButton(
                PATTERN_TAB_X,
                TAB_Y,
                PATTERN_TAB_W,
                TAB_H,
                () -> Component.translatable("gui.neoecoae.multiblock.pattern"),
                () -> true,
                () -> {},
                () -> true);
        addHostButton(StructureTerminalHostType.CRAFTING, 0, Action.SELECT_CRAFTING);
        addHostButton(StructureTerminalHostType.STORAGE, 1, Action.SELECT_STORAGE);
        addHostButton(StructureTerminalHostType.COMPUTATION, 2, Action.SELECT_COMPUTATION);
        addTierButton(1, 0, Action.SELECT_TIER_1);
        addTierButton(2, 1, Action.SELECT_TIER_2);
        addTierButton(3, 2, Action.SELECT_TIER_3);
        addServerButton(
                LENGTH_X,
                CONTROL_Y,
                LENGTH_BUTTON_W,
                CONTROL_H,
                () -> Component.literal("-"),
                Action.DECREASE,
                () -> false,
                () -> true,
                null);
        addServerButton(
                LENGTH_X + LENGTH_BUTTON_W + LENGTH_VALUE_W,
                CONTROL_Y,
                LENGTH_BUTTON_W,
                CONTROL_H,
                () -> Component.literal("+"),
                Action.INCREASE,
                () -> false,
                () -> true,
                null);
        addServerButton(
                MIRROR_X,
                CONTROL_Y,
                MIRROR_W,
                CONTROL_H,
                () -> Component.translatable("gui.neoecoae.structure_terminal.preview_mirrored"),
                Action.TOGGLE_PREVIEW_MIRRORED,
                () -> currentState().previewMirrored(),
                () -> true,
                null);

        patternViewer = new NEMultiblockPatternViewerWidget(
                SCENE_X,
                SCENE_Y,
                SCENE_W,
                SCENE_H,
                this::currentDefinition,
                () -> currentState().length(),
                () -> currentState().previewMirrored(),
                () -> currentState().previewFormed(),
                () -> currentState().previewLayer());
        addWidget(patternViewer);
        patternWidgets.add(patternViewer);
        addPatternLayerButton(LAYER_PREV_X, Component.literal("<"), Action.PREVIOUS_LAYER);
        addPatternLayerButton(LAYER_NEXT_X, Component.literal(">"), Action.NEXT_LAYER);
        addServerButton(
                FORMED_PREVIEW_X,
                FORMED_PREVIEW_Y,
                FORMED_PREVIEW_W,
                CONTROL_H,
                () -> Component.translatable("gui.neoecoae.structure_terminal.preview_formed"),
                Action.TOGGLE_PREVIEW_FORMED,
                () -> currentState().previewFormed(),
                () -> true,
                patternWidgets);
        addFooterActionButton(
                0,
                FOOTER_BUTTON_W,
                Component.translatable("gui.neoecoae.structure_terminal.mode.build"),
                Action.BUILD_LINKED);
        addFooterActionButton(
                1,
                FOOTER_MIRROR_BUTTON_W,
                Component.translatable("gui.neoecoae.structure_terminal.mode.mirrored_build"),
                Action.BUILD_MIRRORED_LINKED);
        addFooterActionButton(
                2,
                FOOTER_BUTTON_W,
                Component.translatable("gui.neoecoae.structure_terminal.mode.dismantle"),
                Action.DISMANTLE_LINKED);
        refreshWidgetVisibility();
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (id == 2) {
            applyAction(buffer.readEnum(Action.class), buffer);
            syncStateNow();
            return;
        }
        super.handleClientAction(id, buffer);
    }

    @Override
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (isInPatternMaterialGrid(mouseX, mouseY)) {
            int size = patternMaterials().size();
            int current = clampScroll(currentState().previewMaterialScroll(), size, patternVisibleSlots());
            patternMaterialScroll = clampScroll(
                    current + (wheelDelta < 0 ? PATTERN_MATERIAL_COLS : -PATTERN_MATERIAL_COLS),
                    size,
                    patternVisibleSlots());
            if (patternMaterialScroll != current) {
                writeClientAction(2, buf -> {
                    buf.writeEnum(Action.SET_PATTERN_MATERIAL_SCROLL);
                    buf.writeVarInt(patternMaterialScroll);
                });
            }
            return size > patternVisibleSlots();
        }
        return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
    }

    @Override
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        drawInsetValue(graphics, LENGTH_X + LENGTH_BUTTON_W, CONTROL_Y, LENGTH_VALUE_W, CONTROL_H);
        NELDLibStyle.drawDarkInsetRect(
                graphics, absX(PATTERN_PANEL_X), absY(PATTERN_PANEL_Y), PATTERN_PANEL_W, PATTERN_PANEL_H);
        NELDLibStyle.drawDarkInsetRect(
                graphics, absX(INFO_PANEL_X), absY(INFO_PANEL_Y), INFO_PANEL_W, INFO_PANEL_H);
        drawPatternMaterialSlots(graphics);
    }

    @Override
    protected void drawMachineForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        drawLocalString(graphics, title, 8, 8, TEXT_PRIMARY);
        drawControlValues(graphics);
        drawRenderedButtonLabels(graphics);
        drawPatternPage(graphics);
    }

    @Override
    protected void drawMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        renderPatternMaterialTooltip(graphics, mouseX, mouseY);
    }

    private void drawPatternPage(GuiGraphics graphics) {
        MultiblockPatternSnapshot snapshot = patternViewer.snapshot();
        drawLocalString(
                graphics,
                snapshot == null
                        ? Component.translatable("gui.neoecoae.multiblock.pattern")
                        : snapshot.definition().getName(),
                PATTERN_PANEL_X + 8,
                PATTERN_PANEL_Y + 7,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        drawCenteredLocalString(
                graphics,
                patternViewer.selectedLayer() < 0
                        ? Component.translatable("gui.neoecoae.multiblock.layer_all")
                        : Component.translatable("gui.neoecoae.multiblock.layer_value", patternViewer.selectedLayer()),
                LAYER_LABEL_X,
                PATTERN_PANEL_Y + 7,
                LAYER_LABEL_W,
                NELDLibStyle.DARK_TEXT_VALUE);

        drawLocalString(
                graphics,
                Component.translatable("gui.neoecoae.multiblock.pattern"),
                INFO_PANEL_X + 7,
                INFO_PANEL_Y + 7,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        if (snapshot != null) {
            drawLocalString(
                    graphics,
                    Component.translatable(
                            "gui.neoecoae.multiblock.size", snapshot.sizeX(), snapshot.sizeY(), snapshot.sizeZ()),
                    INFO_PANEL_X + 7,
                    INFO_PANEL_Y + 20,
                    NELDLibStyle.DARK_TEXT_VALUE);
            PatternBlockEntry controller = controllerEntry(snapshot);
            if (controller != null) {
                drawFitted(
                        graphics,
                        Component.translatable(
                                "gui.neoecoae.multiblock.controller",
                                controller.relativePos().getX(),
                                controller.relativePos().getY(),
                                controller.relativePos().getZ()),
                        INFO_PANEL_X + 7,
                        INFO_PANEL_Y + 33,
                        INFO_PANEL_W - 14,
                        NELDLibStyle.DARK_TEXT_MUTED);
            }
        }
        drawLocalString(
                graphics,
                Component.translatable("gui.neoecoae.multiblock.material_summary"),
                INFO_PANEL_X + 7,
                INFO_PANEL_Y + 48,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        drawPatternMaterialItems(graphics);
        drawPageText(
                graphics,
                patternMaterialScroll,
                patternMaterials().size(),
                patternVisibleSlots(),
                INFO_PANEL_X + INFO_PANEL_W - 7,
                INFO_PANEL_Y + 48);
        drawCenteredFitted(
                graphics,
                Component.translatable("gui.neoecoae.structure_terminal.hint_shift_build"),
                FOOTER_HINT_X,
                FOOTER_Y,
                WIDTH - FOOTER_HINT_X - 7,
                TEXT_MUTED);
    }

    private void drawControlValues(GuiGraphics graphics) {
        NEStructureTerminalConfigState state = currentState();
        drawCenteredLocalString(
                graphics,
                Component.literal(Integer.toString(state.length())),
                LENGTH_X + LENGTH_BUTTON_W,
                CONTROL_Y + 5,
                LENGTH_VALUE_W,
                NELDLibStyle.DARK_TEXT_VALUE);
    }

    private void addHostButton(StructureTerminalHostType hostType, int index, Action action) {
        addServerButton(
                HOST_X + index * (HOST_W + HOST_GAP),
                CONTROL_Y,
                HOST_W,
                CONTROL_H,
                () -> Component.translatable(hostTypeKey(hostType)),
                action,
                () -> currentState().hostType() == hostType,
                () -> true,
                null);
    }

    private void addTierButton(int tier, int index, Action action) {
        addServerButton(
                TIER_X + index * (TIER_W + TIER_GAP),
                CONTROL_Y,
                TIER_W,
                CONTROL_H,
                () -> Component.literal(tierLabel(currentState().hostType(), tier)),
                action,
                () -> currentState().tier() == tier,
                () -> true,
                null);
    }

    private void addPatternLayerButton(int x, Component label, Action action) {
        addServerButton(
                x,
                PATTERN_PANEL_Y + 3,
                LAYER_BUTTON_W,
                CONTROL_H,
                () -> label,
                action,
                () -> false,
                () -> true,
                patternWidgets);
    }

    private void addFooterActionButton(int index, int width, Component label, Action action) {
        int x = 7;
        for (int i = 0; i < index; i++) {
            x += (i == 1 ? FOOTER_MIRROR_BUTTON_W : FOOTER_BUTTON_W) + FOOTER_BUTTON_GAP;
        }
        addServerButton(
                x,
                FOOTER_BUTTON_Y,
                width,
                CONTROL_H,
                () -> label,
                action,
                () -> switch (action) {
                    case BUILD_LINKED -> currentState().operationModePending()
                            && currentState().operationMode() == StructureTerminalMode.BUILD;
                    case BUILD_MIRRORED_LINKED -> currentState().operationModePending()
                            && currentState().operationMode() == StructureTerminalMode.MIRRORED_BUILD;
                    case DISMANTLE_LINKED -> currentState().operationModePending()
                            && currentState().operationMode() == StructureTerminalMode.DISMANTLE;
                    default -> false;
                },
                () -> true,
                null);
    }

    private void addLocalButton(
            int x,
            int y,
            int w,
            int h,
            Supplier<Component> label,
            BooleanSupplier selected,
            Runnable action,
            BooleanSupplier visible) {
        addLocalButton(x, y, w, h, label, selected, action, visible, null);
    }

    private void addLocalButton(
            int x,
            int y,
            int w,
            int h,
            Supplier<Component> label,
            BooleanSupplier selected,
            Runnable action,
            BooleanSupplier visible,
            List<Widget> group) {
        ButtonWidget button =
                (ButtonWidget) new ButtonWidget(x, y, w, h, NELDLibStyle.darkInsetButton(selected), click -> {
                    if (click.isRemote) {
                        action.run();
                        refreshWidgetVisibility();
                    }
                });
        addWidget(button);
        if (group != null) {
            group.add(button);
        }
        renderedButtons.add(new RenderedButton(x, y, w, h, button, label, selected, visible));
    }

    private void addServerButton(
            int x,
            int y,
            int w,
            int h,
            Supplier<Component> label,
            Action action,
            BooleanSupplier selected,
            BooleanSupplier visible,
            List<Widget> group) {
        ButtonWidget button =
                (ButtonWidget) new ButtonWidget(x, y, w, h, NELDLibStyle.darkInsetButton(selected), click -> {
                    if (click.isRemote) {
                        writeClientAction(2, buf -> buf.writeEnum(action));
                    }
                });
        addWidget(button);
        if (group != null) {
            group.add(button);
        }
        renderedButtons.add(new RenderedButton(x, y, w, h, button, label, selected, visible));
    }

    private void refreshWidgetVisibility() {
        for (Widget widget : patternWidgets) {
            widget.setVisible(true);
            widget.setActive(true);
        }
        for (RenderedButton renderedButton : renderedButtons) {
            boolean visible = renderedButton.visible().getAsBoolean();
            renderedButton.button().setVisible(visible);
            renderedButton.button().setActive(visible);
        }
    }

    private void drawRenderedButtonLabels(GuiGraphics graphics) {
        for (RenderedButton button : renderedButtons) {
            if (!button.visible().getAsBoolean()) {
                continue;
            }
            int color =
                    button.selected().getAsBoolean() ? NELDLibStyle.DARK_TEXT_SUCCESS : NELDLibStyle.DARK_TEXT_MUTED;
            drawCenteredFitted(
                    graphics,
                    button.label().get(),
                    button.x(),
                    button.y() + (button.h() - font().lineHeight) / 2,
                    button.w(),
                    color);
        }
    }

    private void applyAction(Action action, FriendlyByteBuf buffer) {
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
            case INCREASE -> StructureTerminalItem.setBuildLength(stack, current + 1);
            case DECREASE -> StructureTerminalItem.setBuildLength(stack, current - 1);
            case TOGGLE_PREVIEW_MIRRORED ->
                StructureTerminalItem.setPreviewMirrored(stack, !StructureTerminalItem.isPreviewMirrored(stack));
            case TOGGLE_PREVIEW_FORMED ->
                StructureTerminalItem.setPreviewFormed(stack, !StructureTerminalItem.isPreviewFormed(stack));
            case PREVIOUS_LAYER -> StructureTerminalItem.setPreviewLayer(stack, previousLayer(stack));
            case NEXT_LAYER -> StructureTerminalItem.setPreviewLayer(stack, nextLayer(stack));
            case SET_PATTERN_MATERIAL_SCROLL -> StructureTerminalItem.setPreviewMaterialScroll(stack, buffer.readVarInt());
            case BUILD_LINKED -> StructureTerminalItem.setOperationMode(stack, StructureTerminalMode.BUILD);
            case BUILD_MIRRORED_LINKED -> StructureTerminalItem.setOperationMode(stack, StructureTerminalMode.MIRRORED_BUILD);
            case DISMANTLE_LINKED -> StructureTerminalItem.setOperationMode(stack, StructureTerminalMode.DISMANTLE);
        }
        holder.markAsDirty();
    }

    private MultiBlockDefinition currentDefinition() {
        NEStructureTerminalConfigState state = currentState();
        return state == null ? null : state.hostType().definitionForTier(state.tier());
    }

    private int previousLayer(ItemStack stack) {
        MultiblockPatternSnapshot snapshot = snapshotForStack(stack);
        int selectedLayer = StructureTerminalItem.getPreviewLayer(stack);
        if (snapshot == null || snapshot.layers().isEmpty()) {
            return -1;
        }
        if (selectedLayer < 0 || snapshot.blocksForLayer(selectedLayer).isEmpty()) {
            return snapshot.maxLayerY();
        }
        int previous = -1;
        for (var layer : snapshot.layers()) {
            if (layer.y() >= selectedLayer) {
                break;
            }
            previous = layer.y();
        }
        return previous;
    }

    private int nextLayer(ItemStack stack) {
        MultiblockPatternSnapshot snapshot = snapshotForStack(stack);
        int selectedLayer = StructureTerminalItem.getPreviewLayer(stack);
        if (snapshot == null || snapshot.layers().isEmpty()) {
            return -1;
        }
        if (selectedLayer < 0 || snapshot.blocksForLayer(selectedLayer).isEmpty()) {
            return snapshot.minLayerY();
        }
        for (var layer : snapshot.layers()) {
            if (layer.y() > selectedLayer) {
                return layer.y();
            }
        }
        return -1;
    }

    private @org.jetbrains.annotations.Nullable MultiblockPatternSnapshot snapshotForStack(ItemStack stack) {
        StructureTerminalHostType hostType = StructureTerminalItem.getHostType(stack);
        MultiBlockDefinition definition = hostType.definitionForTier(StructureTerminalItem.getHostTier(stack));
        if (definition == null) {
            return null;
        }
        return MultiblockPatternPreviewService.create(
                definition,
                StructureTerminalItem.getBuildLength(stack, StructureTerminalItem.getMaxBuildLength(stack)),
                StructureTerminalItem.isPreviewMirrored(stack));
    }

    private List<ItemStack> patternMaterials() {
        MultiblockPatternSnapshot snapshot = patternViewer == null ? null : patternViewer.snapshot();
        return snapshot == null ? List.of() : snapshot.materialSummary();
    }

    private void drawPatternMaterialSlots(GuiGraphics graphics) {
        for (int i = 0; i < patternVisibleSlots(); i++) {
            NELDLibStyle.drawDarkSlot(graphics, absX(patternSlotX(i)), absY(patternSlotY(i)), SLOT_SIZE);
        }
    }

    private void drawPatternMaterialItems(GuiGraphics graphics) {
        List<ItemStack> materials = patternMaterials();
        patternMaterialScroll = clampScroll(currentState().previewMaterialScroll(), materials.size(), patternVisibleSlots());
        int count = Math.min(patternVisibleSlots(), Math.max(0, materials.size() - patternMaterialScroll));
        NELDLibGuiRenderState.beginVanillaGuiItemBatch(graphics);
        for (int i = 0; i < count; i++) {
            ItemStack stack = materials.get(patternMaterialScroll + i);
            renderMaterialItem(graphics, stack, patternSlotX(i), patternSlotY(i), stack.getCount());
        }
        NELDLibGuiRenderState.endVanillaGuiItemBatch(graphics);
    }

    private void renderMaterialItem(GuiGraphics graphics, ItemStack source, int x, int y, int count) {
        ItemStack display = source.copy();
        if (display.isEmpty()) {
            return;
        }
        display.setCount(1);
        NELDLibGuiRenderState.renderVanillaSlotItem(
                graphics, font(), display, absX(x + 1), absY(y + 1), "x" + NELDLibText.compactCount(count));
    }

    private void renderPatternMaterialTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int visibleIndex = slotAt(mouseX, mouseY);
        List<ItemStack> materials = patternMaterials();
        int index = visibleIndex < 0 ? -1 : patternMaterialScroll + visibleIndex;
        if (index < 0 || index >= materials.size()) {
            return;
        }
        ItemStack stack = materials.get(index);
        graphics.renderTooltip(
                font(),
                List.of(
                        stack.getHoverName(),
                        Component.translatable(
                                "gui.neoecoae.structure_terminal.required", NELDLibText.number(stack.getCount()))),
                Optional.empty(),
                mouseX,
                mouseY);
    }

    private int slotAt(double mouseX, double mouseY) {
        int slots = patternVisibleSlots();
        for (int i = 0; i < slots; i++) {
            int x = absX(patternSlotX(i));
            int y = absY(patternSlotY(i));
            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                return i;
            }
        }
        return -1;
    }

    private int patternSlotX(int index) {
        return INFO_PANEL_X + 7 + index % PATTERN_MATERIAL_COLS * SLOT_SIZE;
    }

    private int patternSlotY(int index) {
        return PATTERN_MATERIAL_Y + index / PATTERN_MATERIAL_COLS * SLOT_SIZE;
    }

    private boolean isInPatternMaterialGrid(double mouseX, double mouseY) {
        return mouseX >= absX(INFO_PANEL_X)
                && mouseX < absX(INFO_PANEL_X + INFO_PANEL_W)
                && mouseY >= absY(PATTERN_MATERIAL_Y)
                && mouseY < absY(PATTERN_MATERIAL_Y + PATTERN_MATERIAL_ROWS * SLOT_SIZE);
    }

    private int patternVisibleSlots() {
        return PATTERN_MATERIAL_COLS * PATTERN_MATERIAL_ROWS;
    }

    private static int clampScroll(int scroll, int size, int visibleSlots) {
        return Mth.clamp(scroll, 0, Math.max(0, size - visibleSlots));
    }

    private void drawPageText(GuiGraphics graphics, int scroll, int size, int visibleSlots, int rightX, int y) {
        if (size <= visibleSlots) {
            return;
        }
        String text = (scroll + 1) + "-" + Math.min(size, scroll + visibleSlots) + "/" + size;
        drawRightLocalString(graphics, Component.literal(text), rightX, y, NELDLibStyle.DARK_TEXT_MUTED);
    }

    private void drawInsetValue(GuiGraphics graphics, int x, int y, int w, int h) {
        NELDLibStyle.drawTinyInsetRect(graphics, absX(x), absY(y), w, h, 0xFF201E27);
    }

    private void drawFitted(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        String value = text.getString();
        if (font().width(value) > width) {
            value = font().plainSubstrByWidth(value, Math.max(1, width - font().width("..."))) + "...";
        }
        drawLocalString(graphics, Component.literal(value), x, y, color);
    }

    private void drawCenteredFitted(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        String value = text.getString();
        if (font().width(value) > width) {
            value = font().plainSubstrByWidth(value, Math.max(1, width - font().width("..."))) + "...";
        }
        drawCenteredLocalString(graphics, Component.literal(value), x, y, width, color);
    }

    private static PatternBlockEntry controllerEntry(MultiblockPatternSnapshot snapshot) {
        for (PatternBlockEntry entry : snapshot.blocks()) {
            if (entry.controller()) {
                return entry;
            }
        }
        return null;
    }

    private static String hostTypeKey(StructureTerminalHostType hostType) {
        return switch (hostType) {
            case CRAFTING -> "gui.neoecoae.structure_terminal.target.crafting";
            case STORAGE -> "gui.neoecoae.structure_terminal.target.storage";
            case COMPUTATION -> "gui.neoecoae.structure_terminal.target.computation";
        };
    }

    private static String tierLabel(StructureTerminalHostType hostType, int tier) {
        int level =
                switch (StructureTerminalHostType.clampTier(tier)) {
                    case 2 -> 6;
                    case 3 -> 9;
                    default -> 4;
                };
        String prefix =
                switch (hostType) {
                    case STORAGE -> "L";
                    case COMPUTATION -> "C";
                    case CRAFTING -> "F";
                };
        return prefix + level;
    }

    private enum Action {
        INCREASE,
        DECREASE,
        SELECT_CRAFTING,
        SELECT_STORAGE,
        SELECT_COMPUTATION,
        SELECT_TIER_1,
        SELECT_TIER_2,
        SELECT_TIER_3,
        TOGGLE_PREVIEW_MIRRORED,
        TOGGLE_PREVIEW_FORMED,
        PREVIOUS_LAYER,
        NEXT_LAYER,
        SET_PATTERN_MATERIAL_SCROLL,
        BUILD_LINKED,
        BUILD_MIRRORED_LINKED,
        DISMANTLE_LINKED
    }

    private record RenderedButton(
            int x,
            int y,
            int w,
            int h,
            ButtonWidget button,
            Supplier<Component> label,
            BooleanSupplier selected,
            BooleanSupplier visible) {}
}
