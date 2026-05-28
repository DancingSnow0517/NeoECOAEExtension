package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.gui.nativeui.NENativeUiConstants;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEStructureTerminalMenu;
import cn.dancingsnow.neoecoae.multiblock.NEStructureTerminalUiState;
import cn.dancingsnow.neoecoae.network.NENetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Structure Terminal.
 * <p>
 * Provides buttons for build length selection, structure preview,
 * and auto-build. Displays build status and material information
 * received via S2C state packets.
 * </p>
 */
public class NEStructureTerminalScreen extends NEBaseMachineScreen<NEStructureTerminalMenu> {

    private boolean hasState;
    private NEStructureTerminalUiState uiState;

    public NEStructureTerminalScreen(NEStructureTerminalMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, NEMachineScreenConfig.STRUCTURE_TERMINAL);
        this.imageWidth = 340;
        this.imageHeight = 220;
        this.uiState = NEStructureTerminalUiState.empty(menu.getMachinePos());
    }

    /** Called from client packet handler to push state to this screen. */
    public void setStructureTerminalUiState(NEStructureTerminalUiState state) {
        this.hasState = true;
        this.uiState = state;
    }

    @Override
    protected void init() {
        super.init();

        int btnY = topPos + 155;
        int btnH = 20;

        // Build Length -
        addRenderableWidget(Button.builder(Component.literal("-"), btn -> {
            NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalActionPacket(
                menu.getMachinePos(), NENetwork.NEStructureTerminalActionPacket.Action.DECREASE_BUILD_LENGTH));
        }).pos(leftPos + 8, btnY).size(20, btnH).build());

        // Build Length +
        addRenderableWidget(Button.builder(Component.literal("+"), btn -> {
            NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalActionPacket(
                menu.getMachinePos(), NENetwork.NEStructureTerminalActionPacket.Action.INCREASE_BUILD_LENGTH));
        }).pos(leftPos + 32, btnY).size(20, btnH).build());

        // Preview
        addRenderableWidget(Button.builder(Component.literal("Preview"), btn -> {
            NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalActionPacket(
                menu.getMachinePos(), NENetwork.NEStructureTerminalActionPacket.Action.PREVIEW_STRUCTURE));
        }).pos(leftPos + 60, btnY).size(56, btnH).build());

        // Auto Build
        addRenderableWidget(Button.builder(Component.literal("Auto Build"), btn -> {
            NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalActionPacket(
                menu.getMachinePos(), NENetwork.NEStructureTerminalActionPacket.Action.AUTO_BUILD));
        }).pos(leftPos + 122, btnY).size(60, btnH).build());
    }

    @Override
    protected void renderAdditionalLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        NEStructureTerminalUiState s = hasState ? this.uiState : this.uiState;

        final int x = NENativeUiConstants.TITLE_X;
        final int valueColor = 0xFFC0C0D0;
        final int labelColor = 0xFF8A8AA0;
        int y = 50;

        // Row 1: Structure name & Formed
        guiGraphics.drawString(font,
            Component.literal(s.structureName().isEmpty() ? "Structure Terminal" : s.structureName()),
            x, y, valueColor);
        guiGraphics.drawString(font,
            Component.literal("Formed: " + s.formed()),
            x + 200, y, valueColor);
        y += 14;

        // Row 2: Build Length selector
        guiGraphics.drawString(font,
            Component.literal("Build Length: " + s.selectedBuildLength()
                + " [" + s.minBuildLength() + "-" + s.maxBuildLength() + "]"),
            x, y, valueColor);
        guiGraphics.drawString(font,
            Component.literal("Building: " + (s.buildInProgress() ? "Yes" : "No")),
            x + 200, y, valueColor);
        y += 14;

        // Row 3: Progress
        if (s.buildInProgress()) {
            guiGraphics.drawString(font,
                Component.literal("Progress: " + s.placedBlocks() + " / " + s.totalBlocks()),
                x, y, valueColor);
            y += 14;
        }

        // Row 4: Status
        Component statusComponent;
        try {
            statusComponent = Component.translatable(s.previewStatusKey(), s.previewStatusArg1(), s.previewStatusArg2());
        } catch (Exception ignored) {
            statusComponent = Component.literal(s.previewStatusKey());
        }
        guiGraphics.drawString(font,
            Component.literal("Status: ").append(statusComponent),
            x, y, labelColor);
        y += 14;

        // Row 5: Stats
        guiGraphics.drawString(font,
            Component.literal("M:" + s.previewMissingBlocks() +
                " C:" + s.previewConflictBlocks() +
                " R:" + s.previewReusedBlocks() +
                " Req:" + s.previewRequiredItems()),
            x, y, labelColor);
        y += 14;

        // Row 6+: Materials
        if (!s.materials().isEmpty()) {
            guiGraphics.drawString(font, Component.literal("Materials:"), x, y, labelColor);
            y += 12;
            int shown = 0;
            for (var mat : s.materials()) {
                if (shown >= 5) break;
                guiGraphics.drawString(font,
                    Component.literal("  " + mat.item().getHoverName().getString()
                        + ": " + mat.available() + " / " + mat.required()
                        + (mat.missing() > 0 ? " (missing " + mat.missing() + ")" : "")),
                    x, y, labelColor);
                y += 10;
                shown++;
            }
        }
    }

    public NEStructureTerminalMenu getMenu() {
        return menu;
    }
}
