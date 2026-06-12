package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibGuiRenderState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStateCodecs;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEStructureTerminalConfigState;
import cn.dancingsnow.neoecoae.items.StructureTerminalItem;
import cn.dancingsnow.neoecoae.multiblock.INEMultiblockBuildHost;
import cn.dancingsnow.neoecoae.multiblock.NEStructureTerminalUiState;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalHostType;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalMode;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public class NEStructureTerminalWidget extends NELDLibSyncedStateWidget<NEStructureTerminalConfigState> {
    public static final int WIDTH = 390;
    public static final int HEIGHT = 252;

    private static final int TAB_Y = 5;
    private static final int TAB_H = 16;
    private static final int PATTERN_TAB_X = 211;
    private static final int PATTERN_TAB_W = 76;
    private static final int ASSIST_TAB_X = 291;
    private static final int ASSIST_TAB_W = 92;

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

    private static final int ASSIST_STATUS_X = 7;
    private static final int ASSIST_STATUS_Y = 50;
    private static final int ASSIST_STATUS_W = 184;
    private static final int ASSIST_STATUS_H = 166;
    private static final int ASSIST_MATERIAL_X = 198;
    private static final int ASSIST_MATERIAL_Y = 50;
    private static final int ASSIST_MATERIAL_W = 185;
    private static final int ASSIST_MATERIAL_H = 166;
    private static final int ASSIST_MATERIAL_GRID_Y = ASSIST_MATERIAL_Y + 24;
    private static final int ASSIST_MATERIAL_COLS = 10;
    private static final int ASSIST_MATERIAL_ROWS = 6;
    private static final int PREVIEW_BUTTON_X = ASSIST_STATUS_X + 10;
    private static final int EXECUTE_BUTTON_X = ASSIST_STATUS_X + 94;
    private static final int ACTION_BUTTON_Y = ASSIST_STATUS_Y + 124;
    private static final int ACTION_BUTTON_W = 80;
    private static final int MODE_BUTTON_Y = ASSIST_STATUS_Y + 146;
    private static final int MODE_BUTTON_W = 50;
    private static final int MODE_BUTTON_GAP = 6;

    private static final int SLOT_SIZE = 18;
    private static final int FOOTER_Y = 224;

    private final HeldItemUIFactory.HeldItemHolder holder;
    private final List<RenderedButton> renderedButtons = new ArrayList<>();
    private final List<Widget> patternWidgets = new ArrayList<>();
    private final List<Widget> assistWidgets = new ArrayList<>();

    private NEMultiblockPatternViewerWidget patternViewer;
    private ViewMode viewMode = ViewMode.PATTERN;
    private boolean mirroredPattern;
    private int patternMaterialScroll;
    private int assistMaterialScroll;

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
        assistWidgets.clear();

        addLocalButton(
                PATTERN_TAB_X,
                TAB_Y,
                PATTERN_TAB_W,
                TAB_H,
                () -> Component.translatable("gui.neoecoae.multiblock.pattern"),
                () -> viewMode == ViewMode.PATTERN,
                () -> setViewMode(ViewMode.PATTERN),
                () -> true);
        addLocalButton(
                ASSIST_TAB_X,
                TAB_Y,
                ASSIST_TAB_W,
                TAB_H,
                () -> Component.translatable("gui.neoecoae.multiblock.open_build_assist"),
                () -> viewMode == ViewMode.BUILD_ASSIST,
                () -> setViewMode(ViewMode.BUILD_ASSIST),
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
        addLocalButton(
                MIRROR_X,
                CONTROL_Y,
                MIRROR_W,
                CONTROL_H,
                () -> Component.translatable("gui.neoecoae.multiblock.mirror"),
                () -> mirroredPattern,
                () -> mirroredPattern = !mirroredPattern,
                () -> true);

        patternViewer = new NEMultiblockPatternViewerWidget(
                SCENE_X,
                SCENE_Y,
                SCENE_W,
                SCENE_H,
                this::currentDefinition,
                () -> currentState().length(),
                () -> mirroredPattern);
        addWidget(patternViewer);
        patternWidgets.add(patternViewer);
        addPatternLayerButton(LAYER_PREV_X, Component.literal("<"), patternViewer::previousLayer);
        addPatternLayerButton(LAYER_NEXT_X, Component.literal(">"), patternViewer::nextLayer);

        addServerButton(
                PREVIEW_BUTTON_X,
                ACTION_BUTTON_Y,
                ACTION_BUTTON_W,
                CONTROL_H,
                () -> Component.translatable("gui.neoecoae.multiblock.preview"),
                Action.PREVIEW_LINKED,
                () -> false,
                () -> viewMode == ViewMode.BUILD_ASSIST,
                assistWidgets);
        addServerButton(
                EXECUTE_BUTTON_X,
                ACTION_BUTTON_Y,
                ACTION_BUTTON_W,
                CONTROL_H,
                () -> Component.translatable("gui.neoecoae.multiblock.build"),
                Action.EXECUTE_LINKED,
                () -> false,
                () -> viewMode == ViewMode.BUILD_ASSIST,
                assistWidgets);
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
        refreshWidgetVisibility();
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
        if (viewMode == ViewMode.PATTERN && isInPatternMaterialGrid(mouseX, mouseY)) {
            int size = patternMaterials().size();
            patternMaterialScroll = clampScroll(
                    patternMaterialScroll + (wheelDelta < 0 ? PATTERN_MATERIAL_COLS : -PATTERN_MATERIAL_COLS),
                    size,
                    patternVisibleSlots());
            return size > patternVisibleSlots();
        }
        if (viewMode == ViewMode.BUILD_ASSIST && isInAssistMaterialGrid(mouseX, mouseY)) {
            int size = currentState().materials().size();
            assistMaterialScroll = clampScroll(
                    assistMaterialScroll + (wheelDelta < 0 ? ASSIST_MATERIAL_COLS : -ASSIST_MATERIAL_COLS),
                    size,
                    assistVisibleSlots());
            return size > assistVisibleSlots();
        }
        return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
    }

    @Override
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        drawInsetValue(graphics, LENGTH_X + LENGTH_BUTTON_W, CONTROL_Y, LENGTH_VALUE_W, CONTROL_H);
        if (viewMode == ViewMode.PATTERN) {
            NELDLibStyle.drawDarkInsetRect(
                    graphics, absX(PATTERN_PANEL_X), absY(PATTERN_PANEL_Y), PATTERN_PANEL_W, PATTERN_PANEL_H);
            NELDLibStyle.drawDarkInsetRect(
                    graphics, absX(INFO_PANEL_X), absY(INFO_PANEL_Y), INFO_PANEL_W, INFO_PANEL_H);
            drawPatternMaterialSlots(graphics);
        } else {
            NELDLibStyle.drawDarkInsetRect(
                    graphics, absX(ASSIST_STATUS_X), absY(ASSIST_STATUS_Y), ASSIST_STATUS_W, ASSIST_STATUS_H);
            NELDLibStyle.drawDarkInsetRect(
                    graphics, absX(ASSIST_MATERIAL_X), absY(ASSIST_MATERIAL_Y), ASSIST_MATERIAL_W, ASSIST_MATERIAL_H);
            drawAssistMaterialSlots(graphics);
        }
    }

    @Override
    protected void drawMachineForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        drawLocalString(graphics, title, 8, 8, TEXT_PRIMARY);
        drawControlValues(graphics);
        drawRenderedButtonLabels(graphics);
        if (viewMode == ViewMode.PATTERN) {
            drawPatternPage(graphics);
        } else {
            drawBuildAssistPage(graphics);
        }
    }

    @Override
    protected void drawMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (viewMode == ViewMode.PATTERN) {
            renderPatternMaterialTooltip(graphics, mouseX, mouseY);
        } else {
            renderAssistMaterialTooltip(graphics, mouseX, mouseY);
        }
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
                Component.translatable("gui.neoecoae.multiblock.preview_only_hint"),
                7,
                FOOTER_Y,
                WIDTH - 14,
                TEXT_MUTED);
    }

    private void drawBuildAssistPage(GuiGraphics graphics) {
        NEStructureTerminalConfigState state = currentState();
        drawLocalString(
                graphics,
                Component.translatable("gui.neoecoae.multiblock.build_assist"),
                ASSIST_STATUS_X + 8,
                ASSIST_STATUS_Y + 7,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        drawStatusLine(
                graphics,
                Component.translatable("gui.neoecoae.multiblock.linked_host"),
                state.linkedHost()
                        ? Component.translatable("gui.neoecoae.common.yes")
                        : Component.translatable("gui.neoecoae.common.no"),
                ASSIST_STATUS_Y + 22,
                state.linkedHost() ? NELDLibStyle.DARK_TEXT_SUCCESS : NELDLibStyle.DARK_TEXT_ERROR);
        drawStatusLine(
                graphics,
                Component.translatable("gui.neoecoae.multiblock.missing", state.previewMissingBlocks()),
                Component.translatable("gui.neoecoae.multiblock.conflicts", state.previewConflictBlocks()),
                ASSIST_STATUS_Y + 39,
                state.previewConflictBlocks() == 0 ? NELDLibStyle.DARK_TEXT_SUCCESS : NELDLibStyle.DARK_TEXT_ERROR);
        drawStatusLine(
                graphics,
                Component.translatable("gui.neoecoae.multiblock.reused", state.previewReusedBlocks()),
                Component.translatable("gui.neoecoae.multiblock.required_items", state.previewRequiredItems()),
                ASSIST_STATUS_Y + 55,
                NELDLibStyle.DARK_TEXT_VALUE);
        drawFitted(
                graphics,
                buildStatusComponent(state),
                ASSIST_STATUS_X + 8,
                ASSIST_STATUS_Y + 75,
                ASSIST_STATUS_W - 16,
                state.previewConflictBlocks() > 0 ? NELDLibStyle.DARK_TEXT_ERROR : NELDLibStyle.DARK_TEXT_MUTED);
        drawLocalString(
                graphics,
                Component.translatable("gui.neoecoae.multiblock.inventory_materials"),
                ASSIST_MATERIAL_X + 8,
                ASSIST_MATERIAL_Y + 7,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        drawAssistMaterialItems(graphics);
        drawPageText(
                graphics,
                assistMaterialScroll,
                state.materials().size(),
                assistVisibleSlots(),
                ASSIST_MATERIAL_X + ASSIST_MATERIAL_W - 7,
                ASSIST_MATERIAL_Y + 7);
        drawCenteredFitted(
                graphics,
                Component.translatable(
                        state.linkedHost()
                                ? "gui.neoecoae.multiblock.build_assist_hint"
                                : "gui.neoecoae.multiblock.no_linked_host_hint"),
                7,
                FOOTER_Y,
                WIDTH - 14,
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

    private void drawStatusLine(GuiGraphics graphics, Component left, Component right, int y, int rightColor) {
        drawLocalString(graphics, left, ASSIST_STATUS_X + 8, y, NELDLibStyle.DARK_TEXT_MUTED);
        drawRightLocalString(graphics, right, ASSIST_STATUS_X + ASSIST_STATUS_W - 8, y, rightColor);
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

    private void addModeButton(StructureTerminalMode mode, int index, Component label, Action action) {
        addServerButton(
                ASSIST_STATUS_X + 11 + index * (MODE_BUTTON_W + MODE_BUTTON_GAP),
                MODE_BUTTON_Y,
                MODE_BUTTON_W,
                CONTROL_H,
                () -> label,
                action,
                () -> currentState().operationMode() == mode,
                () -> viewMode == ViewMode.BUILD_ASSIST,
                assistWidgets);
    }

    private void addPatternLayerButton(int x, Component label, Runnable action) {
        addLocalButton(
                x,
                PATTERN_PANEL_Y + 3,
                LAYER_BUTTON_W,
                CONTROL_H,
                () -> label,
                () -> false,
                action,
                () -> viewMode == ViewMode.PATTERN,
                patternWidgets);
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

    private void setViewMode(ViewMode mode) {
        viewMode = mode;
        refreshWidgetVisibility();
    }

    private void refreshWidgetVisibility() {
        boolean pattern = viewMode == ViewMode.PATTERN;
        for (Widget widget : patternWidgets) {
            widget.setVisible(pattern);
            widget.setActive(pattern);
        }
        for (Widget widget : assistWidgets) {
            widget.setVisible(!pattern);
            widget.setActive(!pattern);
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
            case PREVIEW_LINKED -> previewLinkedHost(stack);
            case EXECUTE_LINKED -> executeLinkedHost(stack);
        }
        holder.markAsDirty();
    }

    private void previewLinkedHost(ItemStack stack) {
        if (!(holder.getPlayer() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        INEMultiblockBuildHost host = StructureTerminalItem.findLinkedHost(serverPlayer, stack);
        if (host == null) {
            return;
        }
        int length = StructureTerminalItem.getBuildLength(stack, host.getMaxBuildLength());
        boolean mirrored = StructureTerminalItem.getOperationMode(stack) == StructureTerminalMode.MIRRORED_BUILD;
        host.previewStructure(serverPlayer, length, mirrored);
    }

    private void executeLinkedHost(ItemStack stack) {
        if (!(holder.getPlayer() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        INEMultiblockBuildHost host = StructureTerminalItem.findLinkedHost(serverPlayer, stack);
        if (host == null) {
            return;
        }
        int length = StructureTerminalItem.getBuildLength(stack, host.getMaxBuildLength());
        switch (StructureTerminalItem.getOperationMode(stack)) {
            case BUILD -> host.autoBuild(serverPlayer, length, false);
            case MIRRORED_BUILD -> host.autoBuild(serverPlayer, length, true);
            case DISMANTLE -> host.dismantle(serverPlayer);
        }
    }

    private MultiBlockDefinition currentDefinition() {
        NEStructureTerminalConfigState state = currentState();
        return state == null ? null : state.hostType().definitionForTier(state.tier());
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

    private void drawAssistMaterialSlots(GuiGraphics graphics) {
        for (int i = 0; i < assistVisibleSlots(); i++) {
            NELDLibStyle.drawDarkSlot(graphics, absX(assistSlotX(i)), absY(assistSlotY(i)), SLOT_SIZE);
        }
    }

    private void drawPatternMaterialItems(GuiGraphics graphics) {
        List<ItemStack> materials = patternMaterials();
        patternMaterialScroll = clampScroll(patternMaterialScroll, materials.size(), patternVisibleSlots());
        int count = Math.min(patternVisibleSlots(), Math.max(0, materials.size() - patternMaterialScroll));
        NELDLibGuiRenderState.beginVanillaGuiItemBatch(graphics);
        for (int i = 0; i < count; i++) {
            ItemStack stack = materials.get(patternMaterialScroll + i);
            renderMaterialItem(graphics, stack, patternSlotX(i), patternSlotY(i), stack.getCount());
        }
        NELDLibGuiRenderState.endVanillaGuiItemBatch(graphics);
    }

    private void drawAssistMaterialItems(GuiGraphics graphics) {
        List<NEStructureTerminalUiState.BuildMaterialEntry> materials =
                currentState().materials();
        assistMaterialScroll = clampScroll(assistMaterialScroll, materials.size(), assistVisibleSlots());
        int count = Math.min(assistVisibleSlots(), Math.max(0, materials.size() - assistMaterialScroll));
        NELDLibGuiRenderState.beginVanillaGuiItemBatch(graphics);
        for (int i = 0; i < count; i++) {
            NEStructureTerminalUiState.BuildMaterialEntry entry = materials.get(assistMaterialScroll + i);
            renderMaterialItem(graphics, entry.item(), assistSlotX(i), assistSlotY(i), entry.required());
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
        int visibleIndex = slotAt(mouseX, mouseY, true);
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

    private void renderAssistMaterialTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int visibleIndex = slotAt(mouseX, mouseY, false);
        List<NEStructureTerminalUiState.BuildMaterialEntry> materials =
                currentState().materials();
        int index = visibleIndex < 0 ? -1 : assistMaterialScroll + visibleIndex;
        if (index < 0 || index >= materials.size()) {
            return;
        }
        NEStructureTerminalUiState.BuildMaterialEntry entry = materials.get(index);
        graphics.renderTooltip(
                font(),
                List.of(
                        entry.item().getHoverName(),
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

    private int slotAt(double mouseX, double mouseY, boolean pattern) {
        int slots = pattern ? patternVisibleSlots() : assistVisibleSlots();
        for (int i = 0; i < slots; i++) {
            int x = absX(pattern ? patternSlotX(i) : assistSlotX(i));
            int y = absY(pattern ? patternSlotY(i) : assistSlotY(i));
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

    private int assistSlotX(int index) {
        return ASSIST_MATERIAL_X + 3 + index % ASSIST_MATERIAL_COLS * SLOT_SIZE;
    }

    private int assistSlotY(int index) {
        return ASSIST_MATERIAL_GRID_Y + index / ASSIST_MATERIAL_COLS * SLOT_SIZE;
    }

    private boolean isInPatternMaterialGrid(double mouseX, double mouseY) {
        return mouseX >= absX(INFO_PANEL_X)
                && mouseX < absX(INFO_PANEL_X + INFO_PANEL_W)
                && mouseY >= absY(PATTERN_MATERIAL_Y)
                && mouseY < absY(PATTERN_MATERIAL_Y + PATTERN_MATERIAL_ROWS * SLOT_SIZE);
    }

    private boolean isInAssistMaterialGrid(double mouseX, double mouseY) {
        return mouseX >= absX(ASSIST_MATERIAL_X)
                && mouseX < absX(ASSIST_MATERIAL_X + ASSIST_MATERIAL_W)
                && mouseY >= absY(ASSIST_MATERIAL_GRID_Y)
                && mouseY < absY(ASSIST_MATERIAL_GRID_Y + ASSIST_MATERIAL_ROWS * SLOT_SIZE);
    }

    private int patternVisibleSlots() {
        return PATTERN_MATERIAL_COLS * PATTERN_MATERIAL_ROWS;
    }

    private int assistVisibleSlots() {
        return ASSIST_MATERIAL_COLS * ASSIST_MATERIAL_ROWS;
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

    private static Component buildStatusComponent(NEStructureTerminalConfigState state) {
        if ("gui.neoecoae.multiblock.status.building".equals(state.previewStatusKey())) {
            return Component.translatable(
                    state.previewStatusKey(), state.previewStatusArg1(), state.previewStatusArg2());
        }
        return Component.translatable(state.previewStatusKey());
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

    private enum ViewMode {
        PATTERN,
        BUILD_ASSIST
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
        SELECT_BUILD_MODE,
        SELECT_MIRRORED_BUILD_MODE,
        SELECT_DISMANTLE_MODE,
        PREVIEW_LINKED,
        EXECUTE_LINKED
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
