package cn.dancingsnow.neoecoae.util;

import net.minecraft.world.item.ItemStack;

public interface ICellHost {
    void setCellStack(ItemStack itemStack);

    ItemStack getCellStack();

    boolean isItemValid(ItemStack stack);
}
