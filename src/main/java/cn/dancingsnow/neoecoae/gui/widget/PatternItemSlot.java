package cn.dancingsnow.neoecoae.gui.widget;

import appeng.crafting.pattern.EncodedPatternItem;
import com.lowdragmc.lowdraglib2.gui.slot.LocalSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

@LDLRegister(name = "pattern-item-slot", group = "inventory", registry = "ldlib2:ui_element")
public class PatternItemSlot extends ItemSlot {

    public PatternItemSlot() {
        this(new LocalSlot());
    }

    public PatternItemSlot(Slot slot) {
        super(slot);
    }

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
