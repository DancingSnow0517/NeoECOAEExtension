package com.lowdragmc.lowdraglib2.gui.texture;

import net.minecraft.world.item.ItemStack;

public class ItemStackTexture implements IGuiTexture {
    private final ItemStack stack;

    public ItemStackTexture(ItemStack stack) {
        this.stack = stack;
    }

    public ItemStack stack() {
        return stack;
    }
}
