package cn.dancingsnow.neoecoae.gui.widget;

import appeng.crafting.pattern.EncodedPatternItem;
import com.lowdragmc.lowdraglib2.gui.slot.LocalSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.function.BooleanSupplier;

@LDLRegister(name = "pattern-item-slot", group = "inventory", registry = "ldlib2:ui_element")
public class PatternItemSlot extends ItemSlot {
    private BooleanSupplier highlighted = () -> false;
    private BooleanSupplier dimmed = () -> false;
    private ItemStack cachedPattern = ItemStack.EMPTY;
    private ItemStack cachedOutput = ItemStack.EMPTY;

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

    public PatternItemSlot dimmed(BooleanSupplier dimmed) {
        this.dimmed = dimmed;
        return this;
    }

    public boolean isDimmed() { return dimmed.getAsBoolean(); }

    @Override
    protected void drawItemStack(@NonNull IGUIContext context, @NonNull ItemStack stack) {
        if (stack.getItem() instanceof EncodedPatternItem<?> patternItem) {
            ItemStack output = patternItem.getOutput(stack);
            super.drawItemStack(context, output);
        } else {
            super.drawItemStack(context, stack);
        }
    }
}
