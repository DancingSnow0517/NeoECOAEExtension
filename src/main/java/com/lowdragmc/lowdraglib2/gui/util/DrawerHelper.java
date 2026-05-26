package com.lowdragmc.lowdraglib2.gui.util;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

public class DrawerHelper {
    public static void drawItemStack(GuiGraphics graphics, ItemStack stack, int x, int y, int color, Object decoration) {
        graphics.renderItem(stack, x, y);
    }
}
