package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.gui.nativeui.NENativeUiConstants;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEStructureTerminalMenu;
import cn.dancingsnow.neoecoae.gui.nativeui.widget.NEAe2TextButton;
import cn.dancingsnow.neoecoae.network.NENetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Structure Terminal configuration UI.
 * <p>
 * Uses AE2-style generated panel background and AE2-style text buttons.
 * </p>
 */
public class NEStructureTerminalScreen extends AbstractContainerScreen<NEStructureTerminalMenu> {

    private int displayBuildLength;
    private int minLength = 1;
    private int maxLength = 12;

    public NEStructureTerminalScreen(NEStructureTerminalMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 220;
        this.imageHeight = 120;
        this.displayBuildLength = menu.getBuildLength();
    }

    public void setBuildLength(int length, int min, int max) {
        this.displayBuildLength = length;
        this.minLength = min;
        this.maxLength = max;
    }

    @Override
    protected void init() {
        super.init();

        int btnY = topPos + 60;
        int btnH = 20;
        int centerX = leftPos + imageWidth / 2;

        addRenderableWidget(new NEAe2TextButton(centerX - 30, btnY, 20, btnH,
            Component.literal("-"),
            btn -> NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalConfigActionPacket(
                NENetwork.NEStructureTerminalConfigActionPacket.Action.DECREASE))));

        addRenderableWidget(new NEAe2TextButton(centerX + 10, btnY, 20, btnH,
            Component.literal("+"),
            btn -> NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalConfigActionPacket(
                NENetwork.NEStructureTerminalConfigActionPacket.Action.INCREASE))));

        addRenderableWidget(new NEAe2TextButton(centerX + 40, btnY, 44, btnH,
            Component.translatable("gui.neoecoae.structure_terminal.reset"),
            btn -> NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalConfigActionPacket(
                NENetwork.NEStructureTerminalConfigActionPacket.Action.RESET))));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        NENativeAe2StyleRenderer.drawAeMainPanel(guiGraphics, leftPos, topPos, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title,
            NENativeUiConstants.TITLE_X, NENativeUiConstants.TITLE_Y,
            NENativeUiConstants.MACHINE_TEXT_PRIMARY);

        guiGraphics.drawString(font,
            Component.translatable("gui.neoecoae.structure_terminal.variable_sections",
                displayBuildLength, minLength, maxLength),
            NENativeUiConstants.TITLE_X, 30,
            NENativeUiConstants.MACHINE_TEXT_SECONDARY);

        guiGraphics.drawString(font,
            Component.translatable("gui.neoecoae.structure_terminal.hint_shift_build"),
            NENativeUiConstants.TITLE_X, 44,
            NENativeUiConstants.MACHINE_TEXT_HINT);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
