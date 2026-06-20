package cn.dancingsnow.neoecoae.util;

import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

public interface ICellHost {
    void setCellStack(@NonNull ItemStack itemStack);

    @NonNull ItemStack getCellStack();

    boolean isItemValid(@NonNull ItemStack stack);
}
