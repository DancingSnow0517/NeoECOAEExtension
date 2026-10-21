package cn.dancingsnow.neoecoae.gui.widget;

import appeng.crafting.pattern.EncodedPatternItem;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.slot.LocalSlot;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.ItemSlot;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.BooleanSupplier;

@LDLRegister(name = "pattern-item-slot", group = "inventory")
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
    protected void drawItemStack(GUIContext guiContext, ItemStack itemStack) {
        if (itemStack.getItem() instanceof EncodedPatternItem patternItem) {
            if (!ItemStack.matches(cachedPattern, itemStack)) {
                cachedPattern = itemStack.copy();
                cachedOutput = patternItem.getOutput(itemStack);
            }
            ItemStack output = cachedOutput;
            if (!output.isEmpty()) {
                DrawerHelper.drawItemStack(guiContext.graphics, output, 0, 0, -1, null);
                return;
            }
        }
        super.drawItemStack(guiContext, itemStack);
    }
}
