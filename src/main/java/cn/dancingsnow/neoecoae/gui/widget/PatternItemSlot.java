package cn.dancingsnow.neoecoae.gui.widget;

import appeng.crafting.pattern.EncodedPatternItem;
import com.lowdragmc.lowdraglib2.gui.slot.LocalSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

@LDLRegister(name = "pattern-item-slot", group = "inventory", registry = "ldlib2:ui_element")
public class PatternItemSlot extends ItemSlot {

    public PatternItemSlot() {
        this(new LocalSlot());
    }

    public PatternItemSlot(Slot slot) {
        super(slot);
    }

    @Override
    protected void drawItemStack(GUIContext guiContext, ItemStack itemStack) {
        if (itemStack.getItem() instanceof EncodedPatternItem<?> patternItem) {
            ItemStack output = patternItem.getOutput(itemStack);
            if (output.isEmpty()) {
                DrawerHelper.drawItemStack(guiContext.graphics, output, 0, 0, -1, null);
                return;
            }
        }
        super.drawItemStack(guiContext, itemStack);
    }
}