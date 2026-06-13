package cn.dancingsnow.neoecoae.util;

import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public interface ICellHost {
    void setCellStack(@Nullable ItemStack itemStack);

    @Nullable ItemStack getCellStack();

    boolean isItemValid(ItemStack stack);
}
