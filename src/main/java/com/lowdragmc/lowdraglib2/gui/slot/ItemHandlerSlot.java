package com.lowdragmc.lowdraglib2.gui.slot;

import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

public class ItemHandlerSlot extends SlotItemHandler {
    public ItemHandlerSlot(IItemHandler itemHandler, int index) {
        super(itemHandler, index, 0, 0);
    }
}
