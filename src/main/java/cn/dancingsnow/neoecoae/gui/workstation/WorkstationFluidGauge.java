package cn.dancingsnow.neoecoae.gui.workstation;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import java.util.ArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Tank contents stay inside the frame; ticks are rendered over the fluid and hover highlight. */
final class WorkstationFluidGauge extends FluidSlot {
    private final int firstTick;
    private String tooltipRole;

    WorkstationFluidGauge(IFluidHandler fluids, int tank, boolean allowFill, boolean allowDrain) {
        this(fluids, tank, allowFill, allowDrain, 6);
    }

    WorkstationFluidGauge(IFluidHandler fluids, int tank, boolean allowFill, boolean allowDrain, int firstTick) {
        this.firstTick = firstTick;
        bind(fluids, tank);
        // The full-size amount label is decorative; hover belongs to the tank itself.
        amountLabel.setAllowHitTest(false);
        setAllowClickFilled(allowFill);
        setAllowClickDrained(allowDrain);
        style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        slotStyle(style -> style.fillDirection(FillDirection.DOWN_TO_UP));
    }

    WorkstationFluidGauge withTooltipRole(String role) {
        tooltipRole = role;
        return this;
    }

    @Override
    protected void onHoverTooltips(UIEvent event) {
        if (tooltipRole == null) {
            super.onHoverTooltips(event);
            return;
        }
        var lines = new ArrayList<Component>();
        lines.add(Component.translatable("gui.neoecoae.large_integrated_working_station." + tooltipRole + "_fluid")
            .withStyle(ChatFormatting.YELLOW));
        lines.addAll(getFullTooltipTexts());
        lines.add(Component.translatable("gui.neoecoae.workstation." + tooltipRole + "_fluid_hint")
            .withStyle(ChatFormatting.GRAY));
        event.hoverTooltips = new HoverTooltips(lines, null, null, null);
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        super.drawBackgroundAdditional(context);
        if (getValue().isEmpty() && !isHover() && !isSelfOrChildHover()) return;
        int right = Math.round(getPositionX() + getSizeWidth());
        int top = Math.round(getPositionY());
        int height = Math.round(getSizeHeight());
        for (int y = firstTick; y < height; y += 5) {
            int tickWidth = (y - firstTick) % 10 == 0 ? 5 : 3;
            context.graphics.fill(right - tickWidth, top + y, right, top + y + 1, 0xFFF2F2F2);
        }
    }
}
