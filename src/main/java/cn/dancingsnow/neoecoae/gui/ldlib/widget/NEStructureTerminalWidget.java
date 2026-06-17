package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.client.gui.ldlib.NELDLibClientStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStateCodecs;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEStructureTerminalConfigState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.StructureTerminalActions;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.preview.MultiblockPatternSnapshot;
import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class NEStructureTerminalWidget extends NELDLibSyncedStateWidget<NEStructureTerminalConfigState> {
    public static final int WIDTH = NEStructureTerminalLayout.WIDTH;
    public static final int HEIGHT = NEStructureTerminalLayout.HEIGHT;

    private final HeldItemUIFactory.HeldItemHolder holder;

    private NEMultiblockPatternViewerWidget patternViewer;
    private NEStructureTerminalButtonPanel buttonPanel;
    private NEStructureTerminalMaterialPanel materialPanel;
    private NEStructureTerminalInfoPanel infoPanel;

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
        patternViewer = new NEMultiblockPatternViewerWidget(
                NEStructureTerminalLayout.SCENE_X,
                NEStructureTerminalLayout.SCENE_Y,
                NEStructureTerminalLayout.SCENE_W,
                NEStructureTerminalLayout.SCENE_H,
                this::currentDefinition,
                () -> currentState().length(),
                () -> currentState().previewMirrored(),
                () -> currentState().previewFormed(),
                () -> currentState().previewLayer());
        addWidget(patternViewer);

        materialPanel = new NEStructureTerminalMaterialPanel(
                this::currentState, this::patternMaterials, this::sendMaterialScroll);
        buttonPanel = new NEStructureTerminalButtonPanel(this, this::currentState, this::sendAction);
        buttonPanel.init(patternViewer);
        infoPanel = new NEStructureTerminalInfoPanel(patternViewer, materialPanel);
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (id == NEStructureTerminalLayout.ACTION_UPDATE_ID) {
            StructureTerminalActions.apply(holder, buffer.readEnum(Action.class), buffer);
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
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
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
    protected void drawMachineForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        NEStructureTerminalRenderContext context = renderContext();
        drawLocalString(graphics, title, 8, 8, TEXT_PRIMARY);
        drawControlValues(context, graphics);
        infoPanel.draw(context, graphics);
    }

    @Override
    protected void drawMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        materialPanel.drawTooltip(renderContext(), graphics, mouseX, mouseY);
    }

    private void drawControlValues(NEStructureTerminalRenderContext context, GuiGraphics graphics) {
        context.drawCenteredLocalString(
                graphics,
                Component.literal(Integer.toString(currentState().length())),
                NEStructureTerminalLayout.LENGTH_X + NEStructureTerminalLayout.LENGTH_BUTTON_W,
                NEStructureTerminalLayout.CONTROL_Y + 5,
                NEStructureTerminalLayout.LENGTH_VALUE_W,
                NELDLibStyle.DARK_TEXT_VALUE);
    }

    private void sendAction(Action action) {
        writeClientAction(NEStructureTerminalLayout.ACTION_UPDATE_ID, buf -> buf.writeEnum(action));
    }

    private void sendMaterialScroll(int scroll) {
        writeClientAction(NEStructureTerminalLayout.ACTION_UPDATE_ID, buf -> {
            buf.writeEnum(Action.SET_PATTERN_MATERIAL_SCROLL);
            buf.writeVarInt(scroll);
        });
    }

    private MultiBlockDefinition currentDefinition() {
        NEStructureTerminalConfigState state = currentState();
        return state == null ? null : state.hostType().definitionForTier(state.tier());
    }

    private List<ItemStack> patternMaterials() {
        MultiblockPatternSnapshot snapshot = patternViewer == null ? null : patternViewer.snapshot();
        return snapshot == null ? List.of() : snapshot.materialSummary();
    }

    private void drawInsetValue(GuiGraphics graphics, int x, int y, int w, int h) {
        NELDLibClientStyle.drawTinyInsetRect(graphics, absX(x), absY(y), w, h, 0xFF201E27);
    }

    private NEStructureTerminalRenderContext renderContext() {
        return new NEStructureTerminalRenderContext(getPositionX(), getPositionY());
    }

    enum Action {
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
}
