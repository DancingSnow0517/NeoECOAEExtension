package cn.dancingsnow.neoecoae.gui.widget;

import com.lowdragmc.lowdraglib2.gui.slot.LocalSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import net.minecraft.world.inventory.Slot;

import java.util.function.BooleanSupplier;

@LDLRegister(name = "pattern-item-slot", group = "inventory", registry = "ldlib2:ui_element")
public class PatternItemSlot extends ItemSlot {
    private BooleanSupplier highlighted = () -> false;

    public PatternItemSlot() {
        this(new LocalSlot());
    }

    public PatternItemSlot(Slot slot) {
        super(slot);
    }

    public PatternItemSlot highlighted(BooleanSupplier highlighted) {
        this.highlighted = highlighted == null ? () -> false : highlighted;
        return this;
    }

    public boolean isHighlighted() {
        return highlighted.getAsBoolean();
    }
}
