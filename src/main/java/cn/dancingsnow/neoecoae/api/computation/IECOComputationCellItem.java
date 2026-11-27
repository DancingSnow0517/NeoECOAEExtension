package cn.dancingsnow.neoecoae.api.computation;

import cn.dancingsnow.neoecoae.api.IECOTier;
import net.minecraft.world.item.ItemStack;

/** Computation memory, independent of storage cells and of a particular Item base class. */
public interface IECOComputationCellItem {
    IECOTier getTier();
    default IECOTier getTier(ItemStack stack) { return getTier(); }
    default long getComputationBytes(ItemStack stack) { return getTier(stack).getCPUTotalBytes(); }
}
