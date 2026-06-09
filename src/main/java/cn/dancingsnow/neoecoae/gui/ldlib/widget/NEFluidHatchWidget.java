package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.gui.ldlib.support.NEForgeFluidStorage;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.TankWidget;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;

public class NEFluidHatchWidget extends NELDLibMachineWidget {
    private static final int TANK_W = 46;
    private static final int TANK_H = 64;
    private static final int TANK_X = 87;
    private static final int TANK_Y = 25;
    private static final int AMOUNT_Y = TANK_Y + TANK_H + 7;

    private final FluidTank tank;

    public NEFluidHatchWidget(Component title, FluidTank tank) {
        super(title, 220, 110);
        this.tank = tank;
    }

    @Override
    protected void initLdWidgets() {
        addWidget(new TankWidget(new NEForgeFluidStorage(tank), TANK_X, TANK_Y, TANK_W, TANK_H, true, true)
                .setShowAmount(false)
                .setDrawHoverTips(false)
                .setDrawHoverOverlay(true)
                .setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP)
                .setAllowClickFilled(true)
                .setAllowClickDrained(true)
                .setChangeListener(() -> {}));
        addText(0, AMOUNT_Y, width, 9, this::amountText, TEXT_VALUE, TextTexture.TextType.NORMAL);
    }

    @Override
    protected void drawMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isMouseIn(TANK_X, TANK_Y, TANK_W, TANK_H, mouseX, mouseY)) {
            return;
        }
        FluidStack stack = tank.getFluid();
        Component name =
                stack.isEmpty() ? Component.translatable("gui.neoecoae.fluid_tank.empty") : stack.getDisplayName();
        graphics.renderComponentTooltip(font(), List.of(name, amountText()), mouseX, mouseY);
    }

    private Component amountText() {
        return Component.translatable(
                "gui.neoecoae.fluid_tank.amount",
                fmt(Math.max(0, tank.getFluidAmount())),
                fmt(Math.max(0, tank.getCapacity())));
    }
}
