package cn.dancingsnow.neoecoae.api.storage;

import net.minecraft.world.item.ItemStack;

/**
 * Opts an Item out of insertion into AE2's native ME drive and ME chest.
 * Implement this on your own Item class; no registration or ECO cell base class is required.
 * This contract only restricts insertion and does not provide a cell inventory or affect other hosts.
 */
public interface INativeMEStorageRestrictedItem {
    /**
     * Returns whether the given stack must be rejected by both native hosts.
     * Called for non-empty stacks during inventory validation on either logical side.
     * Overrides must be read-only and safe to call repeatedly; false leaves AE2's usual checks in place.
     */
    default boolean isNativeMEStorageRestricted(ItemStack stack) {
        return true;
    }
}
