package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.client.gui.ldlib.NELDLibClientStyle;
import cn.dancingsnow.neoecoae.client.multiblock.preview.MultiblockPreviewScene;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEHostFormationPreviewState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEHostFormationPreviewState.BlockStatus;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStateCodecs;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.multiblock.INEMultiblockBuildHost;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.preview.MultiblockPatternPreviewService;
import cn.dancingsnow.neoecoae.multiblock.preview.MultiblockPatternSnapshot;
import cn.dancingsnow.neoecoae.multiblock.preview.PatternBlockEntry;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class NEHostFormationPreviewWidget extends NELDLibSyncedStateWidget<NEHostFormationPreviewState> {
    public static final int UI_WIDTH = NEStructureTerminalLayout.WIDTH;
    public static final int UI_HEIGHT = NEStructureTerminalLayout.HEIGHT;

    private final INEMultiblockBuildHost host;
    private final Player player;
    private final StateSource stateSource;
    private NELDLibMultiblockSceneWidget sceneWidget;
    private NEStructureTerminalMaterialPanel materialPanel;

    private MultiBlockDefinition cachedDefinition;
    private int cachedLength = Integer.MIN_VALUE;
    private boolean cachedMirrored;
    private MultiblockPatternSnapshot cachedSnapshot;

    public NEHostFormationPreviewWidget(INEMultiblockBuildHost host, Player player) {
        this(host, player, new StateSource(host));
    }

    private NEHostFormationPreviewWidget(INEMultiblockBuildHost host, Player player, StateSource stateSource) {
        super(
                Component.translatable("gui.neoecoae.host_formation.title"),
                UI_WIDTH,
                UI_HEIGHT,
                NEHostFormationPreviewState.empty(),
                stateSource::get,
                NELDLibStateCodecs::writeHostFormationPreview,
                NELDLibStateCodecs::readHostFormationPreview,
                10);
        this.host = host;
        this.player = player;
        this.stateSource = stateSource;
    }

    @Override
    protected void initLdWidgets() {
        sceneWidget = new NELDLibMultiblockSceneWidget(
                NEStructureTerminalLayout.SCENE_X,
                NEStructureTerminalLayout.SCENE_Y,
                NEStructureTerminalLayout.SCENE_W,
                NEStructureTerminalLayout.SCENE_H,
                this::diagnosticScene);
        addWidget(sceneWidget);
        materialPanel = new NEStructureTerminalMaterialPanel(
                () -> currentState().previewMaterialScroll(), this::requiredMaterials, this::sendMaterialScroll);

        addTextButton(
                NEStructureTerminalLayout.LENGTH_X,
                NEStructureTerminalLayout.CONTROL_Y,
                NEStructureTerminalLayout.LENGTH_BUTTON_W,
                NEStructureTerminalLayout.CONTROL_H,
                () -> Component.literal("-"),
                Action.DECREASE_LENGTH,
                () -> false);
        addTextButton(
                NEStructureTerminalLayout.LENGTH_X
                        + NEStructureTerminalLayout.LENGTH_BUTTON_W
                        + NEStructureTerminalLayout.LENGTH_VALUE_W,
                NEStructureTerminalLayout.CONTROL_Y,
                NEStructureTerminalLayout.LENGTH_BUTTON_W,
                NEStructureTerminalLayout.CONTROL_H,
                () -> Component.literal("+"),
                Action.INCREASE_LENGTH,
                () -> false);
        addTextButton(
                NEStructureTerminalLayout.MIRROR_X,
                NEStructureTerminalLayout.CONTROL_Y,
                NEStructureTerminalLayout.MIRROR_W,
                NEStructureTerminalLayout.CONTROL_H,
                () -> Component.translatable("gui.neoecoae.structure_terminal.preview_mirrored"),
                Action.TOGGLE_MIRROR,
                () -> currentState().mirrored());
        addTextButton(
                NEStructureTerminalLayout.LAYER_PREV_X,
                NEStructureTerminalLayout.PATTERN_PANEL_Y + 3,
                NEStructureTerminalLayout.LAYER_BUTTON_W,
                NEStructureTerminalLayout.CONTROL_H,
                () -> Component.literal("<"),
                Action.PREVIOUS_LAYER,
                () -> false);
        addTextButton(
                NEStructureTerminalLayout.LAYER_NEXT_X,
                NEStructureTerminalLayout.PATTERN_PANEL_Y + 3,
                NEStructureTerminalLayout.LAYER_BUTTON_W,
                NEStructureTerminalLayout.CONTROL_H,
                () -> Component.literal(">"),
                Action.NEXT_LAYER,
                () -> false);
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (id == NEStructureTerminalLayout.ACTION_UPDATE_ID) {
            Action action = buffer.readEnum(Action.class);
            switch (action) {
                case INCREASE_LENGTH -> {
                    host.increaseBuildLength();
                    stateSource.selectedLayer = -1;
                }
                case DECREASE_LENGTH -> {
                    host.decreaseBuildLength();
                    stateSource.selectedLayer = -1;
                }
                case TOGGLE_MIRROR -> {
                    stateSource.mirrored = !stateSource.mirrored;
                    stateSource.selectedLayer = -1;
                    stateSource.materialScroll = 0;
                }
                case PREVIOUS_LAYER -> selectLayer(-1);
                case NEXT_LAYER -> selectLayer(1);
                case SET_MATERIAL_SCROLL -> stateSource.materialScroll = Math.max(0, buffer.readVarInt());
            }
            syncStateNow();
            return;
        }
        super.handleClientAction(id, buffer);
    }

    @Override
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (materialPanel != null && materialPanel.isMouseOverGrid(renderContext(), mouseX, mouseY)) {
            return materialPanel.mouseWheelMove(renderContext(), mouseX, mouseY, wheelDelta);
        }
        return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
    }

    @Override
    protected void drawMachineBackground(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        NEStructureTerminalRenderContext context = renderContext();
        drawInsetValue(
                graphics,
                NEStructureTerminalLayout.LENGTH_X + NEStructureTerminalLayout.LENGTH_BUTTON_W,
                NEStructureTerminalLayout.CONTROL_Y,
                NEStructureTerminalLayout.LENGTH_VALUE_W,
                NEStructureTerminalLayout.CONTROL_H);
        NELDLibClientStyle.drawDarkInsetRect(
                graphics,
                context.absX(NEStructureTerminalLayout.PATTERN_PANEL_X),
                context.absY(NEStructureTerminalLayout.PATTERN_PANEL_Y),
                NEStructureTerminalLayout.PATTERN_PANEL_W,
                NEStructureTerminalLayout.PATTERN_PANEL_H);
        NELDLibClientStyle.drawDarkInsetRect(
                graphics,
                context.absX(NEStructureTerminalLayout.INFO_PANEL_X),
                context.absY(NEStructureTerminalLayout.INFO_PANEL_Y),
                NEStructureTerminalLayout.INFO_PANEL_W,
                NEStructureTerminalLayout.INFO_PANEL_H);
        materialPanel.drawSlots(context, graphics);
    }

    @Override
    protected void drawMachineForeground(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        NEStructureTerminalRenderContext context = renderContext();
        drawLocalString(graphics, title, 8, 8, TEXT_PRIMARY);
        context.drawCenteredLocalString(
                graphics,
                Component.literal(Integer.toString(currentState().length())),
                NEStructureTerminalLayout.LENGTH_X + NEStructureTerminalLayout.LENGTH_BUTTON_W,
                NEStructureTerminalLayout.CONTROL_Y + 5,
                NEStructureTerminalLayout.LENGTH_VALUE_W,
                NELDLibStyle.DARK_TEXT_VALUE);
        drawPatternHeader(context, graphics);
        drawInfoPanel(context, graphics);
    }

    @Override
    protected void drawMachineTooltips(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY) {
        materialPanel.drawTooltip(renderContext(), graphics, mouseX, mouseY);
    }

    private void drawPatternHeader(NEStructureTerminalRenderContext context, net.minecraft.client.gui.GuiGraphics graphics) {
        MultiblockPatternSnapshot snapshot = snapshot();
        context.drawFitted(
                graphics,
                snapshot == null ? Component.translatable("gui.neoecoae.multiblock.pattern") : snapshot.definition().getName(),
                NEStructureTerminalLayout.PATTERN_PANEL_X + 8,
                NEStructureTerminalLayout.PATTERN_PANEL_Y + 7,
                NEStructureTerminalLayout.LAYER_PREV_X - NEStructureTerminalLayout.PATTERN_PANEL_X - 12,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        context.drawCenteredLocalString(
                graphics,
                currentState().selectedLayer() < 0
                        ? Component.translatable("gui.neoecoae.multiblock.layer_all")
                        : Component.translatable("gui.neoecoae.multiblock.layer_value", currentState().selectedLayer()),
                NEStructureTerminalLayout.LAYER_LABEL_X,
                NEStructureTerminalLayout.PATTERN_PANEL_Y + 7,
                NEStructureTerminalLayout.LAYER_LABEL_W,
                NELDLibStyle.DARK_TEXT_VALUE);
    }

    private void drawInfoPanel(NEStructureTerminalRenderContext context, net.minecraft.client.gui.GuiGraphics graphics) {
        NEHostFormationPreviewState state = currentState();
        MultiblockPatternSnapshot snapshot = snapshot();
        context.drawLocalString(
                graphics,
                Component.translatable("gui.neoecoae.host_formation.diagnostics"),
                NEStructureTerminalLayout.INFO_PANEL_X + 7,
                NEStructureTerminalLayout.INFO_PANEL_Y + 7,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        if (snapshot != null) {
            context.drawLocalString(
                    graphics,
                    Component.translatable(
                            "gui.neoecoae.multiblock.size", snapshot.sizeX(), snapshot.sizeY(), snapshot.sizeZ()),
                    NEStructureTerminalLayout.INFO_PANEL_X + 7,
                    NEStructureTerminalLayout.INFO_PANEL_Y + 20,
                    NELDLibStyle.DARK_TEXT_VALUE);
            PatternBlockEntry controller = controllerEntry(snapshot);
            if (controller != null) {
                context.drawFitted(
                        graphics,
                        Component.translatable(
                                "gui.neoecoae.multiblock.controller",
                                controller.relativePos().getX(),
                                controller.relativePos().getY(),
                                controller.relativePos().getZ()),
                        NEStructureTerminalLayout.INFO_PANEL_X + 7,
                        NEStructureTerminalLayout.INFO_PANEL_Y + 33,
                        NEStructureTerminalLayout.INFO_PANEL_W - 14,
                        NELDLibStyle.DARK_TEXT_MUTED);
            }
        }
        context.drawLocalString(
                graphics,
                Component.translatable(
                        "gui.neoecoae.host_formation.installed", state.matchedBlocks(), state.totalBlocks()),
                NEStructureTerminalLayout.INFO_PANEL_X + 7,
                NEStructureTerminalLayout.INFO_PANEL_Y + 48,
                NELDLibStyle.DARK_TEXT_VALUE);
        context.drawLocalString(
                graphics,
                Component.translatable(
                        "gui.neoecoae.host_formation.missing_conflict",
                        state.missingBlocks(),
                        state.conflictBlocks()),
                NEStructureTerminalLayout.INFO_PANEL_X + 7,
                NEStructureTerminalLayout.INFO_PANEL_Y + 61,
                state.conflictBlocks() > 0 ? 0xFFFF6060 : NELDLibStyle.DARK_TEXT_MUTED);
        context.drawLocalString(
                graphics,
                Component.translatable("gui.neoecoae.multiblock.material_summary"),
                NEStructureTerminalLayout.INFO_PANEL_X + 7,
                NEStructureTerminalLayout.INFO_PANEL_Y + 74,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        materialPanel.drawItems(context, graphics);
        materialPanel.drawPageText(
                context,
                graphics,
                NEStructureTerminalLayout.INFO_PANEL_X + NEStructureTerminalLayout.INFO_PANEL_W - 7,
                NEStructureTerminalLayout.INFO_PANEL_Y + 74,
                NELDLibStyle.DARK_TEXT_MUTED);
    }

    private MultiblockPreviewScene diagnosticScene() {
        MultiblockPatternSnapshot snapshot = snapshot();
        if (snapshot == null) {
            return null;
        }
        NEHostFormationPreviewState state = currentState();
        Map<BlockPos, BlockStatus> statuses = new HashMap<>();
        for (NEHostFormationPreviewState.BlockEntry entry : state.blocks()) {
            statuses.put(entry.relativePos(), entry.status());
        }

        boolean blink = ((Util.getMillis() / 450L) & 1L) == 0L;
        BlockState errorState = (blink ? Blocks.RED_STAINED_GLASS : Blocks.RED_CONCRETE).defaultBlockState();
        LinkedHashMap<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        List<BlockPos> ordered = new java.util.ArrayList<>();
        int selected = state.selectedLayer();
        for (PatternBlockEntry entry : snapshot.blocks()) {
            if (selected >= 0 && entry.layerY() != selected) {
                continue;
            }
            BlockStatus status = statuses.getOrDefault(entry.relativePos(), BlockStatus.MISSING);
            BlockState renderState = status == BlockStatus.MATCHED ? entry.blockState() : errorState;
            blocks.put(entry.relativePos(), renderState);
            ordered.add(entry.relativePos());
        }
        return new MultiblockPreviewScene(
                snapshot.definition(),
                snapshot.repeats(),
                false,
                blocks,
                ordered,
                state.copyRequiredItems(),
                snapshot.min().getX(),
                snapshot.min().getY(),
                snapshot.min().getZ(),
                snapshot.max().getX(),
                snapshot.max().getY(),
                snapshot.max().getZ(),
                snapshot.max().getY());
    }

    private MultiblockPatternSnapshot snapshot() {
        MultiBlockDefinition definition = host.getBuildDefinition();
        NEHostFormationPreviewState state = currentState();
        if (definition == null) {
            cachedDefinition = null;
            cachedSnapshot = null;
            return null;
        }
        if (cachedSnapshot == null
                || cachedDefinition != definition
                || cachedLength != state.length()
                || cachedMirrored != state.mirrored()) {
            cachedDefinition = definition;
            cachedLength = state.length();
            cachedMirrored = state.mirrored();
            cachedSnapshot = MultiblockPatternPreviewService.create(definition, state.length(), state.mirrored());
        }
        return cachedSnapshot;
    }

    private void selectLayer(int delta) {
        MultiblockPatternSnapshot snapshot = snapshot();
        if (snapshot == null || snapshot.layers().isEmpty()) {
            stateSource.selectedLayer = -1;
            return;
        }
        int current = stateSource.selectedLayer;
        if (current < 0) {
            stateSource.selectedLayer = delta > 0 ? snapshot.minLayerY() : snapshot.maxLayerY();
            return;
        }
        int next = current + delta;
        stateSource.selectedLayer = next < snapshot.minLayerY() || next > snapshot.maxLayerY() ? -1 : next;
    }

    private List<ItemStack> requiredMaterials() {
        return currentState().copyRequiredItems();
    }

    private void addTextButton(
            int x,
            int y,
            int w,
            int h,
            java.util.function.Supplier<Component> label,
            Action action,
            java.util.function.BooleanSupplier selected) {
        addWidget(new NEAe2TextButtonWidget(
                x,
                y,
                w,
                h,
                label,
                click -> {
                    if (click.isRemote) {
                        sendAction(action);
                    }
                },
                selected));
    }

    private void sendAction(Action action) {
        writeClientAction(NEStructureTerminalLayout.ACTION_UPDATE_ID, buf -> buf.writeEnum(action));
    }

    private void sendMaterialScroll(int scroll) {
        writeClientAction(NEStructureTerminalLayout.ACTION_UPDATE_ID, buf -> {
            buf.writeEnum(Action.SET_MATERIAL_SCROLL);
            buf.writeVarInt(scroll);
        });
    }

    private void drawInsetValue(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, int w, int h) {
        NELDLibClientStyle.drawTinyInsetRect(graphics, absX(x), absY(y), w, h, 0xFF201E27);
    }

    private NEStructureTerminalRenderContext renderContext() {
        return new NEStructureTerminalRenderContext(getPositionX(), getPositionY());
    }

    private static PatternBlockEntry controllerEntry(MultiblockPatternSnapshot snapshot) {
        for (PatternBlockEntry entry : snapshot.blocks()) {
            if (entry.controller()) {
                return entry;
            }
        }
        return null;
    }

    private enum Action {
        INCREASE_LENGTH,
        DECREASE_LENGTH,
        TOGGLE_MIRROR,
        PREVIOUS_LAYER,
        NEXT_LAYER,
        SET_MATERIAL_SCROLL
    }

    private static final class StateSource {
        private final INEMultiblockBuildHost host;
        private boolean mirrored;
        private int selectedLayer = -1;
        private int materialScroll;

        private StateSource(INEMultiblockBuildHost host) {
            this.host = host;
        }

        private NEHostFormationPreviewState get() {
            return NEHostFormationPreviewState.fromHost(host, mirrored, selectedLayer, materialScroll);
        }
    }
}
