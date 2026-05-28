package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.nativeui.NENativeUiConstants;
import cn.dancingsnow.neoecoae.gui.nativeui.NENineSliceRenderer;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEIntegratedWorkingStationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the ECO Integrated Working Station.
 * <p>
 * Layout: 176×196 with 3×3 input, 1 output, player inventory, and progress bar.
 * Uses nine-slice background consistent with other ECO machine UIs.
 * </p>
 */
public class NEIntegratedWorkingStationScreen extends AbstractContainerScreen<NEIntegratedWorkingStationMenu> {

    private static final ResourceLocation TEX_BG = NeoECOAE.id("textures/gui/background.png");
    private static final int TEX_SIZE = 16;
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 196;

    private static final int PROGRESS_X = 150;
    private static final int PROGRESS_Y = 17;
    private static final int PROGRESS_W = 10;
    private static final int PROGRESS_H = 54;

    private static final int FLUID_X = 8;
    private static final int FLUID_Y = 17;
    private static final int FLUID_W = 16;
    private static final int FLUID_H = 54;

    public NEIntegratedWorkingStationScreen(NEIntegratedWorkingStationMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // Nine-slice background panel
        NENineSliceRenderer.drawPanel(g, TEX_BG, leftPos, topPos, imageWidth, imageHeight,
            TEX_SIZE, TEX_SIZE, 2, 2, 2, 4);

        // Progress bar (bottom-up fill)
        int progress = menu.getProgress();
        int maxProgress = menu.getMaxProgress();
        if (maxProgress > 0 && progress > 0) {
            int barHeight = (int) ((float) progress / maxProgress * PROGRESS_H);
            int barY = topPos + PROGRESS_Y + PROGRESS_H - barHeight;
            g.fill(leftPos + PROGRESS_X, barY,
                leftPos + PROGRESS_X + PROGRESS_W, topPos + PROGRESS_Y + PROGRESS_H,
                0xFF5A49D6);
        }

        // Fluid bar (bottom-up fill)
        int fluidAmount = menu.getFluidInAmount();
        if (fluidAmount > 0) {
            int fluidH = (int) ((float) fluidAmount / 16000 * FLUID_H);
            int fluidBarY = topPos + FLUID_Y + FLUID_H - fluidH;
            g.fill(leftPos + FLUID_X, fluidBarY,
                leftPos + FLUID_X + FLUID_W, topPos + FLUID_Y + FLUID_H,
                0xFF3A7FD6);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Title
        g.drawString(font, title, NENativeUiConstants.TITLE_X, 6, NENativeUiConstants.MACHINE_TEXT_PRIMARY);

        // Energy text: "所需能量：XXk FE"
        int energy = menu.getEnergy();
        Component energyText = Component.translatable("gui.neoecoae.integrated_working_station.energy",
            energy / 1000);
        g.drawString(font, energyText, 8, 78, NENativeUiConstants.MACHINE_TEXT_SECONDARY);

        // Progress percentage
        int progress = menu.getProgress();
        int maxProgress = menu.getMaxProgress();
        if (maxProgress > 0) {
            int pct = progress * 100 / maxProgress;
            g.drawString(font, Component.literal(pct + "%"),
                PROGRESS_X, PROGRESS_Y + PROGRESS_H + 4, NENativeUiConstants.MACHINE_TEXT_VALUE);
        }

        // Working status
        if (menu.isWorking()) {
            g.drawString(font, Component.translatable("gui.neoecoae.machine.active"),
                90, 78, NENativeUiConstants.MACHINE_TEXT_SUCCESS);
        }
    }
}
