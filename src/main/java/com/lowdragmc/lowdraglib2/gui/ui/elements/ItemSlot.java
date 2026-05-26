package com.lowdragmc.lowdraglib2.gui.ui.elements;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public class ItemSlot extends UIElement {
    protected final Slot slot;

    public ItemSlot() {
        this.slot = null;
    }

    public ItemSlot(Slot slot) {
        this.slot = slot;
    }

    public ItemSlot slotStyle(Consumer<SlotStyle> consumer) {
        consumer.accept(new SlotStyle());
        return this;
    }

    protected void drawItemStack(GUIContext guiContext, ItemStack itemStack) {
    }

    public static class SlotStyle {
        public SlotStyle slotOverlay(IGuiTexture texture) { return this; }
    }
}
