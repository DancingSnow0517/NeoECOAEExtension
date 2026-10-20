package cn.dancingsnow.neoecoae.platform.recipe;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** A read-only recipe snapshot, never a player menu or mutable inventory. */
public interface NERecipeInput extends Container {
    int size();

    @Override
    default int getContainerSize() { return size(); }

    @Override
    default ItemStack removeItem(int slot, int amount) { throw readOnly(); }

    @Override
    default ItemStack removeItemNoUpdate(int slot) { throw readOnly(); }

    @Override
    default void setItem(int slot, ItemStack stack) { throw readOnly(); }

    @Override
    default void setChanged() { throw readOnly(); }

    @Override
    default void clearContent() { throw readOnly(); }

    @Override
    default boolean stillValid(Player player) { return true; }

    private static UnsupportedOperationException readOnly() {
        return new UnsupportedOperationException("Recipe inputs are read-only snapshots");
    }
}
