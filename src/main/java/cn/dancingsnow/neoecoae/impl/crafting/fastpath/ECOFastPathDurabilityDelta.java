package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import net.minecraft.world.item.ItemStack;

/** One verified durability transition. A negative delta means the item was consumed before a remainder existed. */
public record ECOFastPathDurabilityDelta(ItemStack before, ItemStack after, int delta) {
    public ECOFastPathDurabilityDelta {
        before = before == null ? ItemStack.EMPTY : before.copy();
        after = after == null ? ItemStack.EMPTY : after.copy();
    }
}
