package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.nativeui.NENineSliceRenderer;
import cn.dancingsnow.neoecoae.gui.nativeui.NENativeUiConstants;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEStructureTerminalMenu;
import cn.dancingsnow.neoecoae.gui.nativeui.widget.NETexturedButton;
import cn.dancingsnow.neoecoae.network.NENetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Structure Terminal configuration UI.
 * <p>
 * Uses project nine-slice GUI assets for background and buttons.
 * No LDLib dependency.
 * </p>
 */
public class NEStructureTerminalScreen extends AbstractContainerScreen<NEStructureTerminalMenu> {

    private static final ResourceLocation TEX_BACKGROUND = NeoECOAE.id("textures/gui/background.png");
    private static final int TEX_BG_SIZE = 16;
    private static final int BG_LEFT = 2;
    private static final int BG_TOP = 2;
    private static final int BG_RIGHT = 2;
    private static final int BG_BOTTOM = 4;

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

        addRenderableWidget(new NETexturedButton(centerX - 30, btnY, 20, btnH,
            Component.literal("-"),
            btn -> NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalConfigActionPacket(
                NENetwork.NEStructureTerminalConfigActionPacket.Action.DECREASE))));

        addRenderableWidget(new NETexturedButton(centerX + 10, btnY, 20, btnH,
            Component.literal("+"),
            btn -> NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalConfigActionPacket(
                NENetwork.NEStructureTerminalConfigActionPacket.Action.INCREASE))));

        addRenderableWidget(new NETexturedButton(centerX + 40, btnY, 44, btnH,
            Component.translatable("gui.neoecoae.structure_terminal.reset"),
            btn -> NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalConfigActionPacket(
                NENetwork.NEStructureTerminalConfigActionPacket.Action.RESET))));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        NENineSliceRenderer.drawPanel(guiGraphics, TEX_BACKGROUND,
            leftPos, topPos, imageWidth, imageHeight,
            TEX_BG_SIZE, TEX_BG_SIZE,
            BG_LEFT, BG_TOP, BG_RIGHT, BG_BOTTOM);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title,
            NENativeUiConstants.TITLE_X, NENativeUiConstants.TITLE_Y,
            NENativeUiConstants.TITLE_COLOR);

        guiGraphics.drawString(font,
            Component.translatable("gui.neoecoae.structure_terminal.variable_sections",
                displayBuildLength, minLength, maxLength),
            NENativeUiConstants.TITLE_X, 30,
            0xFFC0C0D0);

        guiGraphics.drawString(font,
            Component.translatable("gui.neoecoae.structure_terminal.hint_shift_build"),
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
