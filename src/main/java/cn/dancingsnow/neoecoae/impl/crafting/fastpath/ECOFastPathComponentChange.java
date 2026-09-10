package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import net.minecraft.world.item.ItemStack;

/** One verified before/after ItemStack component transition. */
public record ECOFastPathComponentChange(ItemStack before, ItemStack after) {
    public ECOFastPathComponentChange {
        before = before == null ? ItemStack.EMPTY : before.copy();
        after = after == null ? ItemStack.EMPTY : after.copy();
    }
}
