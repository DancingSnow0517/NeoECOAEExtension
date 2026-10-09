package cn.dancingsnow.neoecoae.util;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Compatibility alias for the public {@link cn.dancingsnow.neoecoae.api.storage.ICellHost} contract.
 * Existing implementations also expose that API; new integrations should depend on the API package.
 */
public interface ICellHost extends cn.dancingsnow.neoecoae.api.storage.ICellHost {
    @Override
    void setCellStack(@Nullable ItemStack itemStack);

    @Override
    @Nullable
    ItemStack getCellStack();

    @Override
    boolean isItemValid(ItemStack stack);

    @Override
    default boolean canExtractCell() {
        return true;
    }
}
