package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.gui.nativeui.NENativeUiConstants;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEStructureTerminalMenu;
import cn.dancingsnow.neoecoae.network.NENetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Structure Terminal configuration UI.
 * <p>
 * Displays and allows editing of the build length stored in the
 * Structure Terminal item's NBT. No machine binding, no preview,
 * no auto-build — those happen via Shift+right-click on a host.
 * </p>
 */
public class NEStructureTerminalScreen extends AbstractContainerScreen<NEStructureTerminalMenu> {

    private int displayBuildLength;

    public NEStructureTerminalScreen(NEStructureTerminalMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 220;
        this.imageHeight = 120;
        this.displayBuildLength = menu.getBuildLength();
    }

    /** Called from client packet handler to update the displayed length. */
    public void setBuildLength(int length) {
        this.displayBuildLength = length;
    }

    @Override
    protected void init() {
        super.init();

        int btnY = topPos + 60;
        int btnH = 20;
        int centerX = leftPos + imageWidth / 2;

        // Build Length -
        addRenderableWidget(Button.builder(Component.literal("-"), btn -> {
            NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalConfigActionPacket(
                NENetwork.NEStructureTerminalConfigActionPacket.Action.DECREASE));
            this.displayBuildLength = Math.max(1, this.displayBuildLength - 1);
        }).pos(centerX - 30, btnY).size(20, btnH).build());

        // Build Length +
        addRenderableWidget(Button.builder(Component.literal("+"), btn -> {
            NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalConfigActionPacket(
                NENetwork.NEStructureTerminalConfigActionPacket.Action.INCREASE));
            this.displayBuildLength = Math.min(64, this.displayBuildLength + 1);
        }).pos(centerX + 10, btnY).size(20, btnH).build());

        // Reset to 1
        addRenderableWidget(Button.builder(Component.literal("Reset"), btn -> {
            NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalConfigActionPacket(
                NENetwork.NEStructureTerminalConfigActionPacket.Action.RESET));
            this.displayBuildLength = 1;
        }).pos(centerX + 40, btnY).size(44, btnH).build());
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(leftPos, topPos,
            leftPos + imageWidth, topPos + imageHeight,
            NENativeUiConstants.BG_COLOR);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title,
            NENativeUiConstants.TITLE_X, NENativeUiConstants.TITLE_Y,
            NENativeUiConstants.TITLE_COLOR);

        // Build length display
        guiGraphics.drawString(font,
            Component.literal("Build Length: " + displayBuildLength),
            NENativeUiConstants.TITLE_X, 30,
            0xFFC0C0D0);

        // Hint
        guiGraphics.drawString(font,
            Component.literal("Shift+right-click a controller to build"),
            NENativeUiConstants.TITLE_X, 44,
            0xFF6A8AAA);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
