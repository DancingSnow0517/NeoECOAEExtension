package cn.dancingsnow.neoecoae.impl.storage.transfer;

import net.minecraft.world.item.ItemStack;

/** Runtime-only bridge implemented by optional mixins; it contains no Sophisticated type references. */
public interface ECOSophisticatedHandlerBridge {
    default Object neoecoae$getDelegate() {
        return null;
    }

    default boolean neoecoae$isFilteredExtractor() {
        return false;
    }

    default ItemStack neoecoae$extractMatching(ItemStack requested, boolean simulate) {
        return ItemStack.EMPTY;
    }

    default int neoecoae$getSlots() {
        return 0;
    }

    default ItemStack neoecoae$getStackInSlot(int slot) {
        return ItemStack.EMPTY;
    }

    default void neoecoae$saveInventory() {
    }
}
