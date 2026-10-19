package cn.dancingsnow.neoecoae.gui.workstation;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Tank contents stay inside the frame; ticks are rendered over the fluid and hover highlight. */
final class WorkstationFluidGauge extends FluidSlot {
    WorkstationFluidGauge(IFluidHandler fluids, int tank, boolean allowFill, boolean allowDrain) {
        bind(fluids, tank);
        setAllowClickFilled(allowFill);
        setAllowClickDrained(allowDrain);
        style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        slotStyle(style -> style.fillDirection(FillDirection.DOWN_TO_UP));
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        super.drawBackgroundAdditional(context);
        int right = Math.round(getPositionX() + getSizeWidth());
        int top = Math.round(getPositionY());
        int height = Math.round(getSizeHeight());
        for (int y = 5; y < height; y += 5) {
            context.graphics.fill(right - 4, top + y, right, top + y + 1, 0xFFF2F2F2);
        }
    }
}
