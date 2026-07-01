package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.multiblock.preview.MultiblockPatternSnapshot;
import cn.dancingsnow.neoecoae.multiblock.preview.PatternBlockEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class NEStructureTerminalInfoPanel {
    private final NEMultiblockPatternViewerWidget patternViewer;
    private final NEStructureTerminalMaterialPanel materialPanel;

    NEStructureTerminalInfoPanel(
            NEMultiblockPatternViewerWidget patternViewer, NEStructureTerminalMaterialPanel materialPanel) {
        this.patternViewer = patternViewer;
        this.materialPanel = materialPanel;
    }

    void draw(NEStructureTerminalRenderContext context, GuiGraphics graphics) {
        MultiblockPatternSnapshot snapshot = patternViewer.snapshot();
        context.drawLocalString(
                graphics,
                snapshot == null
                        ? Component.translatable("gui.neoecoae.multiblock.pattern")
                        : snapshot.definition().getName(),
                NEStructureTerminalLayout.PATTERN_PANEL_X + 8,
                NEStructureTerminalLayout.PATTERN_PANEL_Y + 7,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        context.drawCenteredLocalString(
                graphics,
                patternViewer.selectedLayer() < 0
                        ? Component.translatable("gui.neoecoae.multiblock.layer_all")
                        : Component.translatable("gui.neoecoae.multiblock.layer_value", patternViewer.selectedLayer()),
                NEStructureTerminalLayout.LAYER_LABEL_X,
                NEStructureTerminalLayout.PATTERN_PANEL_Y + 7,
                NEStructureTerminalLayout.LAYER_LABEL_W,
                NELDLibStyle.DARK_TEXT_VALUE);

        context.drawLocalString(
                graphics,
                Component.translatable("gui.neoecoae.multiblock.pattern"),
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
                Component.translatable("gui.neoecoae.multiblock.material_summary"),
                NEStructureTerminalLayout.INFO_PANEL_X + 7,
                NEStructureTerminalLayout.INFO_PANEL_Y + 48,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        materialPanel.drawItems(context, graphics);
        materialPanel.drawPageText(
                context,
                graphics,
                NEStructureTerminalLayout.INFO_PANEL_X + NEStructureTerminalLayout.INFO_PANEL_W - 7,
                NEStructureTerminalLayout.INFO_PANEL_Y + 48,
                NELDLibStyle.DARK_TEXT_MUTED);
        context.drawCenteredFitted(
                graphics,
                Component.translatable("gui.neoecoae.structure_terminal.hint_shift_build"),
                NEStructureTerminalLayout.FOOTER_HINT_X,
                NEStructureTerminalLayout.FOOTER_HINT_Y,
                NEStructureTerminalLayout.WIDTH - NEStructureTerminalLayout.FOOTER_HINT_X - 7,
                NELDLibMachineWidget.TEXT_MUTED);
    }

    private static PatternBlockEntry controllerEntry(MultiblockPatternSnapshot snapshot) {
        for (PatternBlockEntry entry : snapshot.blocks()) {
            if (entry.controller()) {
                return entry;
            }
        }
        return null;
    }
}
