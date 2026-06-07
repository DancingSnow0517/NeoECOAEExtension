package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.gui.nativeui.NENativeUiConstants;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEFluidHatchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.fluids.FluidStack;

/**
 * Screen for ECO Fluid Input/Output Hatches.
 */
public class NEFluidHatchScreen extends NEBaseMachineScreen<NEFluidHatchMenu> {
    private static final int TANK_W = 46;
    private static final int TANK_H = 64;
    private static final int TANK_X = (NENativeUiConstants.UI_WIDTH - TANK_W) / 2;
    private static final int TANK_Y = 25;
    private static final int AMOUNT_Y = TANK_Y + TANK_H + 7;

    public NEFluidHatchScreen(NEFluidHatchMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, NEMachineScreenConfig.FLUID_HATCH);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderFluidTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(guiGraphics, partialTick, mouseX, mouseY);

        int tankX = leftPos + TANK_X;
        int tankY = topPos + TANK_Y;
        FluidStack stack = menu.getClientFluid();
        int amount = menu.getTankAmount();
        int capacity = menu.getTankCapacity();

        NEFluidTankUi.draw(guiGraphics, tankX, tankY, TANK_W, TANK_H, stack, amount, capacity);
        NEFluidTankUi.drawHover(guiGraphics, mouseX, mouseY, tankX, tankY, TANK_W, TANK_H);
    }

    @Override
    protected void renderAdditionalLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Component amount = NEFluidTankUi.amountText(menu.getTankAmount(), menu.getTankCapacity());
        int amountX = (imageWidth - font.width(amount)) / 2;
        guiGraphics.drawString(font, amount, amountX, AMOUNT_Y, NENativeUiConstants.MACHINE_TEXT_VALUE, false);
    }

    private void renderFluidTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        NEFluidTankUi.renderTooltip(
                guiGraphics,
                font,
                menu.getClientFluid(),
                menu.getTankAmount(),
                menu.getTankCapacity(),
                leftPos + TANK_X,
                topPos + TANK_Y,
                TANK_W,
                TANK_H,
                mouseX,
                mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0
                && mouseX >= leftPos + TANK_X
                && mouseX < leftPos + TANK_X + TANK_W
                && mouseY >= topPos + TANK_Y
                && mouseY < topPos + TANK_Y + TANK_H) {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
