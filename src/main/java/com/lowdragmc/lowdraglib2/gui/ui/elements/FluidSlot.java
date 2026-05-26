package com.lowdragmc.lowdraglib2.gui.ui.elements;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import java.util.function.Consumer;

public class FluidSlot extends UIElement {
    public FluidSlot bind(FluidTank tank, int index) {
        return this;
    }

    public FluidSlot slotStyle(Consumer<SlotStyle> consumer) {
        consumer.accept(new SlotStyle());
        return this;
    }

    public FluidSlot setAllowClickDrained(boolean value) { return this; }
    public FluidSlot setAllowClickFilled(boolean value) { return this; }

    public static class SlotStyle {
        public SlotStyle fillDirection(FillDirection direction) { return this; }
    }
}
